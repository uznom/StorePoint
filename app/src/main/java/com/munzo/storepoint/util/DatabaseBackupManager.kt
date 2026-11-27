package com.munzo.storepoint.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.munzo.storepoint.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object DatabaseBackupManager {

    private const val TAG = "DatabaseBackupManager"

    data class RestoreSummary(
        val success: Boolean,
        val message: String,
        val productsCount: Int = 0,
        val transactionsCount: Int = 0,
        val usersCount: Int = 0,
        val backupDate: String = ""
    )

    data class BackupMetadata(
        val dateTime: String,
        val storeName: String,
        val productsCount: Int,
        val transactionsCount: Int,
        val usersCount: Int,
        val checksum: String,
        val file: File,
        val isEncrypted: Boolean = false
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private const val BACKUP_PBKDF2_ITERATIONS = 120_000

    private fun formatDate(date: Date): String = synchronized(dateFormat) {
        dateFormat.format(date)
    }

    private fun formatFileDate(date: Date): String = synchronized(fileDateFormat) {
        fileDateFormat.format(date)
    }

    private fun base64Encode(bytes: ByteArray): String =
        BackupIntegrityCodec.encodeBase64(bytes)

    private fun base64Decode(str: String): ByteArray =
        BackupIntegrityCodec.decodeBase64(str)

    private fun deriveBackupKeys(passphrase: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, BACKUP_PBKDF2_ITERATIONS, 512)
        val material = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Pair(material.copyOfRange(0, 32), material.copyOfRange(32, 64))
    }

    suspend fun encryptBackupJson(jsonContent: String, passphrase: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (passphrase.isEmpty()) {
                    return@withContext Result.failure(Exception("Backup encryption password must not be empty."))
                }
                val random = SecureRandom()
                val salt = ByteArray(16)
                val iv = ByteArray(16)
                random.nextBytes(salt)
                random.nextBytes(iv)
                val (encKey, macKey) = deriveBackupKeys(passphrase, salt)

                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
                val plain = jsonContent.toByteArray(Charsets.UTF_8)
                val first = cipher.update(plain, 0, plain.size)
                val tail = cipher.doFinal()
                val ciphertext = ByteArray(first.size + tail.size)
                first.copyInto(ciphertext, 0)
                tail.copyInto(ciphertext, first.size)

                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(macKey, "HmacSHA256"))
                mac.update(iv, 0, iv.size)
                mac.update(ciphertext, 0, ciphertext.size)
                val tag = mac.doFinal()

                val envelope = JSONObject().apply {
                    put("enc", 1)
                    put("iterations", BACKUP_PBKDF2_ITERATIONS)
                    put("salt", base64Encode(salt))
                    put("iv", base64Encode(iv))
                    put("mac", base64Encode(tag))
                    put("data", base64Encode(ciphertext))
                }
                Result.success(envelope.toString())
            } catch (e: Exception) {
                Result.failure(Exception("Backup encryption failed: ${e.localizedMessage}"))
            }
        }

    suspend fun decryptBackupJson(envelopeContent: String, passphrase: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val root = JSONObject(envelopeContent)
                if (root.optInt("enc", 0) != 1) {
                    return@withContext Result.failure(Exception("This file does not look like an encrypted StorePoint backup."))
                }
                if (passphrase.isEmpty()) {
                    return@withContext Result.failure(Exception("A backup password is required to restore this file."))
                }

                val salt = base64Decode(root.getString("salt"))
                val iv = base64Decode(root.getString("iv"))
                val expectedTag = base64Decode(root.getString("mac"))
                val ciphertext = base64Decode(root.getString("data"))
                val (encKey, macKey) = deriveBackupKeys(passphrase, salt)

                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(macKey, "HmacSHA256"))
                mac.update(iv, 0, iv.size)
                mac.update(ciphertext, 0, ciphertext.size)
                val actualTag = mac.doFinal()
                if (!MessageDigest.isEqual(expectedTag, actualTag)) {
                    return@withContext Result.failure(Exception("Incorrect backup password — MAC verification failed."))
                }

                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
                val plain = cipher.doFinal(ciphertext)
                Result.success(String(plain, Charsets.UTF_8))
            } catch (e: Exception) {
                Result.failure(Exception("Failed to decrypt backup: ${e.localizedMessage}"))
            }
        }

    suspend fun exportBackupJson(db: AppDatabase): String =
        BackupJsonExporter.export(db = db, formattedDate = formatDate(Date()))

    suspend fun importAndRestoreBackupJson(
        context: Context,
        db: AppDatabase,
        jsonContent: String
    ): RestoreSummary = BackupJsonImporter.importAndRestore(db = db, jsonContent = jsonContent)

    suspend fun saveBackupToFile(context: Context, jsonContent: String): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
        val filename = "storepoint_backup_${formatFileDate(Date())}.json"
        val file = File(backupDir, filename)
        FileOutputStream(file).use { out ->
            out.write(jsonContent.toByteArray(Charsets.UTF_8))
        }
        file
    }

    fun shareBackupFile(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "StorePoint POS Backup - ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export / Share Database Backup"))
    }

    fun getPersistentBackupDirectory(context: Context): File =
        File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }

    suspend fun savePersistentSnapshot(context: Context, db: AppDatabase, txId: Int? = null): File? =
        withContext(Dispatchers.IO) {
            try {
                val json = exportBackupJson(db)
                val targetDir = getPersistentBackupDirectory(context)
                if (!targetDir.exists()) targetDir.mkdirs()

                val canonicalFile = File(targetDir, "storepoint_vault_latest.json")
                val tempFile = File(targetDir, "storepoint_vault_latest.tmp")
                FileOutputStream(tempFile).use { it.write(json.toByteArray(Charsets.UTF_8)) }
                if (canonicalFile.exists()) canonicalFile.delete()
                tempFile.renameTo(canonicalFile)

                if (txId != null) {
                    val txFile = File(targetDir, "storepoint_tx_${txId}_${formatFileDate(Date())}.json")
                    FileOutputStream(txFile).use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }

                try {
                    val localDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
                    val localFile = File(localDir, "storepoint_backup_${formatFileDate(Date())}.json")
                    FileOutputStream(localFile).use { it.write(json.toByteArray(Charsets.UTF_8)) }
                } catch (_: Exception) {}

                val allTxFiles = targetDir.listFiles { file ->
                    file.name.startsWith("storepoint_tx_") && file.name.endsWith(".json")
                }?.sortedByDescending { it.lastModified() }

                if (allTxFiles != null && allTxFiles.size > 50) {
                    for (i in 50 until allTxFiles.size) {
                        try {
                            allTxFiles[i].delete()
                        } catch (_: Exception) {}
                    }
                }

                Log.d(TAG, "Silent persistent snapshot saved successfully: ${canonicalFile.absolutePath}")
                canonicalFile
            } catch (e: Exception) {
                Log.e(TAG, "Persistent snapshot error: ${e.message}", e)
                null
            }
        }

    fun getLatestPersistentBackup(context: Context): File? {
        val persistentDir = getPersistentBackupDirectory(context)
        val canonical = File(persistentDir, "storepoint_vault_latest.json")
        if (canonical.exists() && canonical.length() > 0) return canonical

        val persistentFiles = persistentDir.listFiles { file ->
            file.extension.equals("json", ignoreCase = true) && file.length() > 0
        }?.sortedByDescending { it.lastModified() }

        if (!persistentFiles.isNullOrEmpty()) return persistentFiles.first()

        return listLocalSnapshots(context).firstOrNull()
    }

    fun peekBackupMetadata(file: File): BackupMetadata? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val content = FileInputStream(file).use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
            val root = JSONObject(content)
            val isEncrypted = root.optInt("enc", 0) == 1
            val dateTime = root.optString("dateTime", formatDate(Date(file.lastModified())))
            val checksum = root.optString("checksum", "")

            var storeName = if (isEncrypted) "Encrypted Vault Backup" else "StorePoint Store"
            var prodCount = 0
            var txCount = 0
            var userCount = 0

            if (root.has("tables")) {
                val tables = root.getJSONObject("tables")
                if (tables.has("store_config")) {
                    storeName = tables.getJSONObject("store_config").optString("storeName", storeName)
                }
                if (tables.has("products")) {
                    prodCount = tables.getJSONArray("products").length()
                }
                if (tables.has("transactions")) {
                    txCount = tables.getJSONArray("transactions").length()
                }
                if (tables.has("users")) {
                    userCount = tables.getJSONArray("users").length()
                }
            }

            BackupMetadata(
                dateTime = dateTime,
                storeName = storeName,
                productsCount = prodCount,
                transactionsCount = txCount,
                usersCount = userCount,
                checksum = checksum,
                file = file,
                isEncrypted = isEncrypted
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error peeking backup metadata for ${file.name}: ${e.message}")
            null
        }
    }

    suspend fun autoCreateLocalSnapshot(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        try {
            savePersistentSnapshot(context, db)
            val dir = File(context.filesDir, "backups")
            if (dir.exists() && dir.isDirectory) {
                val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                dir.listFiles()?.forEach { file ->
                    if (file.lastModified() < sevenDaysAgo) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "Auto-snapshot failed", e)
        }
    }

    fun listLocalSnapshots(context: Context): List<File> {
        val persistentFiles = try {
            getPersistentBackupDirectory(context)
                .listFiles()?.filter { it.extension.equals("json", ignoreCase = true) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val localFiles = try {
            File(context.filesDir, "backups")
                .listFiles()?.filter { it.extension.equals("json", ignoreCase = true) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return (persistentFiles + localFiles)
            .distinctBy { it.name }
            .sortedByDescending { it.lastModified() }
    }
}
