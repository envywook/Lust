package com.envy.dualcorevpn

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionNavigationAndroidTest {
    @Before
    fun clearSubscriptionState() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("subscriptions", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun telegramButtonIsEnabledWhenHttpsHandlerIsInstalled() {
        ActivityScenario.launch<MainActivity>(
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
        )
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val telegram = device.wait(Until.findObject(By.desc("Новости MaxSpeedVPN в Telegram")), 2_500)
            ?: device.wait(Until.findObject(By.desc("MaxSpeedVPN news on Telegram")), 2_500)
        assertNotNull(telegram)
        assertTrue(telegram!!.isEnabled)
    }

    @Test
    fun emptyHomeOffersSubscriptionManagementAndReturnsToHome() {
        ActivityScenario.launch<MainActivity>(
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
        )
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        waitForEither(device, "Главная", "Home").click()
        waitForEither(device, "Управление подписками", "Manage subscriptions").click()
        assertNotNull(waitForEither(device, "Подписки", "Subscriptions"))
        waitForEither(device, "Назад", "Back").click()
        assertNotNull(waitForEither(device, "Коснитесь для подключения", "Tap to connect"))
    }

    @Test
    fun speedManageSubscriptionsOpensDedicatedAddFlowInsteadOfLegacyHome() {
        ActivityScenario.launch<MainActivity>(
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
        )
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val speed = waitForEither(device, "Скорость", "Speed")
        speed.click()
        val manage = waitForEither(device, "Управление подписками", "Manage subscriptions")
        manage.click()

        assertNotNull(waitForEither(device, "Подписки", "Subscriptions"))
        val add = waitForEither(device, "Добавить подписку", "Add subscription")
        assertFalse(device.hasObject(By.text("Время подключения")))
        assertFalse(device.hasObject(By.text("Connection time")))

        add.click()
        assertNotNull(waitForEither(device, "Новая подписка", "New subscription"))
        assertNotNull(waitForEither(device, "URL подписки", "Subscription URL"))
    }

    private fun waitForEither(device: UiDevice, first: String, second: String) =
        device.wait(Until.findObject(By.text(first)), 2_500)
            ?: device.wait(Until.findObject(By.text(second)), 2_500)
            ?: throw AssertionError("Neither '$first' nor '$second' was displayed")
}
