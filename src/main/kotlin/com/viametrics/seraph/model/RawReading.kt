package com.viametrics.seraph.model

import java.time.Instant

data class RawReading(
    val sensorId: String,
    val timestamp: Instant,
    val counterValue: Long
)
