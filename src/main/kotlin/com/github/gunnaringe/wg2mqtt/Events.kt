package com.github.gunnaringe.wg2mqtt

import com.google.common.eventbus.AsyncEventBus
import com.google.common.eventbus.DeadEvent
import com.google.common.eventbus.Subscribe
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

object Events {
    private val logger = LoggerFactory.getLogger(Events::class.java)
    private val executor = Executors.newCachedThreadPool()

    val inbox = AsyncEventBus("inbox", executor)
    val outbox = AsyncEventBus("outbox", executor)

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
