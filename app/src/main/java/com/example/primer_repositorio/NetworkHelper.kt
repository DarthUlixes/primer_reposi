package com.example.primer_repositorio

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object NetworkHelper {
    private val client = OkHttpClient()

    /**
     * Lógica de la Diapositiva 34: Realiza una petición GET
     */
    fun get(url: String, callback: (String?) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("FETCH", "Error: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.d("FETCH", "Error en la respuesta: ${response.code}")
                        callback(null)
                    } else {
                        val responseData = response.body?.string()
                        Log.d("FETCH", "Respuesta: $responseData")
                        callback(responseData)
                    }
                }
            }
        })
    }

    /**
     * Lógica de la Diapositiva 35: Realiza una petición POST enviando un JSON
     */
    fun post(url: String, jsonBody: String) {
        val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonBody.toRequestBody(JSON)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("POST", "Error al enviar: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e("POST", "Error en servidor: ${response.code}")
                    } else {
                        val result = response.body?.string()
                        Log.d("POST", "Respuesta del servidor: $result")
                    }
                }
            }
        })
    }
}
