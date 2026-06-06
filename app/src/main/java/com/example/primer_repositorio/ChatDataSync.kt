package com.example.primer_repositorio

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object ChatDataSync {
    private const val TAG = "ChatPhone"
    const val DATA_PATH = "/chat_sync"
    private const val ITEM_ULTIMO = "$DATA_PATH/ultimo"
    private const val KEY_MSG = "msg"
    private const val KEY_TIME = "time"
    private const val KEY_FROM_NODE = "from_node"

    fun enviar(context: Context, mensaje: String, fromNode: String) {
        val time = System.currentTimeMillis()
        val request = PutDataMapRequest.create(ITEM_ULTIMO).apply {
            dataMap.putString(KEY_MSG, mensaje)
            dataMap.putLong(KEY_TIME, time)
            dataMap.putString(KEY_FROM_NODE, fromNode)
        }
        Wearable.getDataClient(context)
            .putDataItem(request.asPutDataRequest().setUrgent())
            .addOnSuccessListener { Log.d(TAG, "DataLayer enviado: $mensaje") }
            .addOnFailureListener { e -> Log.e(TAG, "DataLayer error: ${e.message}", e) }
    }

    fun cargarUltimo(
        context: Context,
        lastTime: Long,
        onNuevo: (mensaje: String, fromNode: String, time: Long) -> Unit
    ) {
        Wearable.getDataClient(context)
            .getDataItems(Uri.parse("wear://*$ITEM_ULTIMO"))
            .addOnSuccessListener { items ->
                try {
                    for (item in items) {
                        val map = DataMapItem.fromDataItem(item).dataMap
                        val time = map.getLong(KEY_TIME)
                        if (time <= lastTime) continue
                        val msg = map.getString(KEY_MSG) ?: continue
                        val fromNode = map.getString(KEY_FROM_NODE).orEmpty()
                        Log.d(TAG, "DataLayer pendiente: $msg de $fromNode")
                        onNuevo(msg, fromNode, time)
                    }
                } finally {
                    items.release()
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Error cargar DataLayer: ${e.message}", e) }
    }

    fun procesar(
        dataEvents: DataEventBuffer,
        lastTime: Long,
        onNuevo: (mensaje: String, fromNode: String, time: Long) -> Unit
    ): Long {
        var ultimo = lastTime
        try {
            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val path = event.dataItem.uri.path ?: continue
                if (!path.startsWith(DATA_PATH)) continue
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val time = map.getLong(KEY_TIME)
                if (time <= lastTime) continue
                val msg = map.getString(KEY_MSG) ?: continue
                val fromNode = map.getString(KEY_FROM_NODE).orEmpty()
                ultimo = maxOf(ultimo, time)
                Log.d(TAG, "DataLayer recibido: $msg de $fromNode")
                onNuevo(msg, fromNode, time)
            }
        } finally {
            dataEvents.close()
        }
        return ultimo
    }
}
