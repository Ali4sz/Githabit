package com.githabit.prototype.data

import com.githabit.prototype.domain.Workspace
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class WorkspaceByteEncodingTest {
    @Test fun canonicalEmptyWorkspaceBytesAreStableAcrossSinks() {
        val golden = """{"version":2,"onboarded":false,"theme":"SYSTEM","name":"","bio":"","habits":[],"commits":[],"calendar":{"initialWeekStart":"MONDAY","transitions":[],"pending":null}}"""
        val state = Workspace()
        val (bytes, digest) = WorkspaceJson.encodeAndDigest(state)
        assertArrayEquals(golden.toByteArray(), bytes)
        assertEquals(bytes.size, WorkspaceJson.encodedSize(state))
        assertEquals(MessageDigest.getInstance("SHA-256").digest(golden.toByteArray()).hexDigest(), digest)
        assertEquals(digest, WorkspaceJson.fingerprint(state))
        val output = ByteArrayOutputStream()
        WorkspaceJson.export(state) { output }
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test fun unicodeEscapesAndWriterChunkBoundariesPreserveCanonicalBytes() {
        val state = Workspace(name = "  Kept 🌱  ", bio = "\u0000\"\\\b\n\r\t\u000cالعربية")
        val (bytes, digest) = WorkspaceJson.encodeAndDigest(state)
        assertEquals(state, WorkspaceJson.decode(bytes))
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).hexDigest(), digest)
        val text = "x".repeat(8190) + "🌱 العربية\n"
        val writer = BoundedJsonWriter()
        writer.string(text)
        assertArrayEquals(("\"" + text.replace("\n", "\\n") + "\"").toByteArray(), writer.toByteArray())
    }
}
