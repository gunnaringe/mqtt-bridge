package com.github.gunnaringe.wg2mqtt.wg2

import com.github.gunnaringe.wg2mqtt.Events
import com.github.gunnaringe.wg2mqtt.model.Call
import com.github.gunnaringe.wg2mqtt.model.CallEnvelope
import com.github.gunnaringe.wg2mqtt.model.Event
import com.github.gunnaringe.wg2mqtt.model.Metadata
import com.github.gunnaringe.wg2mqtt.model.Sms
import com.github.gunnaringe.wg2mqtt.model.SmsEnvelope
import com.github.gunnaringe.wg2mqtt.toInstant
import com.google.protobuf.Duration
import com.google.protobuf.empty
import com.wgtwo.api.v0.events.EventsProto
import com.wgtwo.api.v0.events.EventsProto.AckRequest
import com.wgtwo.api.v0.events.EventsProto.Event.EventCase
import com.wgtwo.api.v0.events.EventsProto.EventType
import com.wgtwo.api.v0.events.EventsProto.ManualAckConfig
import com.wgtwo.api.v0.events.EventsProto.SmsEvent.FromAddressCase
import com.wgtwo.api.v0.events.EventsProto.SmsEvent.ToAddressCase
import com.wgtwo.api.v0.events.EventsProto.SubscribeEventsRequest
import com.wgtwo.api.v0.events.EventsProto.SubscribeEventsResponse
import com.wgtwo.api.v0.events.EventsServiceGrpc
import com.wgtwo.auth.ClientCredentialSource
import io.grpc.Channel
import io.grpc.Context
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EventsV0Listener(
    private val channel: Channel,
    private val tokenSource: ClientCredentialSource,
    private val eventQueue: String?,
) : Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val context = Context.current().withCancellation()

    fun start() {
        executor.submit {
            while (!context.isCancelled) {
                subscribe()
                // Wait 10 seconds before reconnecting
                Thread.sleep(10_000)
            }
        }
    }

    private fun subscribe() {
        val stub = EventsServiceGrpc.newBlockingStub(channel)
            .withCallCredentials(tokenSource.callCredentials())

        logger.info("Subscribing to events")
        val request = SubscribeEventsRequest.newBuilder().apply {
            this.addType(EventType.SMS_EVENT)
            this.addType(EventType.VOICE_EVENT)
            this.startAtOldestPossible = empty {}
            if (eventQueue != null) {
                this.durableName = eventQueue
                this.queueName = eventQueue
            }
            this.manualAck = ManualAckConfig.newBuilder().apply {
                this.enable = true
                this.timeout = Duration.newBuilder().setSeconds(60).build()
            }.build()
        }.build()

        try {
            context.run {
                stub.subscribe(request).forEach { response: SubscribeEventsResponse ->
                    logger.debug("Received event: {}", response.event)
                    val user = response.event.owner.phoneNumber.e164.removePrefix("+")
                    val event = when (response.event.eventCase) {
                        EventCase.SMS_EVENT -> createSmsEvent(user, response)
                        EventCase.VOICE_EVENT -> createVoiceEvent(user, response)
                        else -> {
                            logger.warn("Skipping unhandled event type: ${response.event.eventCase}")
                            null
                        }
                    }

                    if (event != null) {
                        logger.info("Publishing event: $event")
                        Events.inbox.post(event)
                    }
                    ack(response)
                }
            }
        } catch (e: Exception) {
            logger.error("Error while listening for events", e)
        }
    }

    private fun createVoiceEvent(user: String, response: SubscribeEventsResponse): Event {
        val timestamp = response.event.timestamp.toInstant()
        val voiceEvent = response.event.voiceEvent
        val type = voiceEvent.type
        val from = voiceEvent.fromNumber.e164
        val to = voiceEvent.toNumber.e164
        val callerIdHidden = voiceEvent.callerIdHidden

        val action = when (type) {
            EventsProto.VoiceEvent.VoiceEventType.CALL_INITIATED -> "initiated"
            EventsProto.VoiceEvent.VoiceEventType.CALL_RINGING -> "ringing"
            EventsProto.VoiceEvent.VoiceEventType.CALL_ANSWERED -> "answered"
            EventsProto.VoiceEvent.VoiceEventType.CALL_ENDED -> "ended"
            EventsProto.VoiceEvent.VoiceEventType.CALL_FWD_VOICEMAIL -> "forwarded to voicemail"
            else -> "unknown"
        }

        return CallEnvelope(
            metadata = Metadata(timestamp, user, "call"),
            call = Call(from, to, action, callerIdHidden),
        )
    }

    private fun createSmsEvent(user: String, response: SubscribeEventsResponse): Event? {
        val timestamp = response.event.timestamp.toInstant()
        val smsEvent = response.event.smsEvent
        val from = when (smsEvent.fromAddressCase) {
            FromAddressCase.FROM_E164 -> smsEvent.fromE164.e164
            FromAddressCase.FROM_NATIONAL_PHONE_NUMBER -> smsEvent.fromNationalPhoneNumber.nationalPhoneNumber
            FromAddressCase.FROM_TEXT_ADDRESS -> smsEvent.fromTextAddress.textAddress
            else -> ""
        }
        val to = when (smsEvent.toAddressCase) {
            ToAddressCase.TO_E164 -> smsEvent.toE164.e164
            ToAddressCase.TO_NATIONAL_PHONE_NUMBER -> smsEvent.toNationalPhoneNumber.nationalPhoneNumber
            ToAddressCase.TO_TEXT_ADDRESS -> smsEvent.toTextAddress.textAddress
            else -> ""
        }
        val content = when (smsEvent.contentCase) {
            EventsProto.SmsEvent.ContentCase.TEXT -> smsEvent.text
            else -> ""
        }

        // Do not generate duplicate SMS if sending to yourself
        if (smsEvent.direction == EventsProto.SmsEvent.Direction.TO_SUBSCRIBER && from == to) {
            logger.info("Skipping SMS to self")
            return null
        }

        return SmsEnvelope(
            metadata = Metadata(
                user = user,
                timestamp = timestamp,
                type = "sms",
            ),
            sms = Sms(
                from = from,
                to = to,
                content = content,
            ),
        )
    }

    private fun ack(response: SubscribeEventsResponse) {
        val request = AckRequest.newBuilder().apply {
            this.inbox = response.event.metadata.ackInbox
            this.sequence = response.event.metadata.sequence
        }.build()

        val stub = EventsServiceGrpc.newBlockingStub(channel)
            .withCallCredentials(tokenSource.callCredentials())
        stub.ack(request)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EventsV0Listener::class.java)
    }

    override fun close() {
        context.cancel(null)
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
    }
}
