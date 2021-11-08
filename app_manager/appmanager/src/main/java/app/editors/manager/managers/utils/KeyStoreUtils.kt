package app.editors.manager.managers.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.Charset
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

object KeyStoreUtils {

    const val ALIAS = "TEST"
    const val ANDROID_KEY_STORE = "AndroidKeyStore"
    const val RSA_ECB_PKCS1_PADDING = "RSA/ECB/PKCS1Padding"
    const val KEY_ALGORITHM_RSA = "RSA"

    fun init() {

        val ks = KeyStore.getInstance(ANDROID_KEY_STORE)
        ks.load(null)

        val privateKey = ks.getKey(ALIAS, null) as PrivateKey?
        val publicKey: PublicKey? = ks.getCertificate(ALIAS)?.publicKey

        privateKey?.let {
            publicKey?.let {
                return
            }
        }

        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .build()

        val kpGen = KeyPairGenerator.getInstance(KEY_ALGORITHM_RSA, ANDROID_KEY_STORE)
        kpGen.initialize(spec)
        kpGen.generateKeyPair()
    }

    fun encryptData(data: String): String {
        val ks = KeyStore.getInstance(ANDROID_KEY_STORE)
        ks.load(null)

        val publicKey = ks.getCertificate(ALIAS).publicKey ?: return ""

        val cipher = Cipher.getInstance(RSA_ECB_PKCS1_PADDING)

        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        return Base64.encodeToString(encryptedData, Base64.DEFAULT)
    }

    fun decryptData(data: String): String {
        val ks = KeyStore.getInstance(ANDROID_KEY_STORE)
        ks.load(null)

        val privateKey = ks.getKey(ALIAS, null) as PrivateKey

        val encrypted = Base64.decode(data, Base64.DEFAULT)

        val cipher = Cipher.getInstance(RSA_ECB_PKCS1_PADDING)

        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val result = cipher.doFinal(encrypted)
        return result.toString(Charsets.UTF_8)
    }

}