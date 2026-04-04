package com.gooseco.myliftsquad

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

class BillingManager(
    private val activity: Activity,
    private val onDonated: () -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "support_developer"
        private const val PREFS_NAME = "myliftsquad_prefs"
        private const val PREF_DONATED = "donated"

        // Set to true to simulate a completed donation for UI testing
        const val DEBUG_FORCE_DONATED = false

        fun isDonated(context: Context): Boolean {
            if (DEBUG_FORCE_DONATED) return true
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_DONATED, false)
        }
    }

    private val billingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryExistingPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    fun launchPurchaseFlow() {
        if (!billingClient.isReady) {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryAndLaunch()
                    }
                }
                override fun onBillingServiceDisconnected() {}
            })
        } else {
            queryAndLaunch()
        }
    }

    private fun queryAndLaunch() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ))
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK &&
                productDetailsList.isNotEmpty()) {
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetailsList[0])
                            .build()
                    ))
                    .build()
                activity.runOnUiThread {
                    billingClient.launchBillingFlow(activity, flowParams)
                }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    private fun queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.filter {
                    it.products.contains(PRODUCT_ID) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }.forEach { setDonated() }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRODUCT_ID) ||
            purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        if (!purchase.isAcknowledged) {
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    setDonated()
                }
            }
        } else {
            setDonated()
        }
    }

    private fun setDonated() {
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_DONATED, true)
            .apply()
        activity.runOnUiThread { onDonated() }
    }

    fun disconnect() {
        billingClient.endConnection()
    }
}
