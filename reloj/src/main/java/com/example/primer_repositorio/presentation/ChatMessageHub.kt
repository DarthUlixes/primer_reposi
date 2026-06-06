package com.example.primer_repositorio.presentation

object ChatMessageHub {
    var onMessageReceived: ((message: String, sourceNodeId: String) -> Unit)? = null
    private var pending: Pair<String, String>? = null

    fun deliver(message: String, sourceNodeId: String) {
        val callback = onMessageReceived
        if (callback != null) callback(message, sourceNodeId)
        else pending = message to sourceNodeId
    }

    fun flushPending() {
        val pair = pending ?: return
        pending = null
        onMessageReceived?.invoke(pair.first, pair.second)
    }
}
