package com.example.primer_repositorio

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
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
    private lateinit var txtRecibidos: TextView
    private lateinit var edtMensaje: EditText
    private lateinit var btnEnviar: Button

    private var nodeId = ""
    private var localNodeId = ""
    private var deviceConnected = false
    private var lastDataTime = 0L
    private val mensajesVistos = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        txtEstado = findViewById(R.id.txtEstado)
        txtRecibidos = findViewById(R.id.txtRecibidos)
        edtMensaje = findViewById(R.id.edtMensaje)
        btnEnviar = findViewById(R.id.btnEnviar)

        findViewById<Button>(R.id.btnConectar).setOnClickListener { getNodes() }
        btnEnviar.setOnClickListener { sendMessage() }
        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }

        launch(Dispatchers.IO) {
            try {
                localNodeId = Tasks.await(Wearable.getNodeClient(this@ChatActivity).localNode).id
                Log.d(TAG, "Nodo local: $localNodeId")
            } catch (e: Exception) {
                Log.e(TAG, "Error nodo local: ${e.message}", e)
            }
        }
    }

    private fun getNodes() {
        launch(Dispatchers.IO) {
            try {
                if (localNodeId.isEmpty()) {
                    localNodeId = Tasks.await(Wearable.getNodeClient(this@ChatActivity).localNode).id
                }
                val conectados = Tasks.await(Wearable.getNodeClient(this@ChatActivity).connectedNodes)
                    .filter { it.id != localNodeId }
                val capNodes = Tasks.await(
                    Wearable.getCapabilityClient(this@ChatActivity)
                        .getCapability(PhoneApp.CHAT_CAPABILITY, CapabilityClient.FILTER_ALL)
                ).nodes.filter { it.id != localNodeId }

                val remotos = (capNodes + conectados).distinctBy { it.id }
                Log.d(TAG, "Buscar reloj: conectados=${conectados.size}, capability=${capNodes.size}")
                remotos.forEach { Log.d(TAG, "  destino: ${it.displayName} (${it.id})") }

                if (remotos.isNotEmpty()) {
                    nodeId = remotos.first().id
                    deviceConnected = true
                    runOnUiThread {
                        actualizarEstado()
                        remotos.forEach { enviarHandshake(it) }
                    }
                } else {
                    nodeId = ""
                    deviceConnected = false
                    runOnUiThread {
                        txtEstado.text = getString(R.string.chat_sin_nodos, conectados.size)
                        btnEnviar.isEnabled = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en el nodo: ${e.message}", e)
                runOnUiThread {
                    txtEstado.text = getString(R.string.chat_error_nodo)
                    btnEnviar.isEnabled = false
                }
            }
        }
    }

    private fun enviarHandshake(destino: Node) {
        nodeId = destino.id
        enviarPorMessage(CHECK_MESSAGE, destino.id)
        enviarPorData(CHECK_MESSAGE)
    }

    private fun enviarPorMessage(texto: String, destinoId: String = nodeId) {
        if (destinoId.isEmpty()) return
        Wearable.getMessageClient(this)
            .sendMessage(destinoId, PAYLOAD_PATH, texto.toByteArray(StandardCharsets.UTF_8))
            .addOnSuccessListener { Log.d(TAG, "Message enviado a $destinoId: $texto") }
            .addOnFailureListener { e -> Log.e(TAG, "Message error a $destinoId: ${e.message}", e) }
    }

    private fun enviarPorData(texto: String) {
        if (localNodeId.isEmpty()) return
        ChatDataSync.enviar(this, texto, localNodeId)
    }

    private fun sendMessage() {
        val texto = edtMensaje.text.toString().trim()
        if (texto.isEmpty()) return
        if (!deviceConnected || nodeId.isEmpty()) {
            txtEstado.text = getString(R.string.chat_sin_conexion)
            return
        }
        enviarPorMessage(texto)
        enviarPorData(texto)
        agregarMensaje(getString(R.string.chat_enviado, texto))
        edtMensaje.text.clear()
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PAYLOAD_PATH) return
        val message = String(event.data, StandardCharsets.UTF_8)
        procesarMensaje(message, event.sourceNodeId, "msg-${event.requestId}")
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
            CONNECTION_OK -> {
                if (sourceNodeId.isNotEmpty()) nodeId = sourceNodeId
                deviceConnected = true
                runOnUiThread { actualizarEstado() }
            }
            CHECK_MESSAGE -> Unit
            READY -> {
                Log.d(TAG, "Reloj listo → reenviando handshake")
                getNodes()
            }
            else -> {
                if (!deviceConnected && sourceNodeId.isNotEmpty()) {
                    nodeId = sourceNodeId
                    deviceConnected = true
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
        if (capabilityInfo.name != PhoneApp.CHAT_CAPABILITY) return
        val remotos = capabilityInfo.nodes.filter { it.id != localNodeId }
        Log.d(TAG, "Capability reloj detectado: ${remotos.size} nodo(s)")
        if (remotos.isNotEmpty() && !deviceConnected) {
            runOnUiThread { enviarHandshake(remotos.first()) }
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
        Log.d(TAG, "Chat teléfono abierto")
        registrarListeners()
        launch(Dispatchers.IO) {
            try {
                if (localNodeId.isEmpty()) {
                    localNodeId = Tasks.await(Wearable.getNodeClient(this@ChatActivity).localNode).id
                }
                val info = Tasks.await(
                    Wearable.getCapabilityClient(this@ChatActivity)
                        .getCapability(PhoneApp.CHAT_CAPABILITY, CapabilityClient.FILTER_ALL)
                )
                runOnUiThread { onCapabilityChanged(info) }
            } catch (e: Exception) {
                Log.e(TAG, "Error capability: ${e.message}", e)
            }
        }
    }

    override fun onPause() {
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
                Uri.parse("wear://*/${PhoneApp.CHAT_CAPABILITY}"),
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
            txtEstado.text = getString(R.string.chat_desconectado)
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
        private const val TAG = "ChatPhone"
        private const val PAYLOAD_PATH = "/APP_OPEN"
        private const val CHECK_MESSAGE = "CHECK_MESSAGE"
        private const val CONNECTION_OK = "CONNECTION_OK"
        private const val READY = "READY"
    }
}
