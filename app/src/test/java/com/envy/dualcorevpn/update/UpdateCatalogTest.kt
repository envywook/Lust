package com.envy.dualcorevpn.update

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCatalogTest {
    @Test
    fun `compares alpha versions numerically`() {
        assertTrue(ReleaseVersion.parse("v0.1.10-alpha")!! > ReleaseVersion.parse("v0.1.9-alpha")!!)
        assertNull(ReleaseVersion.parse("0.1.11"))
        assertNull(ReleaseVersion.parse("v0.1.11-beta"))
    }

    @Test
    fun `selects newest non draft prerelease and exact abi asset`() {
        val json = JSONArray("""[
          {"tag_name":"v0.1.11-alpha","draft":true,"prerelease":true,"html_url":"https://github.com/envywook/Lust/releases/tag/v0.1.11-alpha","body":"draft","assets":[]},
          {"tag_name":"v0.1.10-alpha","draft":false,"prerelease":true,"html_url":"https://github.com/envywook/Lust/releases/tag/v0.1.10-alpha","body":"notes","assets":[
            {"name":"Lust-v0.1.10-alpha-arm64-v8a.apk","browser_download_url":"https://github.com/envywook/Lust/releases/download/v0.1.10-alpha/a.apk","size":12},
            {"name":"Lust-v0.1.10-alpha-universal.apk","browser_download_url":"https://github.com/envywook/Lust/releases/download/v0.1.10-alpha/u.apk","size":20},
            {"name":"SHA256SUMS.txt","browser_download_url":"https://github.com/envywook/Lust/releases/download/v0.1.10-alpha/SHA256SUMS.txt","size":100}
          ]}
        ]""")

        val update = UpdateCatalog.select(json, ReleaseVersion(0, 1, 9), listOf("arm64-v8a"))!!

        assertEquals("v0.1.10-alpha", update.tag)
        assertEquals("Lust-v0.1.10-alpha-arm64-v8a.apk", update.apk.name)
        assertEquals("SHA256SUMS.txt", update.checksums.name)
    }

    @Test
    fun `falls back to universal and rejects equal version`() {
        val assets = listOf(
            ReleaseAsset("Lust-v0.1.10-alpha-x86.apk", "https://github.com/x.apk", 1),
            ReleaseAsset("Lust-v0.1.10-alpha-universal.apk", "https://github.com/u.apk", 1),
        )
        assertEquals("Lust-v0.1.10-alpha-universal.apk", UpdateCatalog.selectApk(assets, listOf("arm64-v8a"), "v0.1.10-alpha")!!.name)
        assertNull(UpdateCatalog.selectApk(assets, emptyList(), "v0.1.10-alpha"))
    }

    @Test
    fun `ignores non prerelease entries even with alpha tag`() {
        val json = JSONArray("""[{
          "tag_name":"v9.9.9-alpha","draft":false,"prerelease":false,
          "html_url":"https://github.com/envywook/Lust/releases/tag/v9.9.9-alpha","assets":[]
        }]""")

        assertNull(UpdateCatalog.select(json, ReleaseVersion(0, 1, 10), listOf("arm64-v8a")))
    }

    @Test
    fun `parses exact checksum and rejects duplicates or traversal`() {
        val digest = "a".repeat(64)
        assertEquals(digest, UpdateChecksums.expected("$digest  Lust-v.apk\n", "Lust-v.apk"))
        assertNull(UpdateChecksums.expected("$digest  ../Lust-v.apk\n", "Lust-v.apk"))
        assertNull(UpdateChecksums.expected("$digest  Lust-v.apk\n$digest *Lust-v.apk\n", "Lust-v.apk"))
    }
}
