package com.envy.dualcorevpn.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class TrafficCounterState(
    val previousRx: Long,
    val previousTx: Long,
    val totalRx: Long = 0L,
    val totalTx: Long = 0L,
)

internal data class TunnelByteCounters(
    val txBytes: Long,
    val rxBytes: Long,
)

data class VpnTrafficSnapshot(
    val downloadBytesPerSecond: Long = 0L,
    val uploadBytesPerSecond: Long = 0L,
    val downloadedBytes: Long = 0L,
    val uploadedBytes: Long = 0L,
)

internal fun trafficDelta(previous: Long, current: Long): Long =
    if (previous < 0L || current < 0L || current < previous) 0L else current - previous

internal fun bytesPerSecond(previous: Long, current: Long, elapsedMillis: Long): Long {
    if (elapsedMillis <= 0L) return 0L
    val delta = trafficDelta(previous, current)
    if (delta == 0L) return 0L
    return runCatching { Math.multiplyExact(delta, 1_000L) / elapsedMillis }
        .getOrElse { Long.MAX_VALUE }
}

internal fun hevTunnelByteCounters(stats: LongArray): TunnelByteCounters? {
    if (stats.size < 4) return null
    val txBytes = stats[1]
    val rxBytes = stats[3]
    if (txBytes < 0L || rxBytes < 0L) return null
    return TunnelByteCounters(txBytes = txBytes, rxBytes = rxBytes)
}

internal fun advanceTrafficCounters(
    state: TrafficCounterState,
    currentRx: Long,
    currentTx: Long,
): TrafficCounterState = TrafficCounterState(
    previousRx = currentRx.coerceAtLeast(0L),
    previousTx = currentTx.coerceAtLeast(0L),
    totalRx = state.totalRx + trafficDelta(state.previousRx, currentRx),
    totalTx = state.totalTx + trafficDelta(state.previousTx, currentTx),
)

object VpnTrafficStore {
    private val mutableState = MutableStateFlow(VpnTrafficSnapshot())
    val state: StateFlow<VpnTrafficSnapshot> = mutableState.asStateFlow()

    fun update(snapshot: VpnTrafficSnapshot) {
        mutableState.value = snapshot
    }

    fun reset() {
        mutableState.value = VpnTrafficSnapshot()
    }
}
