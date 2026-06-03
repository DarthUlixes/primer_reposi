package com.example.primer_repositorio

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

            // Pasar texto al label
            textView.text = texto

            // Mostrar alerta
            AlertDialog.Builder(this)
                .setTitle("Éxito")
                .setMessage("El texto se mostró con éxito")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}