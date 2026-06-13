package com.estrelladebelen.app

import android.app.Application
import com.estrelladebelen.app.data.repository.AppContainer

class EstrellaDeBelen : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
