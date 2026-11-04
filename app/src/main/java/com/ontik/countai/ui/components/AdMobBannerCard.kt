package com.ontik.countai.ui.components

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.ontik.countai.BuildConfig

/**
 * Displays a real Google AdMob Banner (`ca-app-pub-9544511312663053/2332395038`) across all screens.
 * Keeps a valid minimum 50.dp layout height during measurement so AdMob never rejects the slot
 * with "Not enough space to show ad (320x0 dp)".
 */
@Composable
fun AdMobBannerCard(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x550F172A))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                visibility = View.VISIBLE
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                // Keep AdView alive so subsequent AdMob refresh cycles can fill it
                                visibility = View.VISIBLE
                            }
                        }
                        try {
                            loadAd(AdRequest.Builder().build())
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        }
    }
}
