package com.github.gunnaringe.wg2mqtt

import com.github.gunnaringe.wg2mqtt.model.Event
import com.google.common.eventbus.AsyncEventBus
import com.google.common.eventbus.DeadEvent
import com.google.common.eventbus.SubscriberExceptionHandler
import com.google.common.eventbus.Subscribe
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

/** In-process buses: `inbox` carries events from WG2 to MQTT, `outbox` from MQTT to WG2. */
object Events {
    private val logger = LoggerFactory.getLogger(Events::class.java)
    private val executor = Executors.newCachedThreadPool()
    // Only log the event type and user: events can carry secrets, e.g. the welcome SMS password
    private val exceptionHandler = SubscriberExceptionHandler { e, context ->
        val event = context.event
        val user = (event as? Event)?.metadata?.user
        logger.error("Subscriber ${context.subscriber.javaClass.simpleName} failed on ${event.javaClass.simpleName} for user $user", e)
    }

    val inbox = AsyncEventBus(executor, exceptionHandler)
    val outbox = AsyncEventBus(executor, exceptionHandler)

    init {
        inbox.register(this)
        outbox.register(this)
    }

    @Subscribe
    private fun deadLetter(event: DeadEvent) {
        logger.warn("Got dead letter: source=${event.source}, event=${event.event}")
    }

    fun close() {
        inbox.unregister(this)
        outbox.unregister(this)
        executor.shutdown()
    }
}
