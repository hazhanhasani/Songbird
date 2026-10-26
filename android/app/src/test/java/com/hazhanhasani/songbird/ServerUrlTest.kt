package com.hazhanhasani.songbird

import org.junit.Assert.*
import org.junit.Test

class ServerUrlTest {
    @Test fun normalizesHttpsDomain() {
        assertEquals("https://chat.example.com", ServerUrl.normalize(" chat.example.com/ "))
        assertEquals("https://chat.example.com:8443", ServerUrl.normalize("https://CHAT.example.com:8443"))
    }

    @Test fun refusesCredentialsAndHttpAndPaths() {
        assertNull(ServerUrl.normalize("http://chat.example.com"))
        assertNull(ServerUrl.normalize("https://user:pass@chat.example.com"))
        assertNull(ServerUrl.normalize("https://chat.example.com/path"))
        assertNull(ServerUrl.normalize("https://chat.example.com/?next=https://evil.example"))
        assertNull(ServerUrl.normalize("javascript:alert(1)"))
        assertNull(ServerUrl.normalize("localhost"))
    }

    @Test fun comparesSemanticVersions() {
        assertTrue(AndroidUpdates.compareVersions("1.10.0", "1.9.99") > 0)
        assertEquals(0, AndroidUpdates.compareVersions("1.2.0", "1.2.0"))
        assertTrue(AndroidUpdates.compareVersions("2.0.0", "1.99.99") > 0)
    }
}
