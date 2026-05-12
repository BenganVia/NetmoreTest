package com.viametrics.seraph

import com.viametrics.seraph.model.ProcessedReading
import com.viametrics.seraph.model.RawReading
import com.viametrics.seraph.processing.SensorDeltaProcessor
import com.viametrics.seraph.netmore.NetmoreClient
import com.viametrics.seraph.imbuildings.IMBuildingsClient
import com.viametrics.seraph.via.ViametricsUploadClient
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

suspend fun main() {

    val devEui = "0004A30B00EDDEB6"
    val viaID = "30954"
    val formatter = DateTimeFormatter.ISO_DATE

    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val yesterday = today.minusDays(20)
// TODO. At first startup we need to find the last data, and read up from that point
    val fromDate = yesterday.format(formatter)
    val toDate = tomorrow.format(formatter)
    val lastData = fromDate

//    IMBuildingsOutdoor(lastData,devEui, viaID )
    val imBuildings = IMBuildingsClient()
    imBuildings.IMBuildingsOutdoor(lastData, devEui, viaID)

}




fun tryBase64ToHex(value: String): String {
    val raw = Base64.getDecoder().decode(value)
    return raw.joinToString("") { "%02X".format(it.toUByte().toInt()) }
}



fun findLastData() {
    // TODO, pretty much everything.
    /*
    val fromTime = db.getLatestProcessedTimestamp()?.minus(Duration.ofMinutes(30))
        ?: Instant.now().minus(Duration.ofDays(1))

    val rawReadings = api.getReadings(fromTime)

    val processor = SensorDeltaProcessor()

    rawReadings
        .sortedWith(compareBy<RawReading> { it.sensorId }.thenBy { it.timestamp })
        .forEach { reading ->
            val processed = processor.process(reading)

            if (processed != null && processed.timestamp > db.getLatestProcessedTimestampForSensor(reading.sensorId)) {
                db.saveProcessedReading(processed)
            }
        }  */
}

/*
fun NetmoreTest() = runBlocking {
    val client = ViametricsUploadClient(
        email = System.getenv("VIAMETRICS_USERNAME"),
        password = System.getenv("VIAMETRICS_PASSWORD")
    )

    val csv = "någon,kommaseparerad,sträng"

    val result = client.uploadCounterData(csv)
    println(result)

    client.close()

}
*/