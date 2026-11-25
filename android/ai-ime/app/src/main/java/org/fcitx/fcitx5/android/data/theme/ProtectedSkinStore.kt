package org.fcitx.fcitx5.android.data.theme

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.fcitx.fcitx5.android.utils.appContext
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/** Immutable ciphertext on disk; only authenticated resources are decoded in memory. */
object ProtectedSkinStore {
    const val FORMAT = "paopao-skin-encrypted-v1"
    private const val SIGNER = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAhYPP6JyauEocri5PSm+OqKMTBbQrIanR23mr9MEOh7lIFDF/TSQYPNY0smRN/0TPi3EVHQay8IA3TfBWiod3R79nGJArVSk/47ZMYgh68ste44X5amH2gdI92zG0pOTaIua8trlk49GHxFeFrLBLv9OklXEauXlBUJ7IuvrNbSYcpPxYOhhBaxn8rcBKOoMvp9rGnpIfOuz5iTDJWiay7c7YbSdOS6qIfsP6j3xW3zNVk+UuuuDi85cUvW4fPpOeWO4lrFyKTr8kvGyYj7ywN3IudkRNwOI8EX+FtIqp7JLDdYzFyX6z4joHU3MPIx+ItE6bhjGf7WYyRHxMEqbyts+Dr8VcfPeEimUaggxzkqMgi7E/Y1LgR6OIczTmqywvQUCXKyUihz6V8e2dpjh1pUAjslkPCS91lVA2gNjEYMOZ4zO2/iamWhBZKcEDIJ7aU5Cq9L6lcx1cxJ2rkFrbfa5u7ZHBJTZIjmT8gZaMCFSgLOdplSLX8xE6xFiFC4FnAgMBAAE="
    private const val KEY_ID = "f30886c6aa36fe01167058bc"
    private const val ALIAS = "paopao.skin.device.v1"
    private val root get() = File(appContext.noBackupFilesDir, "skin-protection").apply { mkdirs() }
    private val serverKey by lazy { KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decode(SIGNER))) }
    private val oaep = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
    fun owner(): String = appContext.getSharedPreferences("auth_session", Context.MODE_PRIVATE).let {
        if (it.getString("access_token", "").isNullOrBlank()) "" else it.getString("user_id", "").orEmpty()
    }
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
    private fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
    fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun vaultKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun publicKey(): String {
        val publicFile = File(root, "device.spki")
        val privateFile = File(root, "device.sealed")
        if (!publicFile.exists() || !privateFile.exists()) {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, vaultKey()); updateAAD(ALIAS.toByteArray()) }
            val raw = pair.private.encoded
            try { privateFile.writeBytes(cipher.iv + cipher.doFinal(raw)); publicFile.writeBytes(pair.public.encoded) }
            finally { raw.fill(0) }
        }
        return encode(publicFile.readBytes())
    }
    private fun unwrap(bytes: ByteArray): ByteArray {
        publicKey()
        val sealed = File(root, "device.sealed").readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, vaultKey(), GCMParameterSpec(128, sealed.copyOfRange(0,12))); updateAAD(ALIAS.toByteArray()) }
        val raw = cipher.doFinal(sealed.copyOfRange(12,sealed.size))
        try {
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(raw))
            return Cipher.getInstance("RSA/ECB/OAEPPadding").apply { init(Cipher.DECRYPT_MODE,key,oaep) }.doFinal(bytes)
        } finally { raw.fill(0) }
    }
    fun verify(bytes: ByteArray): JSONObject {
        require(bytes.size <= 192 * 1024)
        val envelope = JSONObject(bytes.toString(Charsets.UTF_8))
        val payload = decode(envelope.getString("payload"))
        val valid = Signature.getInstance("SHA256withRSA").run { initVerify(serverKey); update(payload); verify(decode(envelope.getString("signature"))) }
        check(valid) { "皮肤签名校验失败" }
        return JSONObject(payload.toString(Charsets.UTF_8)).also {
            check(it.getString("format") == FORMAT && it.getString("signingKeyId") == KEY_ID)
        }
    }
    data class Opened(val manifest: JSONObject, val key: ByteArray, val owner: String)
    private val cache = LinkedHashMap<String, Opened>()
    @Synchronized fun open(directory: File): Opened {
        val current = owner();check(current.isNotEmpty()) { "请先登录使用皮肤" }
        val licenseBytes = File(directory,"license.json").readBytes()
        val manifestBytes = File(directory,"protected.json").readBytes()
        val cacheId = current + sha(licenseBytes) + sha(manifestBytes)
        cache[cacheId]?.let { check(!SkinOwnershipStore.denied(it.manifest.getString("packageThemeName"))) { "该皮肤使用权已关闭" }; return it }
        val license = verify(licenseBytes)
        check(license.getString("owner") == current && license.getString("platform") == "android")
        check(license.getLong("expiresAt") == 0L && license.getLong("issuedAt") <= System.currentTimeMillis()+60000)
        check(license.getString("deviceKeySha256") == sha(decode(publicKey())))
        check(license.getString("manifestSha256") == sha(manifestBytes))
        val manifest = verify(manifestBytes)
        check(!SkinOwnershipStore.denied(manifest.getString("packageThemeName"))) { "该皮肤使用权已关闭" }
        check(manifest.getString("platform") == "android" && manifest.getString("packageThemeName") == license.getString("packageThemeName") && manifest.getLong("revision") == license.getLong("revision"))
        val files=manifest.getJSONArray("files");check(files.length() in 1..256)
        val names=HashSet<String>()
        for(i in 0 until files.length()) { val file=files.getJSONObject(i);val name=file.getString("name")
            check(name.matches(Regex("[A-Za-z0-9_-]+\\.(png|json)")) && names.add(name) && file.getString("path")==name+".psk")
            check(file.getLong("size") in 32..(20L*1024*1024) && file.getLong("plainSize") == file.getLong("size")-32)
        }
        val key=unwrap(decode(license.getString("wrappedKey")));check(key.size==32)
        if(cache.size>=8) { cache.remove(cache.keys.first())?.key?.fill(0) }
        return Opened(manifest,key,current).also { cache[cacheId]=it }
    }
    @Synchronized fun read(directory: File, name: String): ByteArray {
        val opened=open(directory)
        val files=opened.manifest.getJSONArray("files")
        val entry=(0 until files.length()).map { files.getJSONObject(it) }.single { it.getString("name")==name }
        val file=File(directory,entry.getString("path"));check(file.length()==entry.getLong("size"))
        val bytes=file.readBytes();check(sha(bytes)==entry.getString("sha256") && bytes.copyOfRange(0,4).contentEquals("PSK1".toByteArray()))
        val aad="$FORMAT\n${opened.manifest.getString("contentId")}\n$name".toByteArray()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,SecretKeySpec(opened.key,"AES"),GCMParameterSpec(128,bytes.copyOfRange(4,16)));updateAAD(aad) }
        return cipher.doFinal(bytes.copyOfRange(16,bytes.size)).also { check(sha(it)==entry.getString("plainSha256"));check(owner()==opened.owner) }
    }
    fun readImage(file: File): ByteArray = read(file.parentFile!!,file.name.removeSuffix(".psk"))
    fun authorized(file: File): Boolean = runCatching { open(file.parentFile!!);true }.getOrDefault(false)
    /** Finite trial licences are deliberately rejected by open()/authorized(). */
    fun extractTrial(directory: File, destination: File): Long {
        val current=owner();check(current.isNotBlank())
        val license=verify(File(directory,"license.json").readBytes())
        val now=System.currentTimeMillis()
        val expires=license.getLong("expiresAt")
        check(license.optString("purpose")=="trial"&&license.getString("owner")==current&&license.getString("platform")=="android")
        check(expires>now&&expires-license.getLong("issuedAt")==600000L&&license.getLong("issuedAt")<=now+60000)
        check(license.getString("deviceKeySha256")==sha(decode(publicKey())))
        val manifestBytes=File(directory,"protected.json").readBytes()
        check(sha(manifestBytes)==license.getString("manifestSha256"))
        val manifest=verify(manifestBytes)
        check(manifest.getString("platform")=="android"&&manifest.getString("packageThemeName")==license.getString("packageThemeName")&&manifest.getLong("revision")==license.getLong("revision"))
        val files=manifest.getJSONArray("files");check(files.length() in 1..256)
        val names=HashSet<String>();var total=0L
        val key=unwrap(decode(license.getString("wrappedKey")));check(key.size==32)
        try {
            for(i in 0 until files.length()) {
                val entry=files.getJSONObject(i);val name=entry.getString("name")
                check(name.matches(Regex("[A-Za-z0-9_-]+\\.(png|json)"))&&names.add(name)&&entry.getString("path")=="$name.psk")
                val size=entry.getLong("size");total+=size
                check(size in 32..(20L*1024*1024)&&total<=64L*1024*1024&&entry.getLong("plainSize")==size-32)
                val file=File(directory,"$name.psk");check(file.length()==size)
                val bytes=file.readBytes();check(sha(bytes)==entry.getString("sha256")&&bytes.copyOfRange(0,4).contentEquals("PSK1".toByteArray()))
                val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,bytes.copyOfRange(4,16)));updateAAD("$FORMAT\n${manifest.getString("contentId")}\n$name".toByteArray())}
                val plain=cipher.doFinal(bytes.copyOfRange(16,bytes.size))
                try {check(sha(plain)==entry.getString("plainSha256"));File(destination,name).writeBytes(plain)}finally{plain.fill(0)}
            }
            check(owner()==current&&System.currentTimeMillis()<expires)
            return (expires-System.currentTimeMillis()).coerceIn(1,600000)
        }finally{key.fill(0)}
    }
    @Synchronized fun clearMemory() { cache.values.forEach { it.key.fill(0) };cache.clear() }
}
