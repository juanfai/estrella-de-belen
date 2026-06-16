package com.estrelladebelen.app

import android.app.Application
import com.estrelladebelen.app.data.repository.AppContainer
import com.estrelladebelen.app.notification.NotificationHelper
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

class EstrellaDeBelen : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        NotificationHelper.createChannel(this)
        Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(this, getString(R.string.revenuecat_api_key)).build()
        )
    }
}
