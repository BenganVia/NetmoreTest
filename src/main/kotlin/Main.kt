package org.example

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import java.time.Duration
import java.util.Base64
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@Serializable
data class NetmoreSensorValueDto(
    val payloadHex: String,
    val commTimestamp: String,
    val batteryLevel: String? = null
)

@Serializable
data class NetmoreLoginRequest(
    val password: String
)

@Serializable
data class NetmoreLoginResponse(
    val success: Boolean,
    val status: String? = null,
    val message: String? = null,
    val token: String? = null
)

suspend fun main() {

    val devEui = "0004A30B00EDDEB6"
    val viaID = "3000"
    val formatter = DateTimeFormatter.ISO_DATE

    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
// TODO. At first startup we need to find the last data, and read up from that point
    val fromDate = yesterday.format(formatter)
    val toDate = today.format(formatter)
    val lastData = fromDate

    IMBuildingsOutdoor(lastData,devEui, viaID )

}

suspend fun IMBuildingsOutdoor(
    lasDBdate: String,
    devEui: String,
    viaID: String
) {

    try {
        val client = NetmoreClient(
            username = System.getenv("NETMORE_USERNAME"),
            password = System.getenv("NETMORE_PASSWORD")
        )
        val devEui = devEui
        val formatter = DateTimeFormatter.ISO_DATE

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        val fromDate = yesterday.format(formatter)
        val toDate = today.plusDays(1).format(formatter)

        val values = client.getSensorValues(
            devEui = devEui,
            fromDate = fromDate,
            toDate = toDate
        )

        val cleanValues = values
            .distinctBy { it.payloadHex to it.commTimestamp }
            .sortedBy { Instant.parse(it.commTimestamp) }

        val processor = SensorDeltaProcessor()
        val buckets = mutableMapOf<Pair<String, Instant>, BucketAccumulator>()

        cleanValues
            .sortedBy { Instant.parse(it.commTimestamp) }
            .forEach { item ->

                val decoded = decodeImBuildingsPayloadAuto(item.payloadHex)

                val raw = RawReading(
                    sensorId = decoded.deviceId,
                    timestamp = Instant.parse(item.commTimestamp),
                    counterValue = decoded.totalCounterA.toLong()
                )

                val processed = processor.process(raw)

// Visa alltid rådata
                println("Tid: ${item.commTimestamp}")
                println("Counter A: ${decoded.counterA}")
                println("Counter B: ${decoded.counterB}")

                processed?.let {
                    val normalized = floorToQuarter(it.timestamp)
                    val key = it.sensorId to normalized

                    val bucket = buckets.getOrPut(key) { BucketAccumulator() }

                    bucket.deltaSum += it.delta
                    bucket.deltaTimeSecondsSum += it.deltaTimeSeconds

                    println("Delta: ${it.delta}")
                    println("Delta tid (sek): ${it.deltaTimeSeconds}")
                    println("Normalized: $normalized")
                    println("Normalized: ${bucket.deltaTimeSecondsSum}")
                }

                println("------")
            }
        val normalizedReadings = buckets.map { (key, bucket) ->
            NormalizedReading(
                sensorId = key.first,
                bucketTimestamp = key.second,
                deltaSum = bucket.deltaSum,
                deltaTimeSecondsSum = bucket.deltaTimeSecondsSum
            )
        }

        client.close()

        // NetmoreTest()

        println("Client skapad")
    } catch (e: Exception) {
        e.printStackTrace()
    }

}
class NetmoreClient(
    private val username: String,
    private val password: String
) {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    private var token: String? = null

    private suspend fun login(): String {
        val response: NetmoreLoginResponse =
            httpClient.post("https://api.blink.services/rest/core/login/$username") {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(NetmoreLoginRequest(password))
            }.body()
        val responseText: String? = response.message
        println(responseText)

       // val loginResponse = Json.decodeFromString<LoginResponse>(responseText)
        if (!response.success || response.token.isNullOrBlank()) {
            throw RuntimeException("Netmore login failed: ${response.status} ${response.message}")
        }

        token = response.token
        return response.token
    }

    private suspend fun getToken(): String {
        return token ?: login()
    }

    suspend fun getSensorValues(
        devEui: String,
        fromDate: String? = null,
        toDate: String? = null
    ): List<NetmoreSensorValueDto> {
        val bearerToken = getToken()

        val response: HttpResponse =
            httpClient.get("https://api.blink.services/rest/net/sensors/$devEui/values") {
                accept(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $bearerToken")

                if (fromDate != null) parameter("fromDate", fromDate)
                if (toDate != null) parameter("toDate", toDate)
            }

        val bodyText = response.bodyAsText()

        if (!response.status.isSuccess()) {
            throw RuntimeException("Netmore get values failed: HTTP ${response.status.value} - $bodyText")
        }

        return Json {
            ignoreUnknownKeys = true
        }.decodeFromString(bodyText)
    }

    fun close() {
        httpClient.close()
    }
}

data class ImBuildingsPayload(
    val payloadType: Int,
    val payloadVariant: Int,
    val deviceId: String,
    val deviceStatus: Int,
    val batteryVoltageV: Double,
    val counterA: Int,
    val counterB: Int,
    val sensorStatus: Int,
    val totalCounterA: Int,
    val totalCounterB: Int,
    val payloadCounter: Int
)

data class RawReading(
    val sensorId: String,
    val timestamp: Instant,
    val counterValue: Long
)

data class ProcessedReading(
    val sensorId: String,
    val timestamp: Instant,          // originaltid
    val counterValue: Long,          // raw counter
    val delta: Long,
    val deltaTimeSeconds: Long
)

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

fun floorToQuarter(timestamp: Instant): Instant {
    val seconds = timestamp.epochSecond
    val quarter = 15 * 60L
    val floored = (seconds / quarter) * quarter
    return Instant.ofEpochSecond(floored)
}

fun decodeImBuildingsPayload(payloadHex: String): ImBuildingsPayload {
    val cleanHex = payloadHex.replace(" ", "").trim()

    require(cleanHex.length % 2 == 0) {
        "Hex-strängen måste innehålla ett jämnt antal tecken."
    }

    val data = cleanHex.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()

    require(data.size == 23) {
        "Fel payloadlängd: ${data.size} bytes. För IMBuildings type 2 / variant 6 väntas 23 bytes."
    }

    val payloadType = data[0].toUByte().toInt()
    val payloadVariant = data[1].toUByte().toInt()

    require(payloadType == 2 && payloadVariant == 6) {
        "Fel payloadformat: type=$payloadType, variant=$payloadVariant. Detta stödjer bara type 2 / variant 6."
    }

    fun u8(index: Int): Int =
        data[index].toUByte().toInt()

    fun u16(msbIndex: Int): Int =
        (u8(msbIndex) shl 8) or u8(msbIndex + 1)

    return ImBuildingsPayload(
        payloadType = payloadType,
        payloadVariant = payloadVariant,
        deviceId = data.sliceArray(2 until 10)
            .joinToString("") { "%02X".format(it.toUByte().toInt()) },
        deviceStatus = u8(10),
        batteryVoltageV = u16(11) / 100.0,
        counterA = u16(13),
        counterB = u16(15),
        sensorStatus = u8(17),
        totalCounterA = u16(18),
        totalCounterB = u16(20),
        payloadCounter = u8(22)
    )
}

fun tryBase64ToHex(value: String): String {
    val raw = Base64.getDecoder().decode(value)
    return raw.joinToString("") { "%02X".format(it.toUByte().toInt()) }
}

fun decodeImBuildingsPayloadAuto(value: String): ImBuildingsPayload {
    return try {
        decodeImBuildingsPayload(value)
    } catch (_: Exception) {
        val hexPayload = tryBase64ToHex(value)
        decodeImBuildingsPayload(hexPayload)
    }
}

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val jwt: String,
    val refreshToken: String,
    val platformToken: String
)

class ViametricsUploadClient(
    private val email: String,
    private val password: String
)
{
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    private var jwtToken: String? = null

    private suspend fun login(): String {
        val response: LoginResponse =
            httpClient.post("https://matrix.api.viametrics.com/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(
                    LoginRequest(
                        email = email,
                        password = password
                    )
                )
            }.body()

        jwtToken = response.jwt
        return response.jwt
    }

    suspend fun uploadCounterData(
        csvData: String,
        fileName: String? = null
    ): String {
        val token = jwtToken ?: login()

        val response: HttpResponse =
            httpClient.post("https://matrix.api.viametrics.com/v1/admin/import/counterdata") {
                parameter("period", 900)
                parameter("mode", "SAVE_NEW")

                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Text.Plain)

                setBody(csvData)
            }

        val responseText = response.bodyAsText()

        if (!response.status.isSuccess()) {
            throw RuntimeException(
                "Upload failed: HTTP ${response.status.value} - $responseText"
            )
        }

        return responseText
    }

    fun close() {
        httpClient.close()
    }
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
