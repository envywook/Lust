package com.envy.dualcorevpn.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class LogFilterTest {
    private val entries = listOf(
        LogEntry(1L, LogLevel.INFO, "VPN", "Session connected"),
        LogEntry(2L, LogLevel.ERROR, "HEV", "Native tunnel failed"),
        LogEntry(3L, LogLevel.WARN, "VPN", "Slow reconnect"),
    )

    @Test
    fun `filters independently by minimum level source and query`() {
        assertEquals(
            listOf(entries[2]),
            LogFilter.apply(entries, LogLevel.WARN, "VPN", "reconnect"),
        )
    }

    @Test
    fun `query matches source and message case insensitively`() {
        assertEquals(listOf(entries[1]), LogFilter.apply(entries, LogLevel.DEBUG, null, "hev"))
        assertEquals(listOf(entries[0]), LogFilter.apply(entries, LogLevel.DEBUG, null, "CONNECTED"))
    }

    @Test
    fun `blank source and query return all entries at minimum level`() {
        assertEquals(entries.drop(1), LogFilter.apply(entries, LogLevel.WARN, "", "  "))
    }
}
