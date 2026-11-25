package com.prismtv.launcher.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Play edition: Prism Pro as a one-time in-app product ([Pro.ProductId]) through Google Play Billing.
 * Play keeps the purchase with the Google account, so it applies on every TV signed in to it, and comes
 * back after a reinstall. What Play last said is remembered ([Pro.setOwned]) for when it can't be reached.
 */
object ProStore {
    private const val TAG = "PrismPro"

    private val _price = MutableStateFlow<String?>(null)
    /** Pro's price in the shopper's own currency, once Play has said. */
    val price: StateFlow<String?> = _price

    private val _changes = MutableStateFlow(0)
    /** Bumped whenever ownership may have changed. */
    val changes: StateFlow<Int> = _changes

    private var app: Context? = null
    private var client: BillingClient? = null
    private var details: ProductDetails? = null
    private var connecting = false

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handle(purchases.orEmpty(), complete = false)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> Log.i(TAG, "Purchase ended: ${result.responseCode} ${result.debugMessage}")
        }
    }

    /** Connects to Play and checks what's owned. Safe to call more than once. */
    fun start(ctx: Context) {
        val appCtx = ctx.applicationContext
        app = appCtx
        val c = client ?: BillingClient.newBuilder(appCtx)
            .setListener(purchasesListener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
            .also { client = it }
        if (c.isReady) {
            refresh()
            return
        }
        if (connecting) return
        connecting = true
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                    loadDetails()
                } else {
                    Log.i(TAG, "Play Billing unavailable: ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
            }
        })
    }

    /** Asks Play what's owned (also how "Restore Purchase" works: Play remembers it for the account). */
    fun refresh() {
        val c = client ?: return
        if (!c.isReady) {
            app?.let { start(it) }
            return
        }
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        c.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases, complete = true)
        }
    }

    private fun loadDetails() {
        val c = client ?: return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(Pro.ProductId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        c.queryProductDetailsAsync(params) { result, found ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val d = productList(found).firstOrNull { it.productId == Pro.ProductId } ?: return@queryProductDetailsAsync
            details = d
            @Suppress("DEPRECATION")
            _price.value = d.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    /**
     * Billing Library 8 hands back a QueryProductDetailsResult, older ones a plain list: read either, so
     * a library update doesn't silently stop the price showing.
     */
    @Suppress("UNCHECKED_CAST")
    private fun productList(found: Any?): List<ProductDetails> = when (found) {
        null -> emptyList()
        is List<*> -> found.filterIsInstance<ProductDetails>()
        else -> runCatching {
            found.javaClass.getMethod("getProductDetailsList").invoke(found) as? List<ProductDetails>
        }.getOrNull().orEmpty()
    }

    /** Opens Play's purchase sheet. False when Play isn't ready yet (no connection, or no product found). */
    fun buy(activity: Activity): Boolean {
        val c = client
        val d = details
        if (c == null || !c.isReady || d == null) {
            start(activity)
            return false
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        val r = c.launchBillingFlow(activity, params)
        return r.responseCode == BillingClient.BillingResponseCode.OK
    }

    /**
     * [complete]: the full list of what's owned (a refund shows as Pro missing from it). A purchase that
     * just finished is acknowledged, or Play refunds it after three days.
     */
    private fun handle(purchases: List<Purchase>, complete: Boolean) {
        val ctx = app ?: return
        val pro = purchases.filter { Pro.ProductId in it.products }
        val bought = pro.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        pro.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }.forEach { p ->
            client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r ->
                if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "Acknowledge failed: ${r.responseCode}")
            }
        }
        if (bought) Pro.setOwned(ctx, true) else if (complete) Pro.setOwned(ctx, false)
        _changes.value++
    }
}
