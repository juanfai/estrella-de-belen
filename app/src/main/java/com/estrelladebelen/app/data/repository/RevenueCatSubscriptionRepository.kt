package com.estrelladebelen.app.data.repository

import android.app.Activity
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val ENTITLEMENT_PREMIUM = "premium"

class RevenueCatSubscriptionRepository : SubscriptionRepository {

    private val _isSubscribed = MutableStateFlow(false)
    override val isSubscribed: Flow<Boolean> = _isSubscribed.asStateFlow()

    init {
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { info -> _isSubscribed.value = info.isPremium }
    }

    override suspend fun syncSubscriptionStatus() {
        runCatching {
            val info = Purchases.sharedInstance.awaitCustomerInfo()
            _isSubscribed.value = info.isPremium
        }
    }

    override suspend fun purchase(activity: Activity, productId: String): Result<Boolean> {
        return runCatching {
            val offerings = Purchases.sharedInstance.awaitOfferings()
            val pkg = offerings.current?.availablePackages
                ?.find { it.product.id == productId || it.product.id.startsWith("$productId:") }
                ?: error("Producto no encontrado: $productId")

            val result = Purchases.sharedInstance.awaitPurchase(
                PurchaseParams.Builder(activity, pkg).build()
            )
            val active = result.customerInfo.isPremium
            _isSubscribed.value = active
            active
        }.recoverUserCancellation()
    }

    override suspend fun restorePurchases(): Result<Boolean> {
        return runCatching {
            val info = Purchases.sharedInstance.awaitRestore()
            val active = info.isPremium
            _isSubscribed.value = active
            active
        }
    }

}

private val CustomerInfo.isPremium: Boolean
    get() = entitlements[ENTITLEMENT_PREMIUM]?.isActive == true

// Convierte cancelación del usuario en Result.success(false) en lugar de error
private fun Result<Boolean>.recoverUserCancellation(): Result<Boolean> = fold(
    onSuccess = { Result.success(it) },
    onFailure = { e ->
        val cancelled = (e as? PurchasesException)?.error?.code == PurchasesErrorCode.PurchaseCancelledError
        if (cancelled) Result.success(false) else Result.failure(e)
    }
)
