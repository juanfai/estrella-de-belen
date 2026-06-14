package com.estrelladebelen.app.data.repository

import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    val isSubscribed: Flow<Boolean>
    suspend fun purchase(productId: String): Result<Unit>
    suspend fun restorePurchases(): Result<Boolean>
    suspend fun syncSubscriptionStatus()
}
