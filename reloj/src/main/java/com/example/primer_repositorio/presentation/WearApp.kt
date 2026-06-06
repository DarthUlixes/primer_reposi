package com.example.primer_repositorio.presentation

import android.app.Application
import com.google.android.gms.wearable.Wearable

class WearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Wearable.getCapabilityClient(this)
            .addLocalCapability(CHAT_CAPABILITY)
    }

    companion object {
        const val CHAT_CAPABILITY = "chat_capability"
    }
}
