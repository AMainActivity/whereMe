package ru.ama.whereme.data.session

import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun interface SessionKeyProvider { fun key(): SecretKey }

/** Version + a fresh 96-bit IV + ciphertext with the GCM authentication tag. */
class SessionCipher(private val keys: SessionKeyProvider) {
    fun encrypt(plaintext: ByteArray): ByteArray = checked {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keys.key())
        cipher.updateAAD(AAD)
        require(cipher.iv.size == 12)
        byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
    }
    fun decrypt(envelope: ByteArray): ByteArray = checked {
        require(envelope.size >= 29 && envelope[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
        cipher.updateAAD(AAD)
        cipher.doFinal(envelope.copyOfRange(13, envelope.size))
    }
    private fun <T> checked(block: () -> T): T = try { block() } catch (_: Exception) {
        throw IOException("Session storage is unavailable; stored data retained")
    }
    companion object {
        private val AAD = "ru.ama.whereme.session.v1".toByteArray(Charsets.UTF_8)
    }
}
