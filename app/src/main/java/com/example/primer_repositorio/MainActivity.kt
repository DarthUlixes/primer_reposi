package com.example.primer_repositorio

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editText = findViewById<EditText>(R.id.editTextNombre)
        val button = findViewById<Button>(R.id.btnMostrar)
        val textView = findViewById<TextView>(R.id.txtResultado)

        button.setOnClickListener {
            val texto = editText.text.toString()
            textView.text = texto
            AlertDialog.Builder(this)
                .setTitle("Éxito")
                .setMessage("El texto se mostró con éxito")
                .setPositiveButton("OK", null)
                .show()
        }

        findViewById<Button>(R.id.btnChat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        val btnConsultar = findViewById<Button>(R.id.btnConsultarApi)
        val btnCargarMas = findViewById<Button>(R.id.btnCargarMas)
        val txtApiResultados = findViewById<TextView>(R.id.txtApiResultados)
        var todosLosDatos = ""

        findViewById<Button>(R.id.btnGuardarApi).setOnClickListener {
            val texto = editText.text.toString()
            if (texto.isNotEmpty()) {
                val url = "http://10.0.2.2:3000/guardar"
                val json = "{\"nombre\": \"$texto\", \"fecha\": \"${System.currentTimeMillis()}\"}"
                NetworkHelper.post(url, json)
            }
        }

        btnConsultar.setOnClickListener {
            val url = "http://10.0.2.2:3000/datos"
            NetworkHelper.get(url) { json ->
                runOnUiThread {
                    if (json != null) {
                        try {
                            val jsonArray = org.json.JSONArray(json)
                            val total = jsonArray.length()
                            val lista = mutableListOf<String>()
                            
                            // Guardar todos para el botón "Cargar más"
                            val fullList = mutableListOf<String>()
                            for (i in 0 until total) {
                                val obj = jsonArray.getJSONObject(i)
                                fullList.add("${obj.optString("nombre")} (${obj.optString("fecha")})")
                            }
                            todosLosDatos = fullList.joinToString("\n")

                            // Mostrar solo los últimos 5
                            val inicio = if (total > 5) total - 5 else 0
                            for (i in inicio until total) {
                                val obj = jsonArray.getJSONObject(i)
                                lista.add("${obj.optString("nombre")} (${obj.optString("fecha")})")
                            }
                            
                            txtApiResultados.text = "Últimos 5 registros:\n" + lista.reversed().joinToString("\n")
                            
                            if (total > 5) btnCargarMas.visibility = android.view.View.VISIBLE
                            else btnCargarMas.visibility = android.view.View.GONE

                        } catch (e: Exception) {
                            txtApiResultados.text = "Error al leer datos"
                        }
                    } else {
                        txtApiResultados.text = "No se pudo conectar con la API"
                    }
                }
            }
        }

        btnCargarMas.setOnClickListener {
            txtApiResultados.text = "Todos los registros:\n$todosLosDatos"
            btnCargarMas.visibility = android.view.View.GONE
        }
    }
}