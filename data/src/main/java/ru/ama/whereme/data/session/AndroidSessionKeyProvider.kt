package ru.ama.whereme.data.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSessionKeyProvider @Inject constructor() : SessionKeyProvider {
    @Synchronized
    override fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        // Never request authentication: background sync must work without an interactive unlock prompt.
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false).setRandomizedEncryptionRequired(true).setKeySize(KEY_SIZE_BITS).build())
        }.generateKey()
    }
    private companion object {
        const val ALIAS = "whereme.session.v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 256
    }
}
