package com.viametrics.seraph

import com.viametrics.seraph.model.RawReading
import com.viametrics.seraph.processing.SensorDeltaProcessor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class SensorDeltaProcessorTest {

    @Test
    fun `delta räknas korrekt`() {
        val processor = SensorDeltaProcessor()

        val t1 = Instant.parse("2026-04-28T12:00:00Z")
        val t2 = Instant.parse("2026-04-28T12:15:00Z")

        processor.process(RawReading("A", t1, 100))

        val result = processor.process(RawReading("A", t2, 150))

        Assertions.assertEquals(50, result!!.delta)
    }
}