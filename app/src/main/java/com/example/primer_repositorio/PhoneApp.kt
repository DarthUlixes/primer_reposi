package com.example.primer_repositorio

import android.app.Application
import com.google.android.gms.wearable.Wearable

class PhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Wearable.getCapabilityClient(this)
            .addLocalCapability(CHAT_CAPABILITY)
    }

    companion object {
        const val CHAT_CAPABILITY = "chat_capability"
    }
}
