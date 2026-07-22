package com.envy.dualcorevpn.core

import org.json.JSONObject

object XrayConfigValidator {
    fun validate(config: String): ValidationResult {
        if (config.isBlank()) return ValidationResult.Invalid("Configuration is empty")
        return try {
            val root = JSONObject(config)
            val outbounds = root.optJSONArray("outbounds")
            if (outbounds == null || outbounds.length() == 0) {
                ValidationResult.Invalid("At least one outbound is required")
            } else {
                ValidationResult.Valid
            }
        } catch (error: Exception) {
            ValidationResult.Invalid(error.message ?: "Invalid Xray JSON")
        }
    }
}
