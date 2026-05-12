package com.viametrics.seraph.netmore

// import com.viametrics.seraph.NetmoreLoginRequest
// import com.viametrics.seraph.NetmoreLoginResponse
// import com.viametrics.seraph.NetmoreSensorValueDto
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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


@Serializable
data class NetmoreSensorValueDto(
    val payloadHex: String,
    val commTimestamp: String,
    val batteryLevel: String? = null
)



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
