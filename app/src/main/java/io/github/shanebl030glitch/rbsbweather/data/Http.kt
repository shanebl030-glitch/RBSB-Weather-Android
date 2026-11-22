// A small HTTPS client (no extra libraries), with limits and friendly errors.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/** Something went wrong getting the weather. The message is written for the user. */
open class WeatherException(message: String, val retryable: Boolean = false, val status: Int? = null) :
    Exception(message)

fun interface HttpClient {
    /** GET a URL; returns the body. Throws WeatherException with a user-friendly message. */
    fun get(url: String, headers: Map<String, String>): String
}

object Http {
    const val MAX_BYTES = 5 * 1024 * 1024
    val USER_AGENT = "RBSB-Weather-Android/${Version.NAME} (github.com/shanebl030-glitch/RBSB-Weather-Android; " +
        "Created By Shane BlackbuRn)"

    fun query(base: String, params: Map<String, String>): String =
        base + "?" + params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }

    val default = HttpClient { url, headers ->
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw WeatherException("Couldn't open the connection.")
        }
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Encoding", "identity")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            if (code == 204) throw WeatherException("The service had no data for this request.", status = 204)
            if (code !in 200..299) {
                throw WeatherException(
                    when (code) {
                        429 -> "The weather service is busy (too many requests). It will try again soon."
                        in 500..599 -> "The weather service had a problem (HTTP $code)."
                        else -> "The weather service returned an error (HTTP $code)."
                    }, retryable = code == 429 || code >= 500, status = code)
            }
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw WeatherException("The weather service sent far more data than expected.")
                }
                out.toByteArray()
            }
            String(bytes, Charsets.UTF_8)
        } catch (e: WeatherException) {
            throw e
        } catch (e: UnknownHostException) {
            throw WeatherException("No internet connection (couldn't find the weather service).", retryable = true)
        } catch (e: SocketTimeoutException) {
            throw WeatherException("The weather service took too long to answer.", retryable = true)
        } catch (e: IOException) {
            throw WeatherException("Couldn't reach the weather service (${e.message ?: "connection problem"}).", retryable = true)
        } finally {
            conn.disconnect()
        }
    }
}
