package com.maximillianurio.afya.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * An optional passphrase over the JSON backup. The file stays a small JSON envelope, so the same
 * picker opens it, but the history inside is AES-256-GCM under a key stretched from the passphrase
 * with PBKDF2-HMAC-SHA256. Nothing about the passphrase is stored anywhere; lose it and the file is
 * unreadable, which is the point.
 */
object BackupCrypto {
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 310_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val MARKER = "AES-256-GCM"

    class WrongPassphrase : Exception("The passphrase does not open this backup")

    /** True when [text] is an envelope this object wrote, rather than a plain backup. */
    fun isSealed(text: String): Boolean = field(text, "encrypted") == MARKER

    fun seal(plain: String, passphrase: String, random: SecureRandom = SecureRandom()): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // Every value is a fixed word or base64, so the envelope needs no escaping and no JSON library.
        return listOf(
            "app" to "\"afya\"",
            "encrypted" to "\"$MARKER\"",
            "kdf" to "\"$KDF\"",
            "iterations" to "$ITERATIONS",
            "salt" to "\"${b64(salt)}\"",
            "iv" to "\"${b64(iv)}\"",
            "data" to "\"${b64(sealed)}\"",
        ).joinToString(",\n", prefix = "{\n", postfix = "\n}\n") { (name, value) -> "  \"$name\": $value" }
    }

    /** The plain backup inside [text]; throws [WrongPassphrase] when the passphrase does not fit. */
    fun open(text: String, passphrase: String): String {
        require(isSealed(text)) { "Not an encrypted Afya backup" }
        val salt = unb64(requireNotNull(field(text, "salt")) { "No salt" })
        val iv = unb64(requireNotNull(field(text, "iv")) { "No nonce" })
        val data = unb64(requireNotNull(field(text, "data")) { "No data" })
        val iterations = Regex("\"iterations\"\\s*:\\s*(\\d+)").find(text)
            ?.groupValues?.get(1)?.toIntOrNull() ?: ITERATIONS
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        return try {
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw WrongPassphrase()
        }
    }

    private fun key(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun field(text: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1)

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)
}
