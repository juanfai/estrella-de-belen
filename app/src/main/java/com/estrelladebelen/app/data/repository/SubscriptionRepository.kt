package com.estrelladebelen.app.data.repository

import android.app.Activity
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    val isSubscribed: Flow<Boolean>
    suspend fun purchase(activity: Activity, productId: String): Result<Unit>
    suspend fun restorePurchases(): Result<Boolean>
    suspend fun syncSubscriptionStatus()
}
