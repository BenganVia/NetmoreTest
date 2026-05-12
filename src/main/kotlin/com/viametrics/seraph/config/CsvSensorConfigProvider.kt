package com.viametrics.seraph.config

import io.ktor.http.ContentDisposition.Companion.File
import java.io.File

data class SensorConfig(
    val netmoreSensorId: String,
    val viametricsId: String,
    val name: String
)

class CsvSensorConfigProvider(
    private val filePath: String
) : SensorConfigProvider {

    override fun load(): List<SensorConfig> {
        return File(filePath)
            .readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split(",")

                SensorConfig(
                    netmoreSensorId = parts[0].trim(),
                    viametricsId = parts[1].trim(),
                    name = parts[2].trim()
                )
            }
    }
}