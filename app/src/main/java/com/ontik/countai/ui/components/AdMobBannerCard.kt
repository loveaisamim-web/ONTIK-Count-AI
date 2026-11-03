package com.ontik.countai.ui.components

import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.ontik.countai.BuildConfig

/**
 * Displays a Google AdMob Banner ONLY for FREE plan users on History & Settings screens.
 * - Uses BuildConfig.ADMOB_BANNER_UNIT_ID:
 *   - Debug builds: Google Test Banner ID (ca-app-pub-3940256099942544/9214589741)
 *   - Release builds: Real Banner ID (ca-app-pub-9544511312663053/2332395038)
 * - Never rendered on the Live Camera / Counting screen.
 * - Handles load failure silently: collapses completely with zero empty gap and no crash when offline.
 */
@Composable
fun AdMobBannerCard(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    var isAdLoaded by remember { mutableStateOf(false) }
    var hasAdFailed by remember { mutableStateOf(false) }

    if (!isVisible || hasAdFailed) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isAdLoaded) Modifier.padding(vertical = 4.dp)
                else Modifier.heightIn(max = 0.dp).alpha(0f)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            isAdLoaded = true
                            hasAdFailed = false
                            visibility = View.VISIBLE
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            isAdLoaded = false
                            hasAdFailed = true
                            visibility = View.GONE
                        }
                    }
                    try {
                        loadAd(AdRequest.Builder().build())
                    } catch (_: Throwable) {
                        isAdLoaded = false
                        hasAdFailed = true
                        visibility = View.GONE
                    }
                }
            }
        )
    }
}
