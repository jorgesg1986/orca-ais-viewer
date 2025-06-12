package com.orca.ais.viewer.actors

import com.orca.ais.viewer.actors.ClientActor.CheckPosition
import com.orca.ais.viewer.actors.DbUpdater.UpsertPosition
import com.orca.ais.viewer.utils.Utils.getAISStreamMessage
import io.prometheus.metrics.core.metrics.{Counter, Gauge}
import org.apache.pekko.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.mockito.Mockito.{times, verify}
import org.mockito.MockitoSugar.mock
import org.scalatest.wordspec.AnyWordSpecLike

class MessageProcessorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  // In order to test all this without sleeping the Threads a refactor should be made to Ack the messages

  import MessageProcessor._

  // Common test setup using a trait, following the established style
  trait Context {
    val dbUpdaterProbe: TestProbe[DbUpdater.Command] = createTestProbe[DbUpdater.Command]()
    val mockMessagesCounter: Counter = mock[Counter]
    val mockClientsGauge: Gauge = mock[Gauge]

    // Probes to simulate connected client actors
    val clientActorProbe1: TestProbe[ClientActor.Command] = createTestProbe[ClientActor.Command]("client1")
    val clientActorProbe2: TestProbe[ClientActor.Command] = createTestProbe[ClientActor.Command]("client2")

    val initialState: MessageProcessorState = MessageProcessorState(
      dbUpdater = dbUpdaterProbe.ref,
      messagesProcessedCounter = mockMessagesCounter,
      registeredClientActorsGauge = mockClientsGauge
    )

    val messageProcessor = spawn(MessageProcessor(initialState))

    val samplePosition = getAISStreamMessage(123456789, 0.5, 50.5)
  }

  "MessageProcessor" should {

    "register a new client actor" in new Context {
      // Action
      messageProcessor ! RegisterActor(clientActorProbe1.ref)
      Thread.sleep(100)
      // Verification
      verify(mockClientsGauge, times(1)).inc()

      // Send a position message to confirm the new actor receives it
      messageProcessor ! Position(samplePosition)
      clientActorProbe1.expectMessageType[CheckPosition]
    }

    "remove a registered client actor" in new Context {
      // Setup: Register two actors first
      messageProcessor ! RegisterActor(clientActorProbe1.ref)
      messageProcessor ! RegisterActor(clientActorProbe2.ref)
      Thread.sleep(100)
      verify(mockClientsGauge, times(2)).inc() // Verify setup

      // Action: Remove one actor
      messageProcessor ! RemoveActor(clientActorProbe1.ref)
      Thread.sleep(100)
      verify(mockClientsGauge, times(1)).dec()

      // Verification: Send a position and ensure only the remaining actor gets it
      messageProcessor ! Position(samplePosition)
      clientActorProbe2.expectMessageType[CheckPosition]
      clientActorProbe1.expectNoMessage()
    }

    "process a Position message correctly" in new Context {
      // Setup: Register clients
      messageProcessor ! RegisterActor(clientActorProbe1.ref)
      messageProcessor ! RegisterActor(clientActorProbe2.ref)

      // Action
      val timestamp = System.currentTimeMillis()
      messageProcessor ! Position(samplePosition, timestamp)
      Thread.sleep(100)
      // Verification
      // 1. Verify metrics were updated
      verify(mockMessagesCounter, times(1)).inc()

      // 2. Verify the position was sent to the DbUpdater
      val dbMessage = dbUpdaterProbe.expectMessageType[UpsertPosition]
      dbMessage.message.MetaData.MMSI should be(123456789)

      // 3. Verify the position was broadcast to all registered clients
      val client1Msg = clientActorProbe1.expectMessageType[CheckPosition]
      client1Msg.aisMessage.MetaData.MMSI should be(123456789)
      client1Msg.timestampReceived should be(timestamp)

      val client2Msg = clientActorProbe2.expectMessageType[CheckPosition]
      client2Msg.aisMessage.MetaData.MMSI should be(123456789)
      client2Msg.timestampReceived should be(timestamp)
    }

    "not fail when processing a Position with no registered clients" in new Context {
      // Action
      messageProcessor ! Position(samplePosition)
      Thread.sleep(100)
      // Verification
      // Metrics and DB update should still happen
      verify(mockMessagesCounter, times(1)).inc()
      dbUpdaterProbe.expectMessageType[UpsertPosition]

      // No clients should receive messages, and no errors should occur
      clientActorProbe1.expectNoMessage()
      clientActorProbe2.expectNoMessage()
    }

    "handle multiple registrations and removals correctly" in new Context {
      // Register 1, Register 2
      messageProcessor ! RegisterActor(clientActorProbe1.ref)
      messageProcessor ! RegisterActor(clientActorProbe2.ref)
      Thread.sleep(100)
      verify(mockClientsGauge, times(2)).inc()

      // Send a message, both should receive it
      messageProcessor ! Position(samplePosition)
      clientActorProbe1.expectMessageType[CheckPosition]
      clientActorProbe2.expectMessageType[CheckPosition]

      // Remove 1
      messageProcessor ! RemoveActor(clientActorProbe1.ref)
      Thread.sleep(100)
      verify(mockClientsGauge, times(1)).dec()

      // Send another message, only 2 should receive it
      messageProcessor ! Position(samplePosition)
      clientActorProbe1.expectNoMessage()
      clientActorProbe2.expectMessageType[CheckPosition]
    }
  }
}
