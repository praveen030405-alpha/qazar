package com.qazar.pdfviewer.data.tools

import android.content.Context
import android.media.MediaScannerConnection
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.qazar.pdfviewer.data.QazarStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enterprise-Grade Offline PDF Protection & Security Engine
 * Secures documents with AES-256-GCM vault encapsulation and PBKDF2 key derivation.
 * Includes Hardware-backed Keystore support.
 */
object PdfProtectionEngine {

    private const val TAG = "PdfProtectionEngine"
    private const val ENCRYPTED_MAGIC_V1 = "QAZAR_SECURE_PDF_VAULT_V1\n"
    private const val ENCRYPTED_MAGIC_V2 = "QAZAR_SECURE_PDF_VAULT_V2\n"

    // --- User Vault (Password Protected PDF) ---

    suspend fun encryptPdf(
        context: Context,
        inputFile: File,
        outputFile: File? = null,
        password: String
    ): Result<File> = withContext(Dispatchers.IO) {
        val res = protectPdf(context, inputFile, password)
        if (outputFile != null && res.isSuccess) {
            val generated = res.getOrThrow()
            if (generated.absolutePath != outputFile.absolutePath) {
                outputFile.parentFile?.mkdirs()
                generated.copyTo(outputFile, overwrite = true)
                Result.success(outputFile)
            } else res
        } else res
    }

    suspend fun protectPdf(
        context: Context,
        inputFile: File,
        password: String
    ): Result<File> = withContext(Dispatchers.IO) {
        if (SecurityGuardian.isVaultRestricted) {
            return@withContext Result.failure(SecurityException("Vault operations are restricted due to environment tampering."))
        }
        try {
            if (password.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Password cannot be blank"))
            }

            val baseName = inputFile.nameWithoutExtension.ifBlank { "Document" }
            val exportsDir = QazarStorageManager.getExportsDir(context)
            val outFile = File(exportsDir, "${baseName}_Protected.pdf")

            // V2: AES-256-GCM with PBKDF2
            val random = SecureRandom()
            val salt = ByteArray(16).apply { random.nextBytes(this) }
            val iv = ByteArray(12).apply { random.nextBytes(this) }

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            FileOutputStream(outFile).use { fos ->
                fos.write(ENCRYPTED_MAGIC_V2.toByteArray(Charsets.UTF_8))
                fos.write(salt)
                fos.write(iv)

                FileInputStream(inputFile).use { fis ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        val encryptedChunk = cipher.update(buffer, 0, read)
                        if (encryptedChunk != null && encryptedChunk.isNotEmpty()) {
                            fos.write(encryptedChunk)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        fos.write(finalBytes)
                    }
                }
            }

            MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf("application/pdf"), null)
            Log.i(TAG, "Protected PDF (V2) created: ${outFile.absolutePath}")
            Result.success(outFile)
        } catch (e: Exception) {
            Log.e(TAG, "Protection failed", e)
            Result.failure(e)
        }
    }

    fun isVaultProtected(file: File): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                val magic1 = ENCRYPTED_MAGIC_V1.toByteArray(Charsets.UTF_8)
                val buffer = ByteArray(magic1.size)
                val read = fis.read(buffer)
                if (read == magic1.size) {
                    val str = String(buffer, Charsets.UTF_8)
                    str == ENCRYPTED_MAGIC_V1 || str == ENCRYPTED_MAGIC_V2
                } else false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun unlockPdf(
        context: Context,
        protectedFile: File,
        password: String
    ): Result<File> = withContext(Dispatchers.IO) {
        if (SecurityGuardian.isVaultRestricted) {
            return@withContext Result.failure(SecurityException("Vault operations are restricted due to environment tampering."))
        }
        try {
            val baseName = protectedFile.nameWithoutExtension.replace("_Protected", "")
            val exportsDir = QazarStorageManager.getExportsDir(context)
            val outFile = File(exportsDir, "${baseName}_Unlocked.pdf")

            FileInputStream(protectedFile).use { fis ->
                val magicBuffer = ByteArray(ENCRYPTED_MAGIC_V1.toByteArray(Charsets.UTF_8).size)
                fis.read(magicBuffer)
                val magic = String(magicBuffer, Charsets.UTF_8)

                if (magic == ENCRYPTED_MAGIC_V1) {
                    // Legacy V1 decryption (CBC)
                    val digest = MessageDigest.getInstance("SHA-256")
                    val keyBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
                    val secretKey = SecretKeySpec(keyBytes, "AES")
                    val ivBytes = ByteArray(16) { i -> (keyBytes[i].toInt() xor 0x5A).toByte() }
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(ivBytes))

                    FileOutputStream(outFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            val decryptedChunk = cipher.update(buffer, 0, read)
                            if (decryptedChunk != null && decryptedChunk.isNotEmpty()) fos.write(decryptedChunk)
                        }
                        val finalBytes = cipher.doFinal()
                        if (finalBytes != null && finalBytes.isNotEmpty()) fos.write(finalBytes)
                    }

                } else if (magic == ENCRYPTED_MAGIC_V2) {
                    // V2 decryption (GCM)
                    val salt = ByteArray(16)
                    fis.read(salt)
                    val iv = ByteArray(12)
                    fis.read(iv)

                    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
                    val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

                    FileOutputStream(outFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            val decryptedChunk = cipher.update(buffer, 0, read)
                            if (decryptedChunk != null && decryptedChunk.isNotEmpty()) fos.write(decryptedChunk)
                        }
                        val finalBytes = cipher.doFinal()
                        if (finalBytes != null && finalBytes.isNotEmpty()) fos.write(finalBytes)
                    }
                } else {
                    return@withContext Result.failure(Exception("Unknown or corrupt vault format."))
                }
            }

            MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf("application/pdf"), null)
            Log.i(TAG, "Unlocked PDF created: ${outFile.absolutePath}")
            Result.success(outFile)
        } catch (e: Exception) {
            Log.e(TAG, "Unlock failed", e)
            Result.failure(e)
        }
    }

    // --- Hardware Keystore (For App internal secrets) ---

    private const val KEY_ALIAS = "QazarMasterWrapKey"

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            )
            keyGenerator.generateKey()
        }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    fun encryptAppSecret(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        // Prepend IV (12 bytes)
        return iv + ciphertext
    }

    fun decryptAppSecret(encryptedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = encryptedData.copyOfRange(0, 12)
        val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}
