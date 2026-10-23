package io.github.winddrift.fuckpangguai.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.winddrift.fuckpangguai.storage.appDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TokenStore(private val context: Context) {

    @Volatile
    var currentToken: String? = null
        private set

    suspend fun load(): String? = withContext(Dispatchers.IO) {
        val preferences = context.appDataStore.data.first()
        val cipherText = preferences[TOKEN_CIPHER] ?: return@withContext null
        val iv = preferences[TOKEN_IV] ?: return@withContext null
        val token = runCatching { decrypt(cipherText, iv) }.getOrNull()
        currentToken = token
        if (token == null) {
            context.appDataStore.edit {
                it.remove(TOKEN_CIPHER)
                it.remove(TOKEN_IV)
            }
        }
        token
    }

    suspend fun save(token: String): Unit = withContext(Dispatchers.IO) {
        require(token.isNotBlank()) { "token must not be blank" }
        val (cipherText, iv) = encrypt(token)
        context.appDataStore.edit {
            it[TOKEN_CIPHER] = cipherText
            it[TOKEN_IV] = iv
        }
        currentToken = token
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        currentToken = null
        context.appDataStore.edit {
            it.remove(TOKEN_CIPHER)
            it.remove(TOKEN_IV)
        }
    }

    private fun encrypt(value: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP) to
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(cipherText: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        return String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "pangguai_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val TOKEN_CIPHER = stringPreferencesKey("token_cipher")
        val TOKEN_IV = stringPreferencesKey("token_iv")
    }
}
