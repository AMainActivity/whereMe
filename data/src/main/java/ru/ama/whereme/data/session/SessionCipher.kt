package ru.ama.whereme.data.session

import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun interface SessionKeyProvider {
    fun key(): SecretKey
}

/** Version + a fresh 96-bit IV + ciphertext with the GCM authentication tag. */
class SessionCipher(private val keys: SessionKeyProvider) {
    fun encrypt(plaintext: ByteArray): ByteArray = checked {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.key())
        cipher.updateAAD(AAD)
        require(cipher.iv.size == IV_SIZE_BYTES)
        byteArrayOf(FORMAT_VERSION) + cipher.iv + cipher.doFinal(plaintext)
    }

    fun decrypt(envelope: ByteArray): ByteArray = checked {
        require(envelope.size >= MIN_ENVELOPE_SIZE_BYTES && envelope[0] == FORMAT_VERSION)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keys.key(),
            GCMParameterSpec(
                TAG_SIZE_BITS,
                envelope.copyOfRange(VERSION_SIZE_BYTES, CIPHERTEXT_OFFSET),
            ),
        )
        cipher.updateAAD(AAD)
        cipher.doFinal(envelope.copyOfRange(CIPHERTEXT_OFFSET, envelope.size))
    }

    private fun <T> checked(block: () -> T): T =
        try {
            block()
        } catch (failure: Exception) {
            throw IOException("Session storage is unavailable; stored data retained", failure)
        }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val VERSION_SIZE_BYTES = 1
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
        const val CIPHERTEXT_OFFSET = VERSION_SIZE_BYTES + IV_SIZE_BYTES
        const val MIN_ENVELOPE_SIZE_BYTES = CIPHERTEXT_OFFSET + TAG_SIZE_BITS / Byte.SIZE_BITS
        private val AAD = "ru.ama.whereme.session.v1".toByteArray(Charsets.UTF_8)
    }
}
