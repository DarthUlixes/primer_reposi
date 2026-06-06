package com.example.primer_repositorio

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.StandardCharsets

class ChatMessageListenerService : WearableListenerService() {

    private var lastDataTime = 0L

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PAYLOAD_PATH) return
        val text = String(messageEvent.data, StandardCharsets.UTF_8)
        Log.d(TAG, "Servicio Message: $text")
        ChatMessageHub.deliver(text, messageEvent.sourceNodeId)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        lastDataTime = ChatDataSync.procesar(dataEvents, lastDataTime) { mensaje, fromNode, _ ->
            ChatMessageHub.deliver(mensaje, fromNode)
        }
    }

    companion object {
        private const val TAG = "ChatPhone"
        private const val PAYLOAD_PATH = "/APP_OPEN"
    }
}
