package ru.ama.whereme.data.session

import java.io.IOException
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class SessionCipherTest {
    private fun cipher(): SessionCipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return SessionCipher { key }
    }
    @Test fun roundTripWithFreshIvForEveryWrite() {
        val cipher = cipher()
        val plain = "synthetic-token".toByteArray()
        val first = cipher.encrypt(plain)
        val second = cipher.encrypt(plain)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, cipher.decrypt(first))
        assertArrayEquals(plain, cipher.decrypt(second))
    }
    @Test fun tamperedCiphertextIsRejected() {
        val cipher = cipher()
        val envelope = cipher.encrypt("test".toByteArray())
        envelope[envelope.lastIndex] = (envelope.last().toInt() xor 1).toByte()
        rejected { cipher.decrypt(envelope) }
    }
    @Test fun differentKeyCannotDecrypt() {
        val encrypted = cipher().encrypt("test".toByteArray())
        rejected { cipher().decrypt(encrypted) }
    }
    @Test fun truncatedAndUnsupportedEnvelopesAreRejected() {
        for (value in listOf(byteArrayOf(), byteArrayOf(1), ByteArray(29) { 2 })) rejected { cipher().decrypt(value) }
    }
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Expected authenticated decryption failure") } catch (failure: IOException) {
            assertFalse(failure.message.orEmpty().contains("synthetic-token"))
        }
    }
}
