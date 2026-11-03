import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

/**
 * RevenueCat Webhook -> Firebase Cloud Function (Server-Side Source of Truth).
 *
 * Security:
 * - Verifies the `Authorization: Bearer <REVENUECAT_WEBHOOK_SECRET>` header configured in RevenueCat.
 * - Extracts `app_user_id` (which is linked to Firebase `uid`), `type`, and `expiration_at_ms`.
 * - Writes `plan`, `proExpiresAt`, and `proSource` ("play") to `users/{uid}` using Firebase Admin SDK
 *   (which bypasses client security rules that forbid client writes to these fields).
 */
export const revenueCatWebhook = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  const expectedSecret = process.env.REVENUECAT_WEBHOOK_SECRET || "";
  const authHeader = req.headers.authorization || "";
  if (expectedSecret && authHeader !== `Bearer ${expectedSecret}`) {
    res.status(401).json({ error: "Unauthorized webhook signature" });
    return;
  }

  const event = req.body?.event;
  if (!event || !event.app_user_id) {
    res.status(400).json({ error: "Missing RevenueCat event or app_user_id" });
    return;
  }

  const uid: string = event.app_user_id;
  const eventType: string = event.type || "";
  const expirationAtMs: number | null = event.expiration_at_ms || null;
  const nowMs = Date.now();

  let plan: "free" | "pro" = "free";
  let proExpiresAt: admin.firestore.Timestamp | null = null;

  const grantTypes = [
    "INITIAL_PURCHASE",
    "RENEWAL",
    "NON_RENEWING_PURCHASE",
    "UNCANCELLATION",
    "PRODUCT_CHANGE"
  ];

  if (grantTypes.includes(eventType)) {
    plan = "pro";
    // For lifetime purchases (no expiration_at_ms), set expiry 100 years ahead
    const targetMs = expirationAtMs ?? (nowMs + 100 * 365 * 24 * 3600 * 1000);
    proExpiresAt = admin.firestore.Timestamp.fromMillis(targetMs);
  } else if (eventType === "CANCELLATION" || eventType === "BILLING_ISSUE") {
    // Canceled-but-active-until-period-end or grace period
    if (expirationAtMs && expirationAtMs > nowMs) {
      plan = "pro";
      proExpiresAt = admin.firestore.Timestamp.fromMillis(expirationAtMs);
    } else {
      plan = "free";
      proExpiresAt = null;
    }
  } else if (eventType === "EXPIRATION") {
    plan = "free";
    proExpiresAt = expirationAtMs ? admin.firestore.Timestamp.fromMillis(expirationAtMs) : null;
  } else {
    res.status(200).json({ status: "ignored_event", eventType });
    return;
  }

  await db.collection("users").doc(uid).set(
    {
      plan: plan,
      proExpiresAt: proExpiresAt,
      proSource: "play",
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    },
    { merge: true }
  );

  res.status(200).json({ status: "updated", uid, plan, eventType });
});
