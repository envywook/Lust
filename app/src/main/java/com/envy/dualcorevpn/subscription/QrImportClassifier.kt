package com.envy.dualcorevpn.subscription

sealed interface QrImportPayload {
    data class Subscription(val request: SubscriptionImportRequest) : QrImportPayload
    data class MieruProfile(val request: MieruImportRequest) : QrImportPayload
    data class Profile(val profile: ServerProfile) : QrImportPayload
}

object QrImportClassifier {
    private const val MAX_LENGTH = 4_096

    fun classify(raw: String?): QrImportPayload {
        return when (val payload = ImportPayloadClassifier.classify(raw, requireHttpsSubscription = true)) {
            is ImportPayload.Subscription -> QrImportPayload.Subscription(payload.request)
            is ImportPayload.MieruProfile -> QrImportPayload.MieruProfile(payload.request)
            is ImportPayload.Profiles -> {
                require(payload.profiles.size == 1) { "QR payload must contain exactly one supported profile" }
                QrImportPayload.Profile(payload.profiles.single())
            }
        }
    }
}
