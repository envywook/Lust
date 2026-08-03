package com.envy.dualcorevpn.core

import org.json.JSONObject

object XrayConfigValidator {
    fun validate(config: String): ValidationResult {
        if (config.isBlank()) return ValidationResult.Invalid("Configuration is empty")
        return try {
            val root = JSONObject(config)
            if (root.optString("maxspeedvpn_format") == "sing-box" || root.optString("lust_format") == "sing-box") {
                return ValidationResult.Invalid("Этот профиль поддерживается только ядром sing-box")
            }
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
