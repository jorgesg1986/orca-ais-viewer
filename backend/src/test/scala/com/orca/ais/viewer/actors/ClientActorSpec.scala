package com.orca.ais.viewer.actors

import com.orca.ais.viewer.data.DbAccess
import com.orca.ais.viewer.model.{AISStreamMessage, BoundingBoxWithAge}
import com.orca.ais.viewer.utils.Utils._
import org.apache.pekko.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage}
import io.prometheus.metrics.core.metrics.Counter
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, times, verify, when}
import org.mockito.MockitoSugar.mock
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class ClientActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import ClientActor._

  // Common test setup
  trait Context {
    val sourceActorProbe: TestProbe[Message] = createTestProbe[Message]()
    val mockCounter: Counter = mock[Counter]
    val mockDbAccess: DbAccess = mock[DbAccess]

    // Default state for the actor
    val initialState: State = State(
      boundingBox = None,
      envelope = None,
      sourceActor = sourceActorProbe.ref,
      aisMessagesCounter = mockCounter,
      dataAccess = mockDbAccess,
      ec = ExecutionContext.global,
    )

    val clientActor: ActorRef[Command] = spawn(getClientActor(initialState))

    // Sample data
    val bbox: BoundingBoxWithAge = BoundingBoxWithAge(-1.0, 50.0, 1.0, 51.0, 3600)
    val positionInside: AISStreamMessage = getAISStreamMessage(123, 0.5, 50.5)
    val positionOutside: AISStreamMessage = getAISStreamMessage(456, 52.0, 2.0)
  }

  "ClientActor" should {

    "in beforeReady state" should {
      "ignore CheckPosition messages" in new Context {
        clientActor ! CheckPosition(positionInside, System.currentTimeMillis())
        sourceActorProbe.expectNoMessage()
        verify(mockCounter, never()).inc()
      }

      "stop when receiving PoisonPill" in new Context {
        val probe = createTestProbe()
        clientActor ! PoisonPill
        probe.expectTerminated(clientActor, probe.remainingOrDefault)
      }

      "transition to processMessages state upon receiving UpdateBoundingBox" in new Context {
        // Mock DB call to return no historical data
        when(mockDbAccess.getAISInfoByLocationAndTimestamp(any[BoundingBoxWithAge], any[Instant]))
          .thenReturn(Future.successful(Seq.empty))

        // Send the bounding box to transition the state
        clientActor ! UpdateBoundingBox(bbox)

        // Now, the actor should be in the `processMessages` state
        // and should process this message.
        clientActor ! CheckPosition(positionInside, System.currentTimeMillis())

        val receivedMsg = sourceActorProbe.expectMessageType[TextMessage.Strict]

        assert(receivedMsg.getStrictText.parseJson.asJsObject.fields("MMSI") == JsNumber(123))
        verify(mockCounter, times(1)).inc()
      }
    }

    "in processMessages state" should {
      // Helper to get the actor into the `processMessages` state
      trait ProcessMessagesContext extends Context {
        when(mockDbAccess.getAISInfoByLocationAndTimestamp(any[BoundingBoxWithAge], any[Instant]))
          .thenReturn(Future.successful(Seq.empty))
        clientActor ! UpdateBoundingBox(bbox)
      }

      "send message to source actor if position is inside bounding box" in new ProcessMessagesContext {
        clientActor ! CheckPosition(positionInside, System.currentTimeMillis())

        val receivedMsg = sourceActorProbe.expectMessageType[TextMessage.Strict]
        assert(receivedMsg.getStrictText.parseJson.asJsObject.fields("MMSI") == JsNumber(123))
        verify(mockCounter, times(1)).inc()
      }

      "not send message if position is outside bounding box" in new ProcessMessagesContext {
        clientActor ! CheckPosition(positionOutside, System.currentTimeMillis())
        sourceActorProbe.expectNoMessage()
        verify(mockCounter, never()).inc()
      }

      "update its bounding box for future checks" in new ProcessMessagesContext {
        val newBbox = BoundingBoxWithAge(5.0, 60.0, 6.0, 61.0, 3600)
        val positionInNewBbox = getAISStreamMessage(789, 5.5, 60.5)

        // Mock the DB call for the new bounding box
        when(mockDbAccess.getAISInfoByLocationAndTimestamp(newBbox))
          .thenReturn(Future.successful(Seq.empty))

        // Update the bounding box
        clientActor ! UpdateBoundingBox(newBbox)

        // This position was outside the old bbox, but is inside the new one
        clientActor ! CheckPosition(positionInNewBbox, System.currentTimeMillis())
        sourceActorProbe.expectMessageType[TextMessage.Strict]
        verify(mockCounter, times(1)).inc() // one for this check

        // This position was inside the old bbox, but is outside the new one
        clientActor ! CheckPosition(positionInside, System.currentTimeMillis())
        sourceActorProbe.expectNoMessage()
      }

      "stop when receiving PoisonPill" in new ProcessMessagesContext {
        val probe = createTestProbe()
        clientActor ! PoisonPill
        probe.expectTerminated(clientActor, probe.remainingOrDefault)
      }
    }
  }
}
