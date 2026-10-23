package com.aistudy.aistudy.ask

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val MODEL = "gemini-3.5-flash"

    fun ask(apiKey: String, prompt: String): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.readTimeout = 60000
        conn.connectTimeout = 60000

        val part = JSONObject().put("text", prompt)
        val partsArray = JSONArray().put(part)
        val content = JSONObject().put("parts", partsArray)
        val contentsArray = JSONArray().put(content)
        val body = JSONObject().put("contents", contentsArray)

        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        conn.outputStream.use { os -> os.write(bytes) }

        val code = conn.responseCode
        if (code in 200..299) {
            val responseBody = conn.inputStream.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            }
            val responseJson = JSONObject(responseBody)
            val candidates = responseJson.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val contentObj = candidate.getJSONObject("content")
                val responseParts = contentObj.getJSONArray("parts")
                if (responseParts.length() > 0) {
                    return responseParts.getJSONObject(0).getString("text")
                }
            }
            return ""
        } else {
            val errorBody = conn.errorStream?.use { errorStream ->
                BufferedReader(InputStreamReader(errorStream)).readText()
            } ?: ""
            throw Exception("HTTP $code: $errorBody")
        }
    }
}
