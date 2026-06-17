package com.estrelladebelen.app.data.repository

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class StubSubscriptionRepository : SubscriptionRepository {

    private val _isSubscribed = MutableStateFlow(false)
    override val isSubscribed: Flow<Boolean> = _isSubscribed.asStateFlow()

    override suspend fun purchase(activity: Activity, productId: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("Not yet available"))

    override suspend fun restorePurchases(): Result<Boolean> = Result.success(false)

    override suspend fun syncSubscriptionStatus() = Unit
}
