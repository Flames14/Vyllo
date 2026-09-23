package com.vyllo.music.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateSecurityTest {

    @Test
    fun `trusted hosts allow github release delivery paths`() {
        assertTrue(AppUpdateRepository.isTrustedApkHost("https://github.com/Flames14/Vyllo/releases/download/v1/a.apk"))
        assertTrue(AppUpdateRepository.isTrustedApkHost("https://api.github.com/repos/Flames14/Vyllo/releases/assets/1"))
        assertTrue(AppUpdateRepository.isTrustedApkHost("https://objects.githubusercontent.com/github-production-release-asset-2e65be/x.apk"))
        assertTrue(AppUpdateRepository.isTrustedApkHost("https://release-assets.githubusercontent.com/github-production-release-asset/x"))
        assertTrue(AppUpdateRepository.isTrustedApkHost("https://raw.githubusercontent.com/Flames14/Vyllo/main/a.apk"))
    }

    @Test
    fun `untrusted or non-https hosts are rejected`() {
        assertFalse(AppUpdateRepository.isTrustedApkHost("http://github.com/x.apk"))
        assertFalse(AppUpdateRepository.isTrustedApkHost("https://evil.example.com/x.apk"))
        assertFalse(AppUpdateRepository.isTrustedApkHost("https://github.com.evil.com/x.apk"))
        assertFalse(AppUpdateRepository.isTrustedApkHost("https://notgithub.com/x.apk"))
        assertFalse(AppUpdateRepository.isTrustedApkHost("ftp://github.com/x.apk"))
        assertFalse(AppUpdateRepository.isTrustedApkHost("not-a-url"))
        assertFalse(AppUpdateRepository.isTrustedApkHost(""))
    }

    @Test
    fun `suffix trick with untrusted parent domain is rejected`() {
        // host must equal a trusted host or end with .trusted — not startswith tricks
        assertFalse(AppUpdateRepository.isTrustedApkHost("https://evilgithub.com/x.apk"))
        assertFalse(AppUpdateRepository.isTrustedApkHost("https://attacker.objects.githubusercontent.com.attacker.io/x.apk"))
    }
}
