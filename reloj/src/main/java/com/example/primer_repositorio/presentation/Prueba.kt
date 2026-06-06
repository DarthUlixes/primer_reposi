package com.example.primer_repositorio.presentation

import android.content.Intent
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.example.primer_repositorio.R

class Prueba : ComponentActivity(), SensorEventListener {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ocultarIcono = Runnable {
        findViewById<ImageView>(R.id.imgCrash).visibility = View.GONE
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var txtSensor: TextView
    private lateinit var btnIniciarSensor: Button
    private lateinit var btnDetenerSensor: Button

    private var sensor: Sensor? = null
    private var sensorType = Sensor.TYPE_HEART_RATE
    private var sensorActivo = false
    private var pendienteIniciarSensor = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.prueba)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(sensorType)

        txtSensor = findViewById(R.id.txtSensor)
        btnIniciarSensor = findViewById(R.id.btnIniciarSensor)
        btnDetenerSensor = findViewById(R.id.btnDetenerSensor)

        if (sensor == null) {
            txtSensor.text = getString(R.string.sensor_no_disponible)
            btnIniciarSensor.isEnabled = false
            btnDetenerSensor.isEnabled = false
        }

        findViewById<Button>(R.id.btnAudio).setOnClickListener {
            reproducirAudio()
            mostrarIconoCrash()
        }

        btnIniciarSensor.setOnClickListener { solicitarInicioSensor() }
        btnDetenerSensor.setOnClickListener { detenerSensor() }

        findViewById<Button>(R.id.btnAbrirChat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        findViewById<Button>(R.id.boton2).setOnClickListener { finish() }

        actualizarBotonesSensor()
    }

    private fun solicitarInicioSensor() {
        if (sensor == null) return

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendienteIniciarSensor = true
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                PERMISO_SENSORES
            )
            return
        }

        iniciarSensor()
    }

    private fun iniciarSensor() {
        if (sensor == null) return

        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorActivo = true
        txtSensor.text = getString(R.string.sensor_esperando)
        actualizarBotonesSensor()
        Log.d(TAG, "Sensor iniciado")
    }

    private fun detenerSensor() {
        sensorManager.unregisterListener(this)
        sensorActivo = false
        txtSensor.text = getString(R.string.sensor_detenido)
        actualizarBotonesSensor()
        Log.d(TAG, "Sensor detenido")
    }

    private fun actualizarBotonesSensor() {
        val disponible = sensor != null
        btnIniciarSensor.isEnabled = disponible && !sensorActivo
        btnDetenerSensor.isEnabled = disponible && sensorActivo
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!sensorActivo || event?.sensor?.type != sensorType) return

        val lectura = event.values[0]
        Log.d("onSensorChanged", "Lectura: $lectura")
        txtSensor.text = getString(R.string.sensor_lectura, lectura)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onPause() {
        super.onPause()
        if (sensorActivo) {
            sensorManager.unregisterListener(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (sensorActivo && sensor != null &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISO_SENSORES) return

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendienteIniciarSensor) {
                pendienteIniciarSensor = false
                iniciarSensor()
            }
        } else {
            pendienteIniciarSensor = false
            txtSensor.text = getString(R.string.sensor_permiso_denegado)
        }
    }

    private fun reproducirAudio() {
        player?.release()
        player = MediaPlayer.create(this, R.raw.crashlaugh)
        player?.start()
    }

    private fun mostrarIconoCrash() {
        val imgCrash = findViewById<ImageView>(R.id.imgCrash)
        handler.removeCallbacks(ocultarIcono)
        imgCrash.visibility = View.VISIBLE
        handler.postDelayed(ocultarIcono, 2000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ocultarIcono)
        sensorManager.unregisterListener(this)
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Prueba"
        private const val PERMISO_SENSORES = 1001
    }
}
