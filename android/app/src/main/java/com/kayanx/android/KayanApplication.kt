package com.kayanx.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KayanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Engine init is deferred until a model is selected (saves startup memory).
    }
}
