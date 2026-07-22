package com.envy.dualcorevpn.subscription

data class SubscriptionUpdateResult(
    val subscription: Subscription,
    val importedCount: Int,
    val unsupportedCount: Int,
    val invalidCount: Int,
    val duplicateCount: Int,
)

data class SubscriptionRefreshPlan(
    val subscriptions: List<Subscription>,
    val servers: List<ServerProfile>,
    val selectedServerId: String?,
    val result: SubscriptionUpdateResult,
)

object SubscriptionRefreshPlanner {
    fun plan(
        subscriptions: List<Subscription>,
        servers: List<ServerProfile>,
        selectedServerId: String?,
        subscription: Subscription,
        report: SubscriptionParser.ParseReport,
        updatedAt: Long,
    ): SubscriptionRefreshPlan {
        require(report.profiles.isNotEmpty()) { "В подписке не найдено поддерживаемых конфигов" }
        val updated = subscription.copy(updatedAt = updatedAt)
        val nextSubscriptions = subscriptions
            .filterNot { it.id == subscription.id } + updated
        val nextServers = servers
            .filterNot { it.subscriptionId == subscription.id } + report.profiles
        val nextSelected = when {
            selectedServerId == null -> report.profiles.first().id
            servers.any { it.id == selectedServerId && it.subscriptionId == subscription.id } &&
                report.profiles.none { it.id == selectedServerId } -> report.profiles.first().id
            else -> selectedServerId
        }
        return SubscriptionRefreshPlan(
            subscriptions = nextSubscriptions,
            servers = nextServers,
            selectedServerId = nextSelected,
            result = SubscriptionUpdateResult(
                subscription = updated,
                importedCount = report.profiles.size,
                unsupportedCount = report.unsupportedCount,
                invalidCount = report.invalidCount,
                duplicateCount = report.duplicateCount,
            ),
        )
    }
}
