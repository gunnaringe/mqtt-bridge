package com.github.gunnaringe.wg2mqtt.wg2

import com.github.gunnaringe.wg2mqtt.Events
import com.github.gunnaringe.wg2mqtt.Metrics
import com.github.gunnaringe.wg2mqtt.model.SmsEnvelope
import com.google.common.eventbus.Subscribe
import com.wgtwo.api.v1.sms.SmsProto.SendTextFromSubscriberRequest
import com.wgtwo.api.v1.sms.SmsServiceGrpc
import com.wgtwo.auth.ClientCredentialSource
import io.grpc.Channel
import org.slf4j.LoggerFactory

/** Sends SMS posted to the outbox through WG2. */
class SmsSender(channel: Channel, tokenSource: ClientCredentialSource) {
    private val stub = SmsServiceGrpc.newBlockingStub(channel)
        .withCallCredentials(tokenSource.callCredentials())

    init {
        Events.outbox.register(this)
    }

    @Subscribe
    fun onEvent(envelope: SmsEnvelope) {
        val request = SendTextFromSubscriberRequest.newBuilder()
            .setFromSubscriber(envelope.sms.from)
            .setToAddress(envelope.sms.to)
            .setContent(envelope.sms.content)
            .build()
        logger.info("Sending SMS: from=${request.fromSubscriber} to=${request.toAddress}")
        val response = try {
            stub.sendTextFromSubscriber(request)
        } catch (e: Exception) {
            failed.increment()
            throw e
        }
        sent.increment()
        logger.info("SMS sent: status=${response.status} messageId=${response.messageId}")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SmsSender::class.java)
        private val sent = Metrics.counter("wg2.sms.sent", "result", "success")
        private val failed = Metrics.counter("wg2.sms.sent", "result", "failure")
    }
}
