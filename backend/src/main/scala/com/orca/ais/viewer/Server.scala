package com.orca.ais.viewer

import com.orca.ais.viewer.actors.MessageProcessor.MessageProcessorState
import com.orca.ais.viewer.actors.{AISRetriever, DbUpdater, MessageProcessor}
import com.orca.ais.viewer.data.{DbAccess, Migrations}
import com.orca.ais.viewer.model.BoundingBox
import com.orca.ais.viewer.routes.Routes
import fr.davit.pekko.http.metrics.core.scaladsl.server.HttpMetricsDirectives._
import fr.davit.pekko.http.metrics.prometheus.marshalling.PrometheusMarshallers._
import fr.davit.pekko.http.metrics.prometheus.PrometheusRegistry
import io.prometheus.metrics.core.metrics.{Counter, Gauge}
import org.apache.pekko.actor.typed.{ActorSystem, DispatcherSelector, SupervisorStrategy}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives.path
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.RouteConcatenation._
import org.apache.pekko.stream.Materializer
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import javax.sql.DataSource
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Server {

  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    // Pekko HTTP still needs a classic ActorSystem to start
    import system.executionContext

    val futureBinding = Http().newServerAt("0.0.0.0", 8088).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {

    // Create 8 bounding boxes to cover the whole world
    val boundingBoxes = List(
      BoundingBox(-90, -180, 0, -90),
      BoundingBox(-90, -90, 0, 0),
      BoundingBox(-90, 0, 0, 90),
      BoundingBox(-90, 90, 0, 180),
      BoundingBox(90, -180, 0, -90),
      BoundingBox(90, -90, 0, 0),
      BoundingBox(90, 0, 0, 90),
      BoundingBox(90, 90, 0, 180),
    )

    val url = sys.env.getOrElse("DATABASE_URL", "jdbc:postgresql://localhost:5432/ais")
    val user = sys.env.getOrElse("DATABASE_USERNAME", "postgres")
    val password = sys.env.getOrElse("DATABASE_PASSWORD", "postgres")
    val aisStreamApiKey = System.getenv("AISSTREAM_API_KEY")

    val db = Database.forURL(
      url = url,
      user = user,
      password = password,
    )

    val dbAccess = new DbAccess(db)
    Migrations.migrate(url, user, password)

    // Create metrics to track status of processes
    val registry = PrometheusRegistry()
    val sentAisMessagesCounter = Counter
      .builder()
      .name("SentAISMessageCounter").build()
    val receivedAisMessagesCounter = Counter
      .builder()
      .name("ReceivedAISMessageCounter").build()
    val messagesProcessedCounter = Counter
      .builder()
      .name("MessagesProcessedCounter").build()
    val registeredClientActorsGauge = Gauge
      .builder()
      .name("registeredClientActorsGauge").build()
    val aisMessagesUpdatedCounter = Counter
      .builder()
      .name("AisMessagesUpdatedCounter").build()

    registry.underlying.register(sentAisMessagesCounter)
    registry.underlying.register(receivedAisMessagesCounter)
    registry.underlying.register(messagesProcessedCounter)
    registry.underlying.register(registeredClientActorsGauge)
    registry.underlying.register(aisMessagesUpdatedCounter)

    val metricsRoute: Route = path("metrics")(metrics(registry))

    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val dbUpdater = context.spawn(DbUpdater(dbAccess, aisMessagesUpdatedCounter), "DbUpdaterActor")
      val msgProcessor = context
        .spawn(
          behavior = MessageProcessor(MessageProcessorState(
            dbUpdater = dbUpdater,
            messagesProcessedCounter = messagesProcessedCounter,
            registeredClientActorsGauge = registeredClientActorsGauge,
          )),
          name = "MessageProcessorActor",
          )

      implicit val actorSystem: ActorSystem[Nothing] = context.system

      val webSocketUri = actorSystem.settings.config.getString("orca-ais-viewer.aisstream.websocket-uri")

      boundingBoxes.zipWithIndex.foreach { case (boundingBox, idx) =>
        val supervisedClientActor = Behaviors
          .supervise(AISRetriever.getActor(aisStreamApiKey, webSocketUri, boundingBox, receivedAisMessagesCounter))
          .onFailure[Exception](
            SupervisorStrategy.restartWithBackoff(
              minBackoff = 1.second, // Wait 1 second before the first restart
              maxBackoff = 10.seconds, // Cap the delay at 10 seconds
              randomFactor = 0.2, // Add 20% "jitter" to the delay
            )
          )
        val AISRetrieverActor = context.spawn(
          behavior = supervisedClientActor,
          name = s"AISRetrieverActor-$idx",
          props = DispatcherSelector.fromConfig("orca-ais-viewer.ais-retriever-dispatcher"),
        )
        context.watch(AISRetrieverActor)
        AISRetrieverActor ! AISRetriever.RetrieveGeometry(msgProcessor)
      }

      context.watch(dbUpdater)
      context.watch(msgProcessor)

      val routes =
        metricsRoute ~
        Routes.routes(msgProcessor, actorSystem, sentAisMessagesCounter, dbAccess)(Materializer(context.system))

      startHttpServer(routes)

      Behaviors.empty
    }
    ActorSystem[Nothing](rootBehavior, "OrcaAISViewerPekkoHttpServer")
    //#server-bootstrapping
  }
}