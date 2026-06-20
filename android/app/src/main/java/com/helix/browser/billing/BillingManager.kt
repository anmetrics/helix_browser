package com.helix.browser.billing

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        const val PRODUCT_ID = "helix_premium_monthly"
        private const val PREFS_NAME = "helix_billing"
        private const val KEY_IS_PREMIUM = "is_premium"

        // RSA algorithm + signing algorithm used by Google Play for purchase
        // signatures. Do not change these unless Play changes its scheme.
        private const val KEY_FACTORY_ALGORITHM = "RSA"
        private const val SIGNATURE_ALGORITHM = "SHA1withRSA"

        // Base64-encoded RSA public key from the Play Console (Monetization
        // setup -> Licensing). This is only a local first line of defense:
        // a compromised client could still patch the APK, so high-value
        // entitlements should additionally be verified by a trusted backend.
        // The verifier fails closed when this is left blank.
        private const val BASE64_PUBLIC_KEY = ""

        // getInstance() always promotes the argument to applicationContext
        // below, so the singleton can never hold an Activity reference.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(context: Context): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // The cached pref is only a non-authoritative hint so the UI is not blank
    // on a cold start; the real entitlement is re-derived from verified Play
    // purchases via checkExistingPurchases() every time we connect.
    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private var productDetails: ProductDetails? = null

    init {
        connectToPlayStore()
    }

    private fun connectToPlayStore() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    querySubscription()
                    checkExistingPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection after delay
                scope.launch {
                    delay(3000)
                    connectToPlayStore()
                }
            }
        })
    }

    private fun querySubscription() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = productDetailsList.firstOrNull()
            }
        }
    }

    private fun checkExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                // Re-derive entitlement from verified Play purchases only; the
                // signed payload is the source of truth, not the cached pref.
                val verified = purchases.filter { isPurchaseValid(it) }

                val hasActive = verified.any { purchase ->
                    purchase.products.contains(PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                setPremium(hasActive)

                // Acknowledge unacknowledged purchases (verified only)
                verified.filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged
                }.forEach { purchase ->
                    acknowledgePurchase(purchase)
                }
            }
        }
    }

    fun launchSubscription(activity: Activity) {
        val details = productDetails ?: return

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    // Never grant entitlement on an unverified payload; a forged
                    // Purchase could otherwise unlock premium for free.
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        isPurchaseValid(purchase)
                    ) {
                        setPremium(true)
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // User cancelled, do nothing
            }
            else -> {
                // Other error
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { /* ignore */ }
    }

    private fun setPremium(premium: Boolean) {
        _isPremium.value = premium
        prefs.edit().putBoolean(KEY_IS_PREMIUM, premium).apply()
    }

    /**
     * Returns true only when the purchase's signed payload validates against the
     * Play public key. Fails closed: if verification cannot be performed (no key
     * configured, missing payload, or any error), the purchase is rejected so a
     * forged or tampered Purchase can never grant entitlement.
     */
    private fun isPurchaseValid(purchase: Purchase): Boolean {
        return verifyPurchase(purchase.originalJson, purchase.signature)
    }

    private fun verifyPurchase(signedData: String?, signature: String?): Boolean {
        if (TextUtils.isEmpty(BASE64_PUBLIC_KEY) ||
            TextUtils.isEmpty(signedData) ||
            TextUtils.isEmpty(signature)
        ) {
            Log.w(TAG, "Purchase verification skipped: missing key, payload, or signature")
            return false
        }
        return try {
            val key = generatePublicKey(BASE64_PUBLIC_KEY)
            val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
            sig.initVerify(key)
            sig.update(signedData!!.toByteArray(Charsets.UTF_8))
            val signatureBytes = Base64.decode(signature, Base64.DEFAULT)
            if (!sig.verify(signatureBytes)) {
                Log.w(TAG, "Purchase signature verification failed")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            // IllegalArgumentException (bad base64), GeneralSecurityException, etc.
            Log.e(TAG, "Purchase verification error", e)
            false
        }
    }

    private fun generatePublicKey(encodedPublicKey: String): PublicKey {
        val decodedKey = Base64.decode(encodedPublicKey, Base64.DEFAULT)
        val keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
        return keyFactory.generatePublic(X509EncodedKeySpec(decodedKey))
    }

    fun getFormattedPrice(): String {
        return productDetails?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            ?: "$2.99/month"
    }

    fun destroy() {
        billingClient.endConnection()
        scope.cancel()
    }
}
