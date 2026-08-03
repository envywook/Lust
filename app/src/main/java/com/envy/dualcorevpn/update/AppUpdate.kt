package com.envy.dualcorevpn.update

import org.json.JSONArray
import java.net.URI

data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int = compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)
    override fun toString(): String = "$major.$minor.$patch-alpha"
    companion object {
        private val pattern = Regex("^v(\\d+)\\.(\\d+)\\.(\\d+)-alpha$")
        fun parse(value: String): ReleaseVersion? = pattern.matchEntire(value)?.destructured?.let { (a, b, c) ->
            runCatching { ReleaseVersion(a.toInt(), b.toInt(), c.toInt()) }.getOrNull()
        }
    }
}

data class ReleaseAsset(val name: String, val downloadUrl: String, val size: Long)
data class AppUpdate(val tag: String, val version: ReleaseVersion, val pageUrl: String, val notes: String, val apk: ReleaseAsset, val checksums: ReleaseAsset)

object UpdateCatalog {
    fun select(releases: JSONArray, current: ReleaseVersion, abis: List<String>): AppUpdate? =
        (0 until releases.length()).mapNotNull { index ->
            val release = releases.getJSONObject(index)
            if (release.optBoolean("draft") || !release.optBoolean("prerelease")) return@mapNotNull null
            val tag = release.optString("tag_name")
            val version = ReleaseVersion.parse(tag)?.takeIf { it > current } ?: return@mapNotNull null
            val assetsJson = release.optJSONArray("assets") ?: return@mapNotNull null
            val assets = (0 until assetsJson.length()).map { assetIndex ->
                assetsJson.getJSONObject(assetIndex).let {
                    val name = it.getString("name")
                    require(name.isNotBlank() && '/' !in name && '\\' !in name && ".." !in name) {
                        "Unsafe release asset name"
                    }
                    ReleaseAsset(name, it.getString("browser_download_url"), it.optLong("size", -1))
                }
            }
            val apk = selectApk(assets, abis, tag) ?: return@mapNotNull null
            val sums = assets.singleOrNull { it.name == "SHA256SUMS.txt" } ?: return@mapNotNull null
            listOf(apk, sums).forEach { requireTrustedUrl(it.downloadUrl) }
            AppUpdate(tag, version, release.getString("html_url"), release.optString("body"), apk, sums)
        }.maxByOrNull { it.version }

    fun selectApk(assets: List<ReleaseAsset>, abis: List<String>, tag: String? = null): ReleaseAsset? {
        val prefix = tag?.let { "MaxSpeedVPN-$it-" }.orEmpty()
        abis.forEach { abi -> assets.singleOrNull { it.name == "$prefix$abi.apk" }?.let { return it } }
        return if (abis.isEmpty()) null else assets.singleOrNull { it.name == "${prefix}universal.apk" }
    }

    fun requireTrustedUrl(value: String) {
        val uri = URI(value)
        require(uri.scheme == "https" && uri.host?.lowercase() in setOf(
            "api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com",
        )) { "Untrusted release asset URL" }
    }
}

object UpdateChecksums {
    fun expected(body: String, filename: String): String? {
        if (filename.contains('/') || filename.contains("..")) return null
        val matches = body.lineSequence().mapNotNull { line ->
            Regex("^([0-9a-fA-F]{64})(?:  | \\*)([^/]+)$").matchEntire(line)?.destructured?.let { (hash, name) -> hash.lowercase() to name }
        }.filter { it.second == filename }.toList()
        return matches.singleOrNull()?.first
    }
}
