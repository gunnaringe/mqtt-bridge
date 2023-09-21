package com.github.gunnaringe.wg2mqtt.wg2

import com.github.gunnaringe.wg2mqtt.Events
import com.github.gunnaringe.wg2mqtt.model.SmsEnvelope
import com.google.common.eventbus.Subscribe
import com.wgtwo.api.v1.sms.SmsProto.SendTextFromSubscriberRequest
import com.wgtwo.api.v1.sms.SmsServiceGrpc
import com.wgtwo.auth.ClientCredentialSource
import io.grpc.Channel
import org.slf4j.LoggerFactory

class SmsSender(private val channel: Channel, private val tokenSource: ClientCredentialSource) {

    fun subscribe() = Unit

    init {
        logger.info("Starting SMS sender")
        Events.outbox.register(this)
    }

    @Subscribe
    fun onEvent(envelope: SmsEnvelope) {
        val stub = SmsServiceGrpc.newBlockingStub(channel)
            .withCallCredentials(tokenSource.callCredentials())

        val request = SendTextFromSubscriberRequest.newBuilder().apply {
            this.fromSubscriber = envelope.sms.from
            this.toAddress = envelope.sms.to
            this.content = envelope.sms.content
        }.build()
        logger.info("Sending SMS: $request")
        stub.sendTextFromSubscriber(request)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SmsSender::class.java)
    }
}
