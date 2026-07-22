package com.envy.dualcorevpn.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SingBoxEngineTest {
    @Test
    fun `validates converts starts and stops isolated runtime`() = runTest {
        val gateway = RecordingSingBoxGateway()
        val engine = SingBoxEngine(gateway)
        val xray = """{"outbounds":[{"protocol":"freedom","settings":{}}]}"""

        assertEquals(ValidationResult.Valid, engine.validate(xray))
        engine.start(xray, 0)
        engine.stop()

        assertEquals("direct", org.json.JSONObject(gateway.config).getJSONArray("outbounds").getJSONObject(0).getString("type"))
        assertEquals(1, gateway.stopCount)
    }

    private class RecordingSingBoxGateway : SingBoxGateway {
        var config = ""
        var stopCount = 0
        override suspend fun start(config: String) { this.config = config }
        override suspend fun stop() { stopCount++ }
        override fun version(): String = "test"
    }
}
