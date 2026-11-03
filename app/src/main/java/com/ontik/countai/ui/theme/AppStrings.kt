package com.ontik.countai.ui.theme

import com.ontik.countai.domain.model.AppLanguage

object AppStrings {
    fun statusReady(lang: AppLanguage) = if (lang == AppLanguage.BANGLA) "প্রস্তুত" else "READY"
    fun statusLive(lang: AppLanguage) = if (lang == AppLanguage.BANGLA) "চলছে" else "LIVE"
    fun statusPaused(lang: AppLanguage) = if (lang == AppLanguage.BANGLA) "বিরতি" else "PAUSED"

    fun totalCountLabel(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "মোট গণনা (TOTAL COUNT)" else "TOTAL COUNT"

    fun inZoneSummary(lang: AppLanguage, inZone: Int, manualDelta: Int): String {
        val adj = if (manualDelta >= 0) "+$manualDelta" else "$manualDelta"
        return if (lang == AppLanguage.BANGLA) {
            "বক্সের ভেতর: $inZone টি  |  ম্যানুয়াল: $adj"
        } else {
            "In Zone: $inZone  |  Manual: $adj"
        }
    }

    fun startCounting(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "গণনা শুরু" else "Start"

    fun pauseCounting(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "বিরতি" else "Pause"

    fun resumeCounting(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "চালু করুন" else "Resume"

    fun resetCounting(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "রিসেট" else "Reset"

    fun photoModeBtn(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "ছবি গণনা" else "Photo"

    fun dragZoneHint(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "বক্সের কোণা বা মাঝের দাগ টেনে ছোট-বড় করুন"
        } else {
            "Drag zone corners or dashed line to adjust"
        }

    fun liveTelemetry(lang: AppLanguage, inZone: Int, ms: Long) =
        if (lang == AppLanguage.BANGLA) {
            "শনাক্ত: $inZone টি • সময়: $ms ms"
        } else {
            "Detected: $inZone • $ms ms"
        }

    fun sessionSavedToast(lang: AppLanguage, count: Int) =
        if (lang == AppLanguage.BANGLA) {
            "সেশন সেভ হয়েছে (মোট গণনা: $count)"
        } else {
            "Session saved to History (Total: $count)"
        }

    fun cameraPermissionTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "ক্যামেরা পারমিশন প্রয়োজন" else "Camera Permission Required"

    fun cameraPermissionDesc(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "ক্যামেরার সামনে বস্তু রেখে স্বয়ংক্রিয়ভাবে গণনা করার জন্য ক্যামেরা পারমিশন অ্যালাউ করুন।"
        } else {
            "ONTIK Count AI needs camera access to detect and count physical objects in real time."
        }

    fun grantPermissionBtn(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "ক্যামেরা চালু করুন" else "Grant Camera Permission"

    // Photo Screen
    fun photoScreenTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "ছবি থেকে বস্তু গণনা" else "Photo Counting"

    fun photoScreenSubtitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "ছবি তুলুন বা গ্যালারি থেকে নিন, এবং ট্যাপ করে সংখ্যা ঠিক করুন"
        } else {
            "Capture or pick a photo, inspect boxes & tap to adjust"
        }

    fun photoEmptyTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "ছবি তুলুন অথবা গ্যালারি থেকে সিলেক্ট করুন" else "Select or Capture a Photo"

    fun photoEmptyDesc(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "সাদা কাগজ বা টেবিলের ওপর বাদাম, বীজ বা শস্যদানা রেখে ছবি তুলুন। কোনো বস্তু বাদ পড়লে ছবির ওপর ট্যাপ করে যোগ করতে পারবেন।"
        } else {
            "Place seeds, grains, beans, or objects on a contrasting surface and capture or pick a photo. Tap any missed item to add it."
        }

    fun photoTotalLabel(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "ছবির মোট গণনা (PHOTO COUNT)" else "PHOTO TOTAL COUNT"

    fun photoBreakdown(lang: AppLanguage, aiCount: Int, tapCount: Int) =
        if (lang == AppLanguage.BANGLA) {
            "অটো শনাক্ত: $aiCount টি  |  ট্যাপ মার্ক: +$tapCount"
        } else {
            "Auto Boxes: $aiCount  |  Tap Marks: +$tapCount"
        }

    fun capturePhotoBtn(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "ছবি তুলুন" else "Take Photo"

    fun pickGalleryBtn(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "গ্যালারি" else "Gallery"

    // History Screen
    fun historyTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "গণনার ইতিহাস (History)" else "Counting History"

    fun historySummary(lang: AppLanguage, sessions: Int, totalItems: Int) =
        if (lang == AppLanguage.BANGLA) {
            "মোট সেশন: $sessions টি • সর্বমোট বস্তু: $totalItems টি"
        } else {
            "$sessions saved sessions • Total objects: $totalItems"
        }

    fun exportCsvBtn(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "CSV এক্সপোর্ট" else "Export CSV"

    fun exportTxtBtn(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "TXT রিপোর্ট" else "Export TXT"

    fun emptyHistoryTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "এখনো কোনো সেশন সেভ করা হয়নি" else "No Saved Counting Sessions Yet"

    fun emptyHistoryDesc(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "লাইভ গণনা বা ছবি গণনা স্ক্রিনের সবুজ সেভ (বুকমার্ক) বাটনে ট্যাপ করলে এখানে বিস্তারিত জমা থাকবে।"
        } else {
            "Tap the green save button on the Live Counting or Photo Counting screen to save a session."
        }

    // Settings Screen
    fun settingsTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "সেটিংস ও এআই কনফিগারেশন" else "Settings & AI Configuration"

    fun settingsSubtitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "ভাষা, ডিটেকশন সেন্সিটিভিটি, লাইন পারাপারের দিক ও মডেল পরিবর্তন করুন"
        } else {
            "Configure language, detection sensitivity, line direction & AI model"
        }

    fun languageSectionTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "অ্যাপের ভাষা (App Language)" else "App Language / অ্যাপের ভাষা"

    fun modelSectionTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "অবজেক্ট ডিটেকশন ইঞ্জিন ও AI মডেল"
        } else {
            "Object Detection Engine & AI Model"
        }

    fun importModelBtn(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "ফোন মেমোরি থেকে .tflite মডেল ফাইল যোগ করুন"
        } else {
            "Import Custom .tflite Model from Phone Storage"
        }

    fun sensitivitySectionTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "ডিটেকশন সেন্সিটিভিটি (Detection Sensitivity)"
        } else {
            "Detection Sensitivity & Overlap Control"
        }

    fun confidenceLabel(lang: AppLanguage, percent: Int) =
        if (lang == AppLanguage.BANGLA) {
            "ডিটেকশন থ্রেশহোল্ড (Sensitivity): $percent% (কম রাখলে ছোট বস্তু সহজে ধরবে)"
        } else {
            "Detection Threshold: $percent% (Lower detects smaller/fainter objects)"
        }

    fun iouLabel(lang: AppLanguage, percent: Int) =
        if (lang == AppLanguage.BANGLA) {
            "ওভারল্যাপ ফিল্টার (NMS IoU): $percent%"
        } else {
            "NMS Overlap IoU Threshold: $percent%"
        }

    fun resetSensitivityBtn(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "ডিফল্ট সেন্সিটিভিটি (35%) ফিরিয়ে আনুন"
        } else {
            "Reset Sensitivity to Recommended Default (35%)"
        }

    fun directionSectionTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "বস্তু গণনার দিক (Counting Line Direction)"
        } else {
            "Counting Line Crossing Direction"
        }

    fun cameraSectionTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) {
            "ক্যামেরা ও ভাইব্রেশন অপশন"
        } else {
            "Camera & Hardware Options"
        }

    fun rearCameraTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "পিছনের ক্যামেরা ব্যবহার করুন (Rear Camera)" else "Use Rear Camera"

    fun rearCameraSub(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "বন্ধ করলে সামনের ক্যামেরা চালু হবে" else "Switch off to use front camera"

    fun hapticTitle(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "গণনার সময় ভাইব্রেশন (Haptic Tick)" else "Haptic Tick on Line Crossing"

    fun hapticSub(lang: AppLanguage) =
        if (lang == AppLanguage.BANGLA) "বস্তু দাগ পার হলে হালকা ভাইব্রেট করবে" else "Subtle vibration feedback when an object is counted"
}
