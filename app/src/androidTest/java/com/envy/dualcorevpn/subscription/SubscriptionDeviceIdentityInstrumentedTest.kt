package com.envy.dualcorevpn.subscription

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionDeviceIdentityInstrumentedTest {
    @Test
    fun identityIsStablePerSubscriptionHostAndNotSharedAcrossHosts() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val identity = SubscriptionDeviceIdentity(context)
        val first = identity.headers("panel-a.example")
        val repeated = identity.headers("panel-a.example")
        val other = identity.headers("panel-b.example")

        assertEquals(first.hwid, repeated.hwid)
        assertNotEquals(first.hwid, other.hwid)
        assertTrue(Regex("^[a-zA-Z0-9=-]{10,64}$").matches(first.hwid))
        assertEquals("Android", first.deviceOs)
        assertTrue(first.deviceModel.isNotBlank())
    }
}
