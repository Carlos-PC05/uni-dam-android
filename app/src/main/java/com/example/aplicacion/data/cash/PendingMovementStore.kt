package com.example.aplicacion.data.cash

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.example.aplicacion.model.Movement
import com.example.aplicacion.model.MovementCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Conserva un envío por cuenta hasta que Firestore confirme su resultado. */
interface PendingMovementStore {
    suspend fun load(uid: String): Movement?
    suspend fun save(uid: String, movement: Movement)
    suspend fun clear(uid: String)
}

@Singleton
class FilePendingMovementStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PendingMovementStore {
    private val key: SecretKey by lazy {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(
                    KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build())
                generateKey()
            }
    }

    override suspend fun load(uid: String): Movement? = withContext(Dispatchers.IO) {
        val file = file(uid)
        val bytes = try {
            synchronized(fileLock) { file.readFully() }
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists()) throw error
            return@withContext null
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = bytes.copyOfRange(0, IV_SIZE)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(uid.encodeToByteArray())
        val data = JSONObject(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).decodeToString())
        Movement(
            data.getString("id"),
            data.getLong("centimos"),
            LocalDate.ofEpochDay(data.getLong("dia")),
            MovementCategory.valueOf(data.getString("categoria")),
            data.getString("nota"),
        )
    }

    override suspend fun save(uid: String, movement: Movement) = withContext(Dispatchers.IO) {
        val data = JSONObject()
            .put("id", movement.id)
            .put("centimos", movement.cents)
            .put("dia", movement.date.toEpochDay())
            .put("categoria", movement.category.name)
            .put("nota", movement.note)
            .toString()
            .encodeToByteArray()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(uid.encodeToByteArray())
        check(cipher.iv.size == IV_SIZE)
        val encrypted = cipher.iv + cipher.doFinal(data)

        val file = file(uid)
        synchronized(fileLock) {
            val stream = file.startWrite()
            try {
                stream.write(encrypted)
                stream.fd.sync()
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
            file.finishWrite(stream)
            check(file.readFully().contentEquals(encrypted))
        }
    }

    override suspend fun clear(uid: String) = withContext(Dispatchers.IO) {
        val file = file(uid)
        synchronized(fileLock) {
            file.delete()
            check(!file.baseFile.exists())
        }
    }

    private fun file(uid: String) =
        AtomicFile(File(context.noBackupFilesDir, "pending-movement-${Uri.encode(uid)}"))

    private companion object {
        const val KEY_ALIAS = "pending-movement"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        val fileLock = Any()
    }
}
