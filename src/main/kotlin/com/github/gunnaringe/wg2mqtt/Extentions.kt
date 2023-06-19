@file:OptIn(ExperimentalUnsignedTypes::class)

package com.github.gunnaringe.wg2mqtt

import com.google.protobuf.Timestamp
import java.time.Instant

fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanos.toLong())
fun UByteArray.asString(): String = this.toByteArray().toString(Charsets.UTF_8)
