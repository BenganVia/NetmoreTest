package com.viametrics.seraph.processing

import com.viametrics.seraph.model.ProcessedReading
import com.viametrics.seraph.model.RawReading
import java.time.Duration

class SensorDeltaProcessor {
    private val lastValues = mutableMapOf<String, RawReading>()

    fun process(reading: RawReading): ProcessedReading? {
        val previous = lastValues[reading.sensorId]
        lastValues[reading.sensorId] = reading

        if (previous == null) return null

        val delta =
            if (reading.counterValue >= previous.counterValue) {
                reading.counterValue - previous.counterValue
            } else {
                // Reset
                reading.counterValue
            }

        val deltaTimeSeconds =
            Duration.between(previous.timestamp, reading.timestamp).seconds

        return ProcessedReading(
            sensorId = reading.sensorId,
            timestamp = reading.timestamp,
            counterValue = reading.counterValue,
            delta = delta,
            deltaTimeSeconds = deltaTimeSeconds
        )
    }
}
