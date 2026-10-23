package com.ahmadns7.mercureal.assistant.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import com.ahmadns7.mercureal.assistant.BuildConfig
import com.ahmadns7.mercureal.assistant.billing.BillingConfig
import com.ahmadns7.mercureal.assistant.billing.BillingManager
import com.ahmadns7.mercureal.assistant.model.FreeTier
import com.ahmadns7.mercureal.assistant.model.ModelRepository
import kotlinx.coroutines.launch

/** Small "PRO" or "free: N left" line for the home screen. */
@Composable
fun PlanBadge(repo: ModelRepository) {
    val isPro by repo.entitlements.isPro.collectAsState(initial = false)
    val used by repo.entitlements.usedToday.collectAsState(initial = 0)
    Text(
        if (isPro) "MercuReal Pro" else "free: ${(FreeTier.DAILY_CONVERSATIONS - used).coerceAtLeast(0)} of ${FreeTier.DAILY_CONVERSATIONS} conversations left today",
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Upsell and purchase screen. Shows remaining free conversations and the one-time Pro unlock. */
@Composable
fun ProScreen(repo: ModelRepository, billing: BillingManager, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isPro by repo.entitlements.isPro.collectAsState(initial = false)
    val used by repo.entitlements.usedToday.collectAsState(initial = 0)
    val product by billing.product.collectAsState()
    val status by billing.status.collectAsState()
    LaunchedEffect(Unit) { billing.connect() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("< back") }
        Text("MercuReal Pro", style = MaterialTheme.typography.headlineMedium)
        if (isPro) {
            Text("you have Pro. unlimited conversations and the Pro badge are on.")
        } else {
            val left = (FreeTier.DAILY_CONVERSATIONS - used).coerceAtLeast(0)
            Text("free tier: $left of ${FreeTier.DAILY_CONVERSATIONS} conversations left today. the count resets at midnight.")
            Text(
                "Pro is a one-time purchase: no subscription, no account. it removes the daily limit and unlocks the Pro badge. speech, prompts and history still stay on this phone.",
                style = MaterialTheme.typography.bodySmall,
            )
            val price = product?.oneTimePurchaseOfferDetails?.formattedPrice
            if (product != null) {
                Button(onClick = {
                    val activity = context as? Activity
                    if (activity != null) billing.launchPurchase(activity)
                }) { Text("unlock Pro${price?.let { " for $it" } ?: ""}") }
            } else {
                Text(
                    status.ifBlank { "loading the Pro product from google play" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = { billing.refresh() }) { Text("restore purchase") }
        }
        if (BuildConfig.DEBUG) {
            Text("debug build only", style = MaterialTheme.typography.bodySmall)
            val debugOn by repo.entitlements.isPro.collectAsState(initial = false)
            OutlinedButton(onClick = { scope.launch { repo.entitlements.setDebugPro(!debugOn) } }) {
                Text(if (debugOn) "turn off test Pro" else "test Pro (local, debug only)")
            }
        }
    }
}
