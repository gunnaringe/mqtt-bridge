package com.github.gunnaringe.wg2mqtt

import com.github.gunnaringe.wg2mqtt.users.Users
import com.sun.net.httpserver.HttpServer
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/** Prometheus metrics, served on `/metrics`. */
object Metrics {
    private val logger = LoggerFactory.getLogger(Metrics::class.java)

    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT).apply {
        listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ProcessorMetrics(),
            UptimeMetrics(),
        ).forEach { it.bindTo(this) }
    }

    fun counter(name: String, vararg tags: String) = registry.counter(name, *tags)

    /** Registered users, counted in the database on each scrape. */
    fun registerUserGauge() {
        Gauge.builder("mqtt_bridge.users") { transaction { Users.selectAll().count() } }
            .description("Registered users")
            .register(registry)
    }

    fun serve(port: Int): HttpServer = HttpServer.create(InetSocketAddress(port), 0).apply {
        createContext("/metrics") { exchange ->
            exchange.use {
                val body = registry.scrape().toByteArray()
                it.responseHeaders.add("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
                it.sendResponseHeaders(200, body.size.toLong())
                it.responseBody.write(body)
            }
        }
        start()
        logger.info("Serving metrics on :$port/metrics")
    }
}
