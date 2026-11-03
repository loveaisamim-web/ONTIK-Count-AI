package com.ontik.countai.ui.theme

import com.ontik.countai.domain.model.AppLanguage

object AppStrings {
    fun statusCalibrating(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "ক্যালিব্রেট হচ্ছে" else "CALIBRATING"

    fun statusReady(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "প্রস্তুত" else "READY"

    fun statusCounting(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "গণনা চলছে" else "COUNTING"

    fun statusPaused(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "বিরতি" else "PAUSED"

    fun totalCountHeader(lang: AppLanguage, isTestMode: Boolean): String {
        return if (isTestMode) {
            if (lang == AppLanguage.BANGLA) "১০০ দানা টেস্ট মোড (TEST MODE)" else "100-GRAIN TEST MODE"
        } else {
            if (lang == AppLanguage.BANGLA) "মোট গণনা (TOTAL COUNT)" else "TOTAL COUNT"
        }
    }

    fun subStatsLine(lang: AppLanguage, inZone: Int, ratePerSec: Float): String {
        val r = String.format(java.util.Locale.US, "%.1f", ratePerSec)
        return if (lang == AppLanguage.BANGLA) {
            "বক্সে: $inZone টি  •  গতি: $r/সেকেন্ড"
        } else {
            "In zone: $inZone  •  Rate: $r/sec"
        }
    }

    fun btnStart(lang: AppLanguage) = if (lang == AppLanguage.BANGLA) "শুরু" else "Start"
    fun btnPause(lang: AppLanguage) = if (lang == AppLanguage.BANGLA) "বিরতি" else "Pause"
    fun btnReset(lang: AppLanguage) = if (lang == AppLanguage.BANGLA) "রিসেট" else "Reset"
    fun btnSave(lang: AppLanguage) = if (lang == AppLanguage.BANGLA) "সেভ" else "Save"
    fun btnAutoCalibrate(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "ক্যালিব্রেট" else "Calibrate"

    fun resetDialogTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "গণনা রিসেট করবেন?" else "Reset Current Count?"

    fun resetDialogBody(lang: AppLanguage, currentCount: Int) =
        if (lang == AppLanguage.BANGLA) {
            "বর্তমান গণনা ($currentCount) মুছে ০ হয়ে যাবে।"
        } else {
            "This will reset the current count ($currentCount) back to 0."
        }

    fun confirmYes(lang: AppLanguage) = if (lang == AppLanguage.BANGLA) "হ্যাঁ, রিসেট করুন" else "Reset"
    fun confirmCancel(lang: AppLanguage) = if (lang == AppLanguage.BANGLA) "বাতিল" else "Cancel"

    // Onboarding
    fun onboardingSlide1Title(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "১. ফোন স্থির ও সমান্তরাল রাখুন" else "1. Steady Camera Above Surface"

    fun onboardingSlide1Body(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "সাদা কাগজ বা সমান টেবিলের ওপর ফোনটি ১৫-২৫ সেমি উপরে স্থির করে ধরুন বা স্ট্যান্ডে রাখুন। ভালো আলোর জন্য ফ্ল্যাশলাইট (Torch) চালু করতে পারেন।"
        } else {
            "Hold or mount your phone 15-25 cm above a clean contrasting surface (such as white paper). Turn on the Torch for crisp, shadow-free lighting."
        }

    fun onboardingSlide2Title(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "২. খালি পৃষ্ঠে 'ক্যালিব্রেট' চাপুন" else "2. Tap 'Auto Calibrate' on Empty Surface"

    fun onboardingSlide2Body(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "কোনো শস্য বা বীজ দেওয়ার আগে খালি কাগজের ওপর 'ক্যালিব্রেট' বাটন চাপুন। ২ সেকেন্ডে অ্যাপটি ৩০টি ফ্রেম থেকে আলো ও ব্যাকগ্রাউন্ড শিখে নেবে এবং ক্যামেরা লক করবে।"
        } else {
            "With the counting zone empty, tap 'Calibrate'. In 2 seconds (30 frames), the app models the background, normalizes uneven lighting, and locks exposure/focus."
        }

    fun onboardingSlide3Title(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "৩. দানা ফেলুন ও নির্ভুল গণনা দেখুন" else "3. Drop Grains Across the Line"

    fun onboardingSlide3Body(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "এবার 'শুরু' চেপে মাঝের ড্যাশ দাগের ওপর দিয়ে চাল, ডাল, বীজ বা ট্যাবলেট পার করুন। একাধিক দানা লেগে থাকলেও অ্যাপ তা আলাদা করে গুনবে!"
        } else {
            "Tap 'Start' and pass grains across the dashed line. Touching grains are automatically split by area & watershed, and fast-falling grains are tracked via Kalman filter."
        }
}
