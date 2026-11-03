package com.ontik.countai.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ontik.countai.BuildConfig
import com.ontik.countai.domain.model.PlanTier
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class PurchaseUiState {
    data object Idle : PurchaseUiState()
    data object Loading : PurchaseUiState()
    data class Success(val messageEn: String, val messageBn: String) : PurchaseUiState()
    data object Cancelled : PurchaseUiState()
    data object PendingPayment : PurchaseUiState()
    data class Error(val messageEn: String, val messageBn: String) : PurchaseUiState()
}

data class StoreProductPricing(
    val monthlyPackage: Package? = null,
    val yearlyPackage: Package? = null,
    val lifetimePackage: Package? = null,
    val monthlyPriceFormatted: String = "$2.99 / month",
    val yearlyPriceFormatted: String = "$19.99 / year",
    val lifetimePriceFormatted: String = "$49.99",
    val hasFreeTrialOnYearly: Boolean = true
)

/**
 * Integrates RevenueCat Android SDK (`purchases-android`) with Google Play Billing.
 *
 * Entitlement ID: `"pro"`
 * Products: `"pro_monthly"`, `"pro_yearly"` (with 7-day trial), `"pro_lifetime"`.
 */
@Singleton
class BillingRepository @Inject constructor(
    private val context: Context,
    private val planManager: PlanManager
) {
    companion object {
        const val ENTITLEMENT_PRO = "pro"
    }

    private val _pricingState = MutableStateFlow(StoreProductPricing())
    val pricingState: StateFlow<StoreProductPricing> = _pricingState.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseUiState>(PurchaseUiState.Idle)
    val purchaseState: StateFlow<PurchaseUiState> = _purchaseState.asStateFlow()

    private var isConfigured = false

    init {
        configureRevenueCatIfNeeded()
    }

    private fun configureRevenueCatIfNeeded() {
        val apiKey = BuildConfig.REVENUECAT_API_KEY
        if (apiKey.isBlank()) return
        try {
            if (!Purchases.isConfigured) {
                Purchases.configure(
                    PurchasesConfiguration.Builder(context, apiKey).build()
                )
            }
            isConfigured = true
            fetchOfferings()
            refreshCustomerInfo()
        } catch (_: Throwable) {
            isConfigured = false
        }
    }
    fun linkFirebaseUser(uid: String) {
        if (!isConfigured || uid.isBlank()) return
        try {
            Purchases.sharedInstance.logIn(
                uid,
                object : LogInCallback {
                    override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                        applyCustomerInfo(customerInfo)
                    }
                    override fun onError(error: PurchasesError) {
                    }
                }
            )
        } catch (_: Throwable) {
        }
    }

    fun logOutRevenueCat() {
        if (!isConfigured) return
        try {
            Purchases.sharedInstance.logOut()
        } catch (_: Throwable) {
        }
    }

    fun fetchOfferings() {
        if (!isConfigured) return
        try {
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: Offerings) {
                    val current = offerings.current ?: return
                    val monthly = current.monthly ?: current.availablePackages.firstOrNull {
                        it.product.id.contains("monthly", ignoreCase = true)
                    }
                    val yearly = current.annual ?: current.availablePackages.firstOrNull {
                        it.product.id.contains("yearly", ignoreCase = true) || it.product.id.contains("annual", ignoreCase = true)
                    }
                    val lifetime = current.lifetime ?: current.availablePackages.firstOrNull {
                        it.product.id.contains("lifetime", ignoreCase = true)
                    }

                    _pricingState.value = StoreProductPricing(
                        monthlyPackage = monthly,
                        yearlyPackage = yearly,
                        lifetimePackage = lifetime,
                        monthlyPriceFormatted = monthly?.product?.price?.formatted ?: "$2.99 / month",
                        yearlyPriceFormatted = yearly?.product?.price?.formatted ?: "$19.99 / year",
                        lifetimePriceFormatted = lifetime?.product?.price?.formatted ?: "$49.99"
                    )
                }

                override fun onError(error: PurchasesError) {
                    // Keep fallback store texts from Remote Config
                }
            })
        } catch (_: Throwable) {
        }
    }

    fun refreshCustomerInfo() {
        if (!isConfigured) return
        try {
            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    applyCustomerInfo(customerInfo)
                }

                override fun onError(error: PurchasesError) {
                }
            })
        } catch (_: Throwable) {
        }
    }

    fun purchasePackage(activity: Activity, pkg: Package?) {
        if (!isConfigured || pkg == null) {
            _purchaseState.value = PurchaseUiState.Error(
                "Google Play Billing is not configured yet (Add REVENUECAT_ANDROID_API_KEY in gradle.properties).",
                "গুগল প্লে বিলিং এখনো কনফিগার করা হয়নি (REVENUECAT_ANDROID_API_KEY যোগ করুন)।"
            )
            return
        }

        _purchaseState.value = PurchaseUiState.Loading
        try {
            Purchases.sharedInstance.purchase(
                PurchaseParams.Builder(activity, pkg).build(),
                object : PurchaseCallback {
                    override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                        applyCustomerInfo(customerInfo)
                        _purchaseState.value = PurchaseUiState.Success(
                            "Welcome to ONTIK Count AI PRO! All features unlocked.",
                            "ONTIK Count AI PRO-তে স্বাগতম! সব প্রো ফিচার আনলক হয়েছে।"
                        )
                    }

                    override fun onError(error: PurchasesError, userCancelled: Boolean) {
                        if (userCancelled || error.code == PurchasesErrorCode.PurchaseCancelledError) {
                            _purchaseState.value = PurchaseUiState.Cancelled
                        } else if (error.code == PurchasesErrorCode.PaymentPendingError) {
                            _purchaseState.value = PurchaseUiState.PendingPayment
                        } else if (error.code == PurchasesErrorCode.ProductAlreadyPurchasedError) {
                            refreshCustomerInfo()
                            _purchaseState.value = PurchaseUiState.Success(
                                "You already own ONTIK PRO! Restored automatically.",
                                "আপনার প্রো সাবস্ক্রিপশন আগে থেকেই চালু আছে!"
                            )
                        } else {
                            _purchaseState.value = PurchaseUiState.Error(
                                error.message,
                                "পেমেন্ট সম্পন্ন করা যায়নি: ${error.message}"
                            )
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            _purchaseState.value = PurchaseUiState.Error(
                e.localizedMessage ?: "Billing unavailable",
                "গুগল প্লে বিলিং এই মুহূর্তে পাওয়া যাচ্ছে না"
            )
        }
    }

    fun restorePurchases() {
        if (!isConfigured) {
            _purchaseState.value = PurchaseUiState.Error(
                "Connect to Google Play to restore purchases.",
                "পারচেজ রিস্টোর করতে গুগল প্লে-তে যুক্ত থাকুন।"
            )
            return
        }
        _purchaseState.value = PurchaseUiState.Loading
        try {
            Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    applyCustomerInfo(customerInfo)
                    val isPro = customerInfo.entitlements[ENTITLEMENT_PRO]?.isActive == true
                    if (isPro) {
                        _purchaseState.value = PurchaseUiState.Success(
                            "PRO subscription restored!",
                            "আপনার PRO সাবস্ক্রিপশন সফলভাবে রিস্টোর হয়েছে!"
                        )
                    } else {
                        _purchaseState.value = PurchaseUiState.Error(
                            "No active PRO subscription found for this account.",
                            "এই অ্যাকাউন্টে কোনো সচল PRO সাবস্ক্রিপশন পাওয়া যায়নি।"
                        )
                    }
                }

                override fun onError(error: PurchasesError) {
                    _purchaseState.value = PurchaseUiState.Error(
                        error.message,
                        "রিস্টোর ব্যর্থ হয়েছে: ${error.message}"
                    )
                }
            })
        } catch (_: Throwable) {
            _purchaseState.value = PurchaseUiState.Idle
        }
    }

    fun resetPurchaseBannerState() {
        _purchaseState.value = PurchaseUiState.Idle
    }

    fun openPlayStoreSubscriptionsPage(context: Context) {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    private fun applyCustomerInfo(customerInfo: CustomerInfo) {
        val entitlement = customerInfo.entitlements[ENTITLEMENT_PRO]
        if (entitlement != null && entitlement.isActive) {
            val expMs = entitlement.expirationDate?.time
            planManager.updateVerifiedPlanFromServer(PlanTier.PRO, expMs)
        }
    }
}
