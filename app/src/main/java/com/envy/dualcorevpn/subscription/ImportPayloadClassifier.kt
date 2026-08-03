package com.envy.dualcorevpn.subscription

sealed interface ImportPayload {
    data class Subscription(val request: SubscriptionImportRequest) : ImportPayload
    data class MieruProfile(val request: MieruImportRequest) : ImportPayload
    data class Profiles(val profiles: List<ServerProfile>) : ImportPayload
}

object ImportPayloadClassifier {
    private const val MAX_LENGTH = 4_096
    private const val LOCAL_SUBSCRIPTION_ID = "local-manual-import"

    fun classify(raw: CharSequence?, requireHttpsSubscription: Boolean = false): ImportPayload {
        val value = raw?.toString()?.trim().orEmpty()
        require(value.isNotEmpty() && value.length <= MAX_LENGTH) { "Invalid import payload" }
        if (value.contains('\n') || value.contains('\r')) {
            val report = SubscriptionParser.parseReport(LOCAL_SUBSCRIPTION_ID, value)
            require(report.profiles.isNotEmpty() && report.unsupportedCount == 0 && report.invalidCount == 0) {
                "Unsupported or invalid server URI"
            }
            return ImportPayload.Profiles(report.profiles)
        }
        SubscriptionDeepLink.parse(value)?.let { request ->
            require(!requireHttpsSubscription || request.url.startsWith("https://", ignoreCase = true)) {
                "Insecure subscription URL"
            }
            return ImportPayload.Subscription(request)
        }
        if (value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)) {
            require(!requireHttpsSubscription || value.startsWith("https://", ignoreCase = true)) {
                "Insecure subscription URL"
            }
            return ImportPayload.Subscription(SubscriptionImportRequest(value))
        }
        if (value.startsWith("mieru://", ignoreCase = true) || value.startsWith("meiru://", ignoreCase = true)) {
            return ImportPayload.MieruProfile(MieruDeepLink.parse(value))
        }
        val report = SubscriptionParser.parseReport(LOCAL_SUBSCRIPTION_ID, value)
        require(report.profiles.isNotEmpty() && report.unsupportedCount == 0 && report.invalidCount == 0) {
            "Unsupported or invalid server URI"
        }
        return ImportPayload.Profiles(report.profiles)
    }
}
