package com.envy.dualcorevpn

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

import org.json.JSONObject
import com.envy.dualcorevpn.subscription.SubscriptionRepository

@RunWith(AndroidJUnit4::class)
class MieruDeepLinkAndroidTest {
    @Test
    fun openingMieruDeepLinkShowsConfirmationAndClearsIntentData() {
        val password = "secret-do-not-display"
        val uri = Uri.parse(
            "mieru://user:$password@ru.maxspeedvpn.site:2023?v=1&transport=tcp&mtu=1400&mux=middle#MaxSpeed%20RU%20Mieru",
        )
        val browsableIntent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        assertNotNull(
            browsableIntent.resolveActivity(
                ApplicationProvider.getApplicationContext<android.content.Context>().packageManager,
            ),
        )
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val scenario = ActivityScenario.launch<MainActivity>(intent)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertNotNull(device.wait(Until.findObject(By.textContains("Mieru")), 5_000))
        assertNull(device.findObject(By.textContains(password)))
        scenario.onActivity { activity -> assertNull(activity.intent.data) }
        val confirm = device.findObject(By.text("Import")) ?: device.findObject(By.text("Импортировать"))
        assertNotNull(confirm)
        confirm!!.click()
        scenario.onActivity { activity ->
            val repository = SubscriptionRepository(activity)
            val profile = repository.servers().single { it.address == "ru.maxspeedvpn.site" && it.port == 2023 }
            val outbound = JSONObject(profile.config).getJSONObject("outbound")
            assertEquals(profile.id, repository.selectedServerId())
            assertEquals("mieru", outbound.getString("type"))
            assertEquals("MULTIPLEXING_MIDDLE", outbound.getString("multiplexing"))
        }
    }
}
