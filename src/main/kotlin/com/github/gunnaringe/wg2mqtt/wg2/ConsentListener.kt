package com.github.gunnaringe.wg2mqtt.wg2

import com.github.gunnaringe.wg2mqtt.Events
import com.github.gunnaringe.wg2mqtt.model.Consent
import com.github.gunnaringe.wg2mqtt.model.ConsentEnvelope
import com.github.gunnaringe.wg2mqtt.model.Metadata
import com.github.gunnaringe.wg2mqtt.toInstant
import com.google.protobuf.empty
import com.wgtwo.api.v1.consent.ConsentEventServiceGrpc
import com.wgtwo.api.v1.consent.ConsentEventsProto.AckConsentChangeEventRequest
import com.wgtwo.api.v1.consent.ConsentEventsProto.ConsentChangeEvent.TypeCase
import com.wgtwo.api.v1.consent.ConsentEventsProto.StreamConsentChangeEventsRequest
import com.wgtwo.api.v1.consent.ConsentEventsProto.StreamConsentChangeEventsResponse
import com.wgtwo.api.v1.events.EventsProto.DurableQueue
import com.wgtwo.api.v1.events.EventsProto.StreamConfiguration
import com.wgtwo.auth.ClientCredentialSource
import io.grpc.Channel
import org.slf4j.LoggerFactory

class ConsentListener(
    channel: Channel,
    tokenSource: ClientCredentialSource,
    private val eventQueue: String?,
) : StreamListener("consent-events") {
    private val stub = ConsentEventServiceGrpc.newBlockingStub(channel)
        .withCallCredentials(tokenSource.callCredentials())

    override fun stream() {
        val request = StreamConsentChangeEventsRequest.newBuilder().apply {
            streamConfiguration = StreamConfiguration.newBuilder().apply {
                startAtOldestPossible = empty {}
                if (eventQueue != null) {
                    durableQueue = DurableQueue.newBuilder().setCustomName(eventQueue).build()
                }
            }.build()
        }.build()

        stub.streamConsentChangeEvents(request).forEach { response ->
            val event = toEvent(response)
            if (event != null) {
                Events.inbox.post(event)
            } else {
                logger.info("Received consent change event for unknown user")
            }
            stub.ackConsentChangeEvent(
                AckConsentChangeEventRequest.newBuilder().setAckInfo(response.metadata.ackInfo).build(),
            )
        }
    }

    private fun toEvent(response: StreamConsentChangeEventsResponse): ConsentEnvelope? {
        val user = response.consentChangeEvent.number.e164.removePrefix("+")
        if (user.isEmpty()) return null

        val change = response.consentChangeEvent
        val (action, scopes) = when (change.typeCase) {
            TypeCase.ADDED -> "added" to change.added.scopesList
            TypeCase.UPDATED -> "updated" to change.updated.scopesList
            TypeCase.REVOKED -> "revoked" to emptyList<String>()
            else -> "unknown" to emptyList<String>()
        }

        return ConsentEnvelope(
            metadata = Metadata(
                timestamp = response.metadata.timestamp.toInstant(),
                user = user,
                type = "consent",
            ),
            consent = Consent(
                subscription = response.metadata.identifier.subscriptionIdentifier.value,
                action = action,
                scopes = scopes,
            ),
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConsentListener::class.java)
    }
}
