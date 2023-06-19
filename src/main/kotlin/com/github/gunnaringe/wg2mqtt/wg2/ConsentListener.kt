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
import com.wgtwo.api.v1.events.EventsProto.AckInfo
import com.wgtwo.api.v1.events.EventsProto.DurableQueue
import com.wgtwo.api.v1.events.EventsProto.StreamConfiguration
import com.wgtwo.auth.ClientCredentialSource
import io.grpc.Channel
import io.grpc.Context
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConsentListener(
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
        val stub = ConsentEventServiceGrpc.newBlockingStub(channel)
            .withCallCredentials(tokenSource.callCredentials())

        logger.info("Subscribing to consent events")

        val request = StreamConsentChangeEventsRequest.newBuilder().apply {
            this.streamConfiguration = StreamConfiguration.newBuilder().apply {
                this.startAtOldestPossible = empty {}
                if (eventQueue != null) {
                    this.durableQueue = DurableQueue.newBuilder().apply {
                        this.customName = eventQueue
                    }.build()
                }
            }.build()
        }.build()

        context.run {
            stub.streamConsentChangeEvents(request).forEach { response ->

                val metadata = response.metadata
                val consentChangeEvent = response.consentChangeEvent
                val timestamp = metadata.timestamp.toInstant()
                val subscription = metadata.identifier.subscriptionIdentifier.value

                val action = when (consentChangeEvent.typeCase) {
                    TypeCase.ADDED -> "added"
                    TypeCase.UPDATED -> "updated"
                    TypeCase.REVOKED -> "revoked"
                    else -> "unknown"
                }
                val scopes = when (consentChangeEvent.typeCase) {
                    TypeCase.ADDED -> consentChangeEvent.added.scopesList
                    TypeCase.UPDATED -> consentChangeEvent.updated.scopesList
                    else -> emptyList()
                }

                val user = response.consentChangeEvent.number.e164.removePrefix("+")
                if (user.isNotEmpty()) {
                    val event = ConsentEnvelope(
                        metadata = Metadata(
                            timestamp = timestamp,
                            user = user,
                            type = "consent",
                        ),
                        consent = Consent(
                            subscription = subscription,
                            action = action,
                            scopes = scopes,

                        ),
                    )
                    Events.inbox.post(event)
                } else {
                    logger.info("Received consent change event for unknown user")
                }

                ack(response.metadata.ackInfo)
            }
        }
    }

    private fun ack(ackInfo: AckInfo) {
        val stub = ConsentEventServiceGrpc.newBlockingStub(channel)
            .withCallCredentials(tokenSource.callCredentials())
        val ackRequest = AckConsentChangeEventRequest.newBuilder()
            .setAckInfo(ackInfo)
            .build()
        stub.ackConsentChangeEvent(ackRequest)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConsentListener::class.java)
    }

    override fun close() {
        context.cancel(null)
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
    }
}
