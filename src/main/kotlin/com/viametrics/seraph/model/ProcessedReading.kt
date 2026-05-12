package com.viametrics.seraph.model

import java.time.Instant

data class ProcessedReading(
    val sensorId: String,
    val timestamp: Instant,          // originaltid
    val counterValue: Long,          // raw counter
    val delta: Long,
    val deltaTimeSeconds: Long
)

