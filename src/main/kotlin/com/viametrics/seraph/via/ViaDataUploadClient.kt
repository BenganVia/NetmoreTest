package com.viametrics.seraph.via

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val jwt: String,
    val refreshToken: String
//    val platformToken: String
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

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneOffset.UTC)

    private var jwtToken: String? = null

    private suspend fun login2(): String {
        val response: LoginResponse =
            httpClient.post("https://matrix.api.viametrics.com/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(
                    LoginRequest(
                        email =  System.getenv("VIAMETRICS_USERNAME"),
                        password =  System.getenv("VIAMETRICS_PASSWORD")
                    )
                )
            }.body()

        jwtToken = response.jwt
        return response.jwt
    }

    private suspend fun login(): String {
        val response: HttpResponse =
            httpClient.post("https://auth.api.viametrics.com/v1/auth/blogin") {
                contentType(ContentType.Application.Json)
                setBody(
                    LoginRequest(
                        email =  System.getenv("VIAMETRICS_USERNAME"),
                        password =  System.getenv("VIAMETRICS_PASSWORD")
                    )
                )
            }

        val bodyText = response.bodyAsText()

        println("Viametrics login HTTP ${response.status.value}")
        println(bodyText)

        if (!response.status.isSuccess()) {
            throw RuntimeException("Viametrics login failed: HTTP ${response.status.value} - $bodyText")
        }

        val loginResponse = Json {
            ignoreUnknownKeys = true
        }.decodeFromString<LoginResponse>(bodyText)

        return loginResponse.jwt
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
