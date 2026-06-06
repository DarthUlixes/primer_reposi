package com.example.primer_repositorio.presentation

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.primer_repositorio.R
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class ChatActivity : AppCompatActivity(),
    CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private lateinit var txtEstado: TextView
    private lateinit var txtDiag: TextView
    private lateinit var txtRecibidos: TextView
    private lateinit var edtMensaje: EditText
    private lateinit var btnEnviar: Button

    private var nodeId = ""
    private var localNodeId = ""
    private var deviceConnected = false
    private var lastDataTime = 0L
    private val mensajesVistos = mutableSetOf<String>()
    private val retryHandler = Handler(Looper.getMainLooper())
    private val retryRunnable = object : Runnable {
        override fun run() {
            if (!deviceConnected) {
                diagnosticarYAvisar()
                retryHandler.postDelayed(this, 5_000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        txtEstado = findViewById(R.id.txtEstado)
        txtDiag = findViewById(R.id.txtDiag)
        txtRecibidos = findViewById(R.id.txtRecibidos)
        edtMensaje = findViewById(R.id.edtMensaje)
        btnEnviar = findViewById(R.id.btnEnviar)

        btnEnviar.setOnClickListener { sendMessage() }
        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
    }

    private fun sendMessage() {
        val texto = edtMensaje.text.toString().trim()
        if (texto.isEmpty()) return
        if (!deviceConnected || nodeId.isEmpty()) {
            diagnosticarYAvisar()
            return
        }
        enviarPorMessage(texto, nodeId)
        enviarPorData(texto)
        agregarMensaje(getString(R.string.chat_enviado, texto))
        edtMensaje.text.clear()
    }

    private fun enviarPorMessage(texto: String, destinoId: String) {
        if (destinoId.isEmpty()) return
        Wearable.getMessageClient(this)
            .sendMessage(destinoId, PAYLOAD_PATH, texto.toByteArray(StandardCharsets.UTF_8))
            .addOnSuccessListener { Log.d(TAG, "Message OK → $destinoId: $texto") }
            .addOnFailureListener { e -> Log.e(TAG, "Message FAIL → $destinoId: ${e.message}", e) }
    }

    private fun enviarPorData(texto: String) {
        if (localNodeId.isEmpty()) return
        ChatDataSync.enviar(this, texto, localNodeId)
    }

    private fun diagnosticarYAvisar() {
        launch(Dispatchers.IO) {
            try {
                if (localNodeId.isEmpty()) {
                    localNodeId = Tasks.await(Wearable.getNodeClient(this@ChatActivity).localNode).id
                }
                val conectados = Tasks.await(Wearable.getNodeClient(this@ChatActivity).connectedNodes)
                    .filter { it.id != localNodeId }
                val capNodes = Tasks.await(
                    Wearable.getCapabilityClient(this@ChatActivity)
                        .getCapability(WearApp.CHAT_CAPABILITY, CapabilityClient.FILTER_ALL)
                ).nodes.filter { it.id != localNodeId }

                val destinos = (conectados.map { it.id } + capNodes.map { it.id }).distinct()
                Log.d(TAG, "Diag: conectados=${conectados.size}, capability=${capNodes.size}, local=$localNodeId")

                runOnUiThread {
                    txtDiag.text = getString(
                        R.string.chat_diag,
                        conectados.size,
                        capNodes.size
                    )
                    if (conectados.isEmpty() && capNodes.isEmpty()) {
                        txtEstado.text = getString(R.string.chat_sin_celular)
                    }
                }

                runOnUiThread { enviarPorData(READY) }
                destinos.forEach { id -> runOnUiThread { enviarPorMessage(READY, id) } }
            } catch (e: Exception) {
                Log.e(TAG, "Error diag: ${e.message}", e)
            }
        }
    }

    private fun cargarMensajesPendientes() {
        ChatDataSync.cargarUltimo(this, lastDataTime) { mensaje, fromNode, time ->
            lastDataTime = maxOf(lastDataTime, time)
            procesarMensaje(mensaje, fromNode, "data-$time")
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PAYLOAD_PATH) return
        procesarMensaje(String(event.data, StandardCharsets.UTF_8), event.sourceNodeId, "msg-${event.requestId}")
    }

    private fun procesarMensaje(message: String, sourceNodeId: String, dedupeKey: String) {
        if (sourceNodeId.isNotEmpty() && sourceNodeId == localNodeId) return
        val key = if (message == CHECK_MESSAGE || message == CONNECTION_OK || message == READY) {
            dedupeKey
        } else {
            "chat-$sourceNodeId-$message"
        }
        if (!mensajesVistos.add(key)) return

        Log.d(TAG, "Procesando: $message de $sourceNodeId")
        when (message) {
            CHECK_MESSAGE -> {
                nodeId = sourceNodeId
                deviceConnected = true
                retryHandler.removeCallbacks(retryRunnable)
                runOnUiThread { actualizarEstado() }
                enviarPorMessage(CONNECTION_OK, nodeId)
                enviarPorData(CONNECTION_OK)
            }
            CONNECTION_OK, READY -> Unit
            else -> {
                if (!deviceConnected && sourceNodeId.isNotEmpty()) {
                    nodeId = sourceNodeId
                    deviceConnected = true
                    retryHandler.removeCallbacks(retryRunnable)
                    runOnUiThread { actualizarEstado() }
                }
                runOnUiThread { agregarMensaje(getString(R.string.chat_recibido, message)) }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        lastDataTime = ChatDataSync.procesar(dataEvents, lastDataTime) { mensaje, fromNode, time ->
            procesarMensaje(mensaje, fromNode, "data-$time")
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (capabilityInfo.name != WearApp.CHAT_CAPABILITY) return
        val celulares = capabilityInfo.nodes.filter { it.id != localNodeId }
        Log.d(TAG, "Capability celular: ${celulares.size} nodo(s)")
        if (celulares.isNotEmpty()) {
            runOnUiThread {
                txtDiag.text = getString(R.string.chat_diag_celular, celulares.size)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ChatMessageHub.onMessageReceived = { message, sourceNodeId ->
            runOnUiThread { procesarMensaje(message, sourceNodeId, "hub-$message") }
        }
        ChatMessageHub.flushPending()
    }

    override fun onStop() {
        ChatMessageHub.onMessageReceived = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Chat abierto — registrando listeners")
        registrarListeners()
        launch(Dispatchers.IO) {
            try {
                localNodeId = Tasks.await(Wearable.getNodeClient(this@ChatActivity).localNode).id
                runOnUiThread {
                    cargarMensajesPendientes()
                    diagnosticarYAvisar()
                    retryHandler.postDelayed(retryRunnable, 5_000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error nodo local: ${e.message}", e)
            }
        }
    }

    override fun onPause() {
        retryHandler.removeCallbacks(retryRunnable)
        quitarListeners()
        super.onPause()
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

    private fun registrarListeners() {
        try {
            Wearable.getDataClient(this).addListener(this)
            Wearable.getMessageClient(this).addListener(this)
            Wearable.getCapabilityClient(this).addListener(
                this,
                Uri.parse("wear://*/${WearApp.CHAT_CAPABILITY}"),
                CapabilityClient.FILTER_REACHABLE
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun quitarListeners() {
        try {
            Wearable.getDataClient(this).removeListener(this)
            Wearable.getMessageClient(this).removeListener(this)
            Wearable.getCapabilityClient(this).removeListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun actualizarEstado() {
        if (deviceConnected && nodeId.isNotEmpty()) {
            txtEstado.text = getString(R.string.chat_conectado, nodeId)
            btnEnviar.isEnabled = true
        } else {
            txtEstado.text = getString(R.string.chat_esperando_celular)
            btnEnviar.isEnabled = false
        }
    }

    private fun agregarMensaje(linea: String) {
        val actual = txtRecibidos.text.toString()
        txtRecibidos.text = if (actual == getString(R.string.chat_sin_mensajes)) {
            linea
        } else {
            "$actual\n$linea"
        }
    }

    companion object {
        private const val TAG = "ChatReloj"
        private const val PAYLOAD_PATH = "/APP_OPEN"
        private const val CHECK_MESSAGE = "CHECK_MESSAGE"
        private const val CONNECTION_OK = "CONNECTION_OK"
        private const val READY = "READY"
    }
}
