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
import com.wgtwo.api.v0.events.EventsProto.VoiceEvent.VoiceEventType
import com.wgtwo.api.v0.events.EventsServiceGrpc
import com.wgtwo.auth.ClientCredentialSource
import io.grpc.Channel
import org.slf4j.LoggerFactory

class EventsV0Listener(
    channel: Channel,
    tokenSource: ClientCredentialSource,
    private val eventQueue: String?,
) : StreamListener("events-v0") {
    private val stub = EventsServiceGrpc.newBlockingStub(channel)
        .withCallCredentials(tokenSource.callCredentials())

    override fun stream() {
        val request = EventsProto.SubscribeEventsRequest.newBuilder().apply {
            addType(EventType.SMS_EVENT)
            addType(EventType.VOICE_EVENT)
            startAtOldestPossible = empty {}
            if (eventQueue != null) {
                durableName = eventQueue
                queueName = eventQueue
            }
            manualAck = ManualAckConfig.newBuilder().apply {
                enable = true
                timeout = Duration.newBuilder().setSeconds(60).build()
            }.build()
        }.build()

        stub.subscribe(request).forEach { response ->
            val event = response.event
            logger.debug("Received event: {}", event)
            val user = event.owner.phoneNumber.e164.removePrefix("+")
            val envelope = when (event.eventCase) {
                EventCase.SMS_EVENT -> toSms(user, event)
                EventCase.VOICE_EVENT -> toCall(user, event)
                else -> {
                    logger.warn("Skipping unhandled event type: ${event.eventCase}")
                    null
                }
            }

            countEvent(envelope?.metadata?.type ?: "ignored")
            if (envelope != null) {
                logger.info("Publishing event: $envelope")
                Events.inbox.post(envelope)
            }
            stub.ack(
                AckRequest.newBuilder()
                    .setInbox(event.metadata.ackInbox)
                    .setSequence(event.metadata.sequence)
                    .build(),
            )
        }
    }

    private fun toCall(user: String, event: EventsProto.Event): Event {
        val voiceEvent = event.voiceEvent
        val action = when (voiceEvent.type) {
            VoiceEventType.CALL_INITIATED -> "initiated"
            VoiceEventType.CALL_RINGING -> "ringing"
            VoiceEventType.CALL_ANSWERED -> "answered"
            VoiceEventType.CALL_ENDED -> "ended"
            VoiceEventType.CALL_FWD_VOICEMAIL -> "forwarded to voicemail"
            else -> "unknown"
        }

        return CallEnvelope(
            metadata = Metadata(event.timestamp.toInstant(), user, "call"),
            call = Call(
                from = voiceEvent.fromNumber.e164,
                to = voiceEvent.toNumber.e164,
                action = action,
                hiddenCaller = voiceEvent.callerIdHidden,
            ),
        )
    }

    private fun toSms(user: String, event: EventsProto.Event): Event? {
        val smsEvent = event.smsEvent
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
            metadata = Metadata(event.timestamp.toInstant(), user, "sms"),
            sms = Sms(from = from, to = to, content = content),
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EventsV0Listener::class.java)
    }
}
