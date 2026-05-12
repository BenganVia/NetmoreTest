package com.viametrics.seraph.imbuildings

import com.viametrics.seraph.processing.BucketAccumulator
import com.viametrics.seraph.processing.NormalizedReading
// import com.viametrics.seraph.decodeImBuildingsPayloadAuto
import com.viametrics.seraph.processing.floorToQuarter
import com.viametrics.seraph.model.RawReading
import com.viametrics.seraph.netmore.NetmoreClient
import com.viametrics.seraph.processing.SensorDeltaProcessor
import com.viametrics.seraph.processing.floorToQuarter
import com.viametrics.seraph.via.ViametricsUploadClient
import com.viametrics.seraph.tryBase64ToHex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class IMBuildingsClient {

    suspend fun IMBuildingsOutdoor(
        lasDBdate: String,
        devEui: String,
        viaID: String
    ) {

        try {
            val netmoreClient = NetmoreClient(
                username = System.getenv("NETMORE_USERNAME"),
                password = System.getenv("NETMORE_PASSWORD")
            )
            val devEui = devEui
            val formatter = DateTimeFormatter.ISO_DATE

            val today = LocalDate.now()
            val yesterday = today.minusDays(1)

            val fromDate = yesterday.format(formatter)
            val toDate = today.plusDays(1).format(formatter)

            val values = netmoreClient.getSensorValues(
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

            val uploadString = buildViaMetricsPayload(
                readings = normalizedReadings,
                viaId = viaID
            )

            println(uploadString)

//            netmoreClient.close()

            val viaClient = ViametricsUploadClient(
                email = System.getenv("VIAMETRICS_USERNAME"),
                password = System.getenv("VIAMETRICS_PASSWORD")
            )

            val csv = uploadString

            val result = viaClient.uploadCounterData(csv)
            println(result)

            netmoreClient.close()
            viaClient.close()
            println("Client skapad")
        } catch (e: Exception) {
            e.printStackTrace()
        }

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

    fun decodeImBuildingsPayloadAuto(value: String): ImBuildingsPayload {
        return try {
            decodeImBuildingsPayload(value)
        } catch (_: Exception) {
            val hexPayload = tryBase64ToHex(value)
            decodeImBuildingsPayload(hexPayload)
        }
    }

    fun buildViaMetricsPayload(
        readings: List<NormalizedReading>,
        viaId: String
    ): String {

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC)

        return readings.joinToString("\n") { reading ->

            val ts = formatter.format(reading.bucketTimestamp)

            "${ts},${reading.deltaSum},0,3600,$viaId"
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



}