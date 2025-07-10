package com.orca.ais.viewer.actors

import com.orca.ais.viewer.actors.MessageProcessor.Position
import com.orca.ais.viewer.model.BoundingBox
import com.orca.ais.viewer.utils.Utils._
import com.orca.ais.viewer.utils.json.JsonSupport._
import io.prometheus.metrics.core.metrics.Counter
import org.apache.pekko.actor
import org.apache.pekko.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.apache.pekko.http.scaladsl.model.ws._
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Flow, Keep}
import org.apache.pekko.stream.testkit.scaladsl.{TestSink, TestSource}
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.MockitoSugar.mock
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import scala.concurrent.Future

class AISRetrieverSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with BeforeAndAfterAll {

  val sampleAisJson: String = getAISStreamMessage(123456789, 0.5, 50.5).toJson.prettyPrint

  // Common test setup using a trait
  trait Context {
    val msgProcessorProbe: TestProbe[MessageProcessor.Command] = createTestProbe[MessageProcessor.Command]()
    val mockCounter: Counter = mock[Counter]
    val secondMockCounter = mock[Counter]
    val mockWebSocketClient: AISRetriever.WebSocketClient = mock[AISRetriever.WebSocketClient]

    val apiKey = "test-api-key"
    val webSocketUri = "ws://example.com/websocket"
    val bbox = BoundingBox(-1.0, 50.0, 1.0, 51.0)

    // Mock the behavior of the WebSocket client function
    when(mockWebSocketClient.apply(any[WebSocketRequest], any[Flow[Message, Message, Any]]))
      .thenReturn((Future.successful(mock[WebSocketUpgradeResponse]), ()))
  }

  "AISRetriever helper methods" should {
    "processMessage: parse a TextMessage, increment counter, and forward to processor" in {
      val msgProcessorProbe = createTestProbe[MessageProcessor.Command]()
      val mockCounter = mock[Counter]
      val secondMockCounter = mock[Counter]

      val processFn = AISRetriever.processMessage(msgProcessorProbe.ref, mockCounter, secondMockCounter)
      processFn(TextMessage.Strict(sampleAisJson))

      verify(mockCounter, times(1)).inc()
      verify(secondMockCounter, times(1)).inc()
      val received = msgProcessorProbe.expectMessageType[Position]
      received.positionReport.MetaData.MMSI should be(123456789)
      received.positionReport.Message.PositionReport.Latitude should be(0.5)
    }

    "processMessage: parse a BinaryMessage and forward to processor" in {
      val msgProcessorProbe = createTestProbe[MessageProcessor.Command]()
      val mockCounter = mock[Counter]
      val secondMockCounter = mock[Counter]

      val processFn = AISRetriever.processMessage(msgProcessorProbe.ref, mockCounter, secondMockCounter)
      processFn(BinaryMessage.Strict(ByteString.apply(sampleAisJson)))

      verify(mockCounter, times(1)).inc()
      verify(secondMockCounter, times(1)).inc()
      msgProcessorProbe.expectMessageType[Position].positionReport.MetaData.MMSI should be(123456789)
    }

    "buildAuthMessage: create a valid TextMessage for authentication" in {
      val apiKey = "test-key"
      val bbox = BoundingBox(-1.0, 50.0, 1.0, 51.0)
      val authMessage = AISRetriever.buildAuthMessage(apiKey, bbox)
      val json = authMessage.getStrictText.parseJson.asJsObject

      json.fields("APIKey") should be(JsString("test-key"))
      json.fields("BoundingBoxes").toString should be("[[[-1.0,50.0],[1.0,51.0]]]")
    }
  }

  "AISRetriever's buildAISFlow" should {
    "emit an auth message and process incoming messages" in new Context {
      // Build the flow to be tested
      val flow = AISRetriever.buildAISFlow(msgProcessorProbe.ref, apiKey, bbox, mockCounter, secondMockCounter)

      implicit val actorSystem: actor.ActorSystem =  testKit.internalSystem.classicSystem

      // Use Pekko Stream's TestKit to test the flow
      val (pub, sub) = TestSource.probe[Message]
        .via(flow)
        .toMat(TestSink.probe[Message])(Keep.both)
        .run()(Materializer(testKit.internalSystem))

      // 1. Test the Source part of the flow (outgoing messages to WebSocket)
      sub.request(1)
      val authMsg = sub.expectNext()
      authMsg shouldBe a[TextMessage]
      authMsg.asInstanceOf[TextMessage].getStrictText should include(apiKey)

      // 2. Test the Sink part of the flow (incoming messages from WebSocket)
      pub.sendNext(TextMessage.Strict(sampleAisJson))
      val received = msgProcessorProbe.expectMessageType[Position]
      received.positionReport.MetaData.MMSI should be(123456789)
      verify(mockCounter, times(1)).inc()
      verify(secondMockCounter, times(1)).inc()

      // 3. Cleanly shut down the stream probes
      pub.sendComplete()
      sub.cancel()
    }
  }
}
