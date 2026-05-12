package com.viametrics.seraph.processing

import java.time.Instant

data class NormalizedReading(
    val sensorId: String,
    val bucketTimestamp: Instant,
    val deltaSum: Long,
    val deltaTimeSecondsSum: Long
)

data class BucketAccumulator(
    var deltaSum: Long = 0,
    var deltaTimeSecondsSum: Long = 0
)


fun floorToQuarter(timestamp: Instant): Instant {
    val seconds = timestamp.epochSecond
    val quarter = 15 * 60L
    val floored = (seconds / quarter) * quarter
    return Instant.ofEpochSecond(floored)
}
