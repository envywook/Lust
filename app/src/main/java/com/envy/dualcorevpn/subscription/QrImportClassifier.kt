package com.envy.dualcorevpn.subscription

sealed interface QrImportPayload {
    data class Subscription(val request: SubscriptionImportRequest) : QrImportPayload
    data class MieruProfile(val request: MieruImportRequest) : QrImportPayload
    data class Profile(val profile: ServerProfile) : QrImportPayload
}

object QrImportClassifier {
    private const val MAX_LENGTH = 4_096

    fun classify(raw: String?): QrImportPayload {
        val value = raw?.trim().orEmpty()
        require(value.isNotEmpty() && value.length <= MAX_LENGTH && !value.contains('\n') && !value.contains('\r')) {
            "Invalid QR payload"
        }
        if (value.startsWith("https://")) {
            return QrImportPayload.Subscription(SubscriptionImportRequest(value))
        }
        if (value.startsWith("lust://", ignoreCase = true)) {
            val request = SubscriptionDeepLink.parse(value) ?: throw IllegalArgumentException("Invalid subscription link")
            require(request.url.startsWith("https://")) { "Insecure subscription URL" }
            return QrImportPayload.Subscription(request)
        }
        if (value.startsWith("mieru://", ignoreCase = true)) {
            return QrImportPayload.MieruProfile(MieruDeepLink.parse(value))
        }
        val report = SubscriptionParser.parseReport("qr-import", value)
        require(report.profiles.size == 1 && report.unsupportedCount == 0 && report.invalidCount == 0) {
            "QR payload must contain exactly one supported profile"
        }
        return QrImportPayload.Profile(report.profiles.single())
    }
}
