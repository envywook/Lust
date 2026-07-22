package com.envy.dualcorevpn.core

import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigValidatorTest {
    private val validator = XrayConfigValidator()

    @Test fun `blank config is invalid`() {
        assertTrue(validator.validate(" ") is ValidationResult.Invalid)
    }

    @Test fun `malformed json is invalid`() {
        assertTrue(validator.validate("{not-json}") is ValidationResult.Invalid)
    }

    @Test fun `json without inbounds or outbounds is invalid`() {
        assertTrue(validator.validate("{"log":{}}") is ValidationResult.Invalid)
    }

    @Test fun `minimal xray structure is valid`() {
        val config = """{"inbounds":[],"outbounds":[{"protocol":"freedom"}]}"""
        assertTrue(validator.validate(config) is ValidationResult.Valid)
    }
}
