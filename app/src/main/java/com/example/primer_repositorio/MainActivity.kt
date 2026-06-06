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
    }
}