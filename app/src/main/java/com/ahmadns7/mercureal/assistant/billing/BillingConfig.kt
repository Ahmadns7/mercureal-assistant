package com.ahmadns7.mercureal.assistant.billing

/**
 * Google Play Billing settings.
 *
 * PRODUCT_ID: create a ONE-TIME product (managed product, not subscription) in Play Console >
 * Monetize > Products > In-app products, with product id exactly "mercureal_pro", price of your
 * choice, status Active. Paste a different id here only if you choose one in Play Console.
 *
 * Until that product exists on Play (and the app is installed from a Play track, or via a license
 * tester), the store reports "product not found". The app then stays free-tier and the upsell screen
 * says so. Debug builds also have a local "test Pro" switch in Settings so premium features can be
 * tried without Play.
 */
object BillingConfig {
    const val PRODUCT_ID = "mercureal_pro"
}
