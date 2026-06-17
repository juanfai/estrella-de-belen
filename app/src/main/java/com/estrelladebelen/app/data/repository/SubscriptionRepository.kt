package com.estrelladebelen.app.data.repository

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import java.util.Date

data class SubscriptionInfo(
    val productId: String?,
    val expirationDate: Date?,
    val willRenew: Boolean
)

interface SubscriptionRepository {
    val isSubscribed: Flow<Boolean>
    suspend fun purchase(activity: Activity, productId: String): Result<Boolean>
    suspend fun restorePurchases(): Result<Boolean>
    suspend fun syncSubscriptionStatus()
    suspend fun getSubscriptionInfo(): SubscriptionInfo
}
