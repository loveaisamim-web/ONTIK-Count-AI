import re

# 1. Fix BillingRepository.kt
billing_path = "app/src/main/java/com/ontik/countai/billing/BillingRepository.kt"
with open(billing_path, "r", encoding="utf-8") as f:
    b = f.read()

if "import com.revenuecat.purchases.interfaces.LogInCallback" not in b:
    b = b.replace(
        "import com.revenuecat.purchases.interfaces.PurchaseCallback",
        "import com.revenuecat.purchases.interfaces.LogInCallback\nimport com.revenuecat.purchases.interfaces.PurchaseCallback"
    )

target_replacement = """    fun linkFirebaseUser(uid: String) {
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
    }"""

pattern = r"\s*fun linkFirebaseUser\(uid: String\) \{.*?\n    \}"
b = re.sub(pattern, "\n" + target_replacement, b, flags=re.DOTALL)

with open(billing_path, "w", encoding="utf-8") as f:
    f.write(b)
print("Fixed BillingRepository.kt")

# 2. Fix SyncWorker.kt
sync_path = "app/src/main/java/com/ontik/countai/data/remote/SyncWorker.kt"
with open(sync_path, "r", encoding="utf-8") as f:
    s = f.read()
s = s.replace(".doc(uid)", ".document(uid)")
with open(sync_path, "w", encoding="utf-8") as f:
    f.write(s)
print("Fixed SyncWorker.kt")

# 3. Fix AuthRepository.kt
auth_path = "app/src/main/java/com/ontik/countai/auth/AuthRepository.kt"
with open(auth_path, "r", encoding="utf-8") as f:
    a = f.read()
a = a.replace(".doc(", ".document(")
with open(auth_path, "w", encoding="utf-8") as f:
    f.write(a)
print("Fixed AuthRepository.kt")
