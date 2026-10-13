/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.core.data

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Build
import android.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.BuildConfig
import org.fcitx.fcitx5.android.core.data.DataManager.dataDir
import org.fcitx.fcitx5.android.utils.FileUtil
import org.fcitx.fcitx5.android.utils.appContext
import org.fcitx.fcitx5.android.utils.isJavaIdentifier
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Build up a Filesystem hierarchy at [dataDir]
 *
 * Operations are synchronized
 */
object DataManager {

    // #region debug-point A:data-sync
    private fun debugReport(
        hypothesisId: String,
        location: String,
        msg: String,
        data: Map<String, Any?> = emptyMap()
    ) = Unit
    // #endregion

    private val builtinPluginPackages = setOf(
        "org.fcitx.fcitx5.android.plugin.rime",
        "org.fcitx.fcitx5.android.plugin.rime.debug"
    )

    data class PluginSet(
        val loaded: Set<PluginDescriptor>,
        val failed: Map<String, PluginLoadFailed>
    )

    const val PLUGIN_INTENT = "${BuildConfig.APPLICATION_ID}.plugin.MANIFEST"

    private val lock = ReentrantLock()

    private val json by lazy { Json { prettyPrint = true } }

    var synced = false
        private set

    // should be consistent with the deserialization in DataDescriptorPlugin (:build-logic)
    private fun deserializeDataDescriptor(raw: String): DataDescriptor {
        return json.decodeFromString<DataDescriptor>(raw)
    }

    private fun serializeDataDescriptor(descriptor: DataDescriptor): String {
        return json.encodeToString(descriptor)
    }

    // If Android version supports direct boot, we put the hierarchy in device encrypted storage
    // instead of credential encrypted storage so that data can be accessed before user unlock
    val dataDir: File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Timber.d("Using device protected storage")
        appContext.createDeviceProtectedStorageContext().dataDir
    } else {
        File(appContext.applicationInfo.dataDir)
    }

    private fun AssetManager.getDataDescriptor(): DataDescriptor {
        return open(BuildConfig.DATA_DESCRIPTOR_NAME)
            .bufferedReader()
            .use { it.readText() }
            .let { deserializeDataDescriptor(it) }
    }

    private val loadedPlugins = mutableSetOf<PluginDescriptor>()
    private val failedPlugins = mutableMapOf<String, PluginLoadFailed>()

    fun getLoadedPlugins(): Set<PluginDescriptor> = loadedPlugins
    fun getFailedPlugins(): Map<String, PluginLoadFailed> = failedPlugins

    fun getSyncedPluginSet() = PluginSet(loadedPlugins, failedPlugins)

    /**
     * Will be cleared after each sync
     */
    private val callbacks = mutableListOf<() -> Unit>()

    private val criticalDataFiles = listOf(
        "usr/share/fcitx5/inputmethod/pinyin.conf",
        "usr/share/libime/sc.dict",
        "usr/share/libime/extb.dict",
        "usr/share/libime/zh_CN.lm",
        "usr/share/libime/zh_CN.lm.predict"
    )

    fun addOnNextSyncedCallback(block: () -> Unit) =
        callbacks.add(block)

    fun detectPlugins(): PluginSet {
        val toLoad = mutableSetOf<PluginDescriptor>()
        val preloadFailed = mutableMapOf<String, PluginLoadFailed>()

        val pm = appContext.packageManager

        val pluginPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                Intent(PLUGIN_INTENT),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            pm.queryIntentActivities(Intent(PLUGIN_INTENT), PackageManager.MATCH_ALL)
        }.map {
            it.activityInfo.packageName
        }.filterNot {
            it in builtinPluginPackages
        }

        Timber.d("Detected plugin packages: ${pluginPackages.joinToString()}")

        // Parse plugin.xml
        for (packageName in pluginPackages) {
            val res = pm.getResourcesForApplication(packageName)

            @SuppressLint("DiscouragedApi")
            val resId = res.getIdentifier("plugin", "xml", packageName)
            if (resId == 0) {
                Timber.w("Failed to get the plugin descriptor of $packageName")
                failedPlugins[packageName] = PluginLoadFailed.MissingPluginDescriptor
                continue
            }
            val parser = res.getXml(resId)
            var eventType = parser.eventType
            var domain: String? = null
            var apiVersion: String? = null
            var description: String? = null
            var hasService = false
            var text: String? = null
            while ((eventType != XmlPullParser.END_DOCUMENT)) {
                when (eventType) {
                    XmlPullParser.TEXT -> text = parser.text
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "apiVersion" -> apiVersion = text
                        "domain" -> domain = text
                        "description" -> description = text
                        "hasService" -> hasService = text?.lowercase() == "true"
                    }
                }
                eventType = parser.next()
            }
            parser.close()

            if (description?.startsWith("@string/") == true) {
                // Replace "@string/" with string resource
                val s = description.substring(8)
                if (s.isJavaIdentifier()) {
                    @SuppressLint("DiscouragedApi")
                    val id = res.getIdentifier(s, "string", packageName)
                    if (id != 0) description = res.getString(id)
                }
            }

            if (apiVersion != null && description != null) {
                if (PluginDescriptor.pluginAPI == apiVersion) {
                    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(
                            packageName,
                            PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                        )
                    } else {
                        pm.getPackageInfo(packageName, PackageManager.GET_META_DATA)
                    }
                    toLoad.add(
                        PluginDescriptor(
                            packageName,
                            apiVersion,
                            domain,
                            description,
                            hasService,
                            info.versionName ?: "",
                            info.applicationInfo?.nativeLibraryDir ?: ""
                        )
                    )
                } else {
                    Timber.w("$packageName's api version [$apiVersion] doesn't match with the current [${PluginDescriptor.pluginAPI}]")
                    preloadFailed[packageName] = PluginLoadFailed.PluginAPIIncompatible(apiVersion)
                }
            } else {
                Timber.w("Failed to parse plugin descriptor of $packageName")
                preloadFailed[packageName] = PluginLoadFailed.PluginDescriptorParseError
            }
        }
        return PluginSet(toLoad, preloadFailed)
    }

    fun sync() = lock.withLock {
        syncLocked(repairAttempted = false)
    }

    private fun syncLocked(repairAttempted: Boolean) {
        synced = false
        loadedPlugins.clear()
        failedPlugins.clear()

        val destDescriptorFile = File(dataDir, BuildConfig.DATA_DESCRIPTOR_NAME)
        // #region debug-point A:sync-start
        debugReport(
            "A",
            "DataManager.sync:start",
            "data sync started",
            mapOf(
                "dataDir" to dataDir.absolutePath,
                "descriptorExists" to destDescriptorFile.exists(),
                "usrDirExists" to dataDir.resolve("usr").exists(),
                "readmeExists" to dataDir.resolve("README.md").exists()
            )
        )
        // #endregion

        // load last run's data descriptor
        val oldDescriptor = destDescriptorFile
            .runCatching { deserializeDataDescriptor(bufferedReader().use { it.readText() }) }
            .getOrElse { DataDescriptor("", emptyMap(), emptyMap()) }

        // load app's data descriptor
        val mainDescriptor = appContext.assets.getDataDescriptor()

        val (parsedDescriptors, failed) = detectPlugins()
        failedPlugins.putAll(failed)
        // #region debug-point B:sync-assets
        debugReport(
            "B",
            "DataManager.sync:assets",
            "main descriptor and plugin detection prepared",
            mapOf(
                "mainHasRimeAddon" to mainDescriptor.files.containsKey("usr/share/fcitx5/addon/rime.conf"),
                "mainHasRimeInputMethod" to mainDescriptor.files.containsKey("usr/share/fcitx5/inputmethod/rime.conf"),
                "mainHasRimeDefaultYaml" to mainDescriptor.files.containsKey("usr/share/rime-data/default.yaml"),
                "mainHasRimeT9Schema" to mainDescriptor.files.containsKey("usr/share/rime-data/t9.schema.yaml"),
                "mainHasOpenccEmoji" to mainDescriptor.files.containsKey("usr/share/opencc/emoji.json"),
                "mainHasOpenccSymlink" to mainDescriptor.symlinks.containsKey("usr/share/rime-data/opencc"),
                "parsedPluginCount" to parsedDescriptors.size,
                "failedPluginCount" to failed.size,
                "parsedPlugins" to parsedDescriptors.joinToString { it.name }
            )
        )
        // #endregion

        Timber.d("Plugins to load: $parsedDescriptors")

        // Create an empty hierarchy
        val newHierarchy = DataHierarchy()
        // Always add app's first
        newHierarchy.install(mainDescriptor, FileSource.Main)

        val pluginAssets = mutableMapOf<String, AssetManager>()

        // Add plugin's one by one
        for (plugin in parsedDescriptors) {
            val pluginContext = appContext.createPackageContext(plugin.packageName, 0)
            val assets = pluginContext.assets
            val descriptor = runCatching { assets.getDataDescriptor() }.onFailure {
                Timber.w("Failed to get or decode data descriptor of '${plugin.name}'")
                Timber.w(it)
            }.getOrNull() ?: continue
            try {
                newHierarchy.install(descriptor, FileSource.Plugin(plugin))
            } catch (e: DataHierarchy.PathConflict) {
                Timber.w("Path '${e.path}' has already been created by '${e.src}', cannot create file")
                failedPlugins[plugin.packageName] =
                    PluginLoadFailed.PathConflict(plugin, e.path, e.src)
                continue
            } catch (e: DataHierarchy.SymlinkConflict) {
                Timber.w("Path '${e.path}' has already been created by '${e.src}', cannot create symlink")
                failedPlugins[plugin.packageName] =
                    PluginLoadFailed.PathConflict(plugin, e.path, e.src)
                continue
            }
            pluginAssets[plugin.name] = assets
            loadedPlugins.add(plugin)
            Timber.d("Merged data hierarchy of ${plugin.name}")
        }

        Timber.d("Hierarchy created")

        // Compute the difference of the created one and the old one
        // Run actions to migrate to the new hierarchy
        val expectedDescriptor = newHierarchy.downToDataDescriptor()
        val actions = DataHierarchy.diff(oldDescriptor, newHierarchy)
        actions.sortedByDescending { it.ordinal }.forEach {
            Timber.d("Action: $it")
            when (it) {
                is FileAction.CreateFile -> {
                    val assets = if (it.src is FileSource.Plugin)
                        pluginAssets.getValue(it.src.descriptor.name)
                    else appContext.assets
                    assets.copyFile(it.path)
                }
                is FileAction.DeleteDir -> {
                    removePath(it.path).getOrThrow()
                }
                is FileAction.DeleteFile -> {
                    removePath(it.path).getOrThrow()
                }
                is FileAction.UpdateFile -> {
                    val assets = if (it.src is FileSource.Plugin)
                        pluginAssets.getValue(it.src.descriptor.name)
                    else appContext.assets
                    assets.copyFile(it.path)
                }
                is FileAction.CreateSymlink -> {
                    removePath(it.path).getOrThrow()
                    symlink(it.src, it.path).getOrThrow()
                }
            }
        }
        // save the new hierarchy as the data descriptor to be used in the next run
        destDescriptorFile.bufferedWriter().use {
            it.write(serializeDataDescriptor(expectedDescriptor))
        }
        val criticalDataIssues = validateCriticalData(
            expectedDescriptor = expectedDescriptor,
            verifyDigest = actions.isEmpty() || repairAttempted
        )
        if (criticalDataIssues.isNotEmpty()) {
            Timber.w(
                "Critical input data invalid after sync (repairAttempted=$repairAttempted): %s",
                criticalDataIssues
            )
            debugReport(
                "B",
                "DataManager.sync:critical-data-invalid",
                "critical input data invalid after sync",
                mapOf(
                    "repairAttempted" to repairAttempted,
                    "issues" to criticalDataIssues.joinToString()
                )
            )
            if (!repairAttempted) {
                clearManagedData()
                syncLocked(repairAttempted = true)
                return
            }
        }
        callbacks.forEach { it() }
        callbacks.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // remove old assets from credential encrypted storage
            val oldDataDir = appContext.dataDir
            val oldDataDescriptor = oldDataDir.resolve(BuildConfig.DATA_DESCRIPTOR_NAME)
            if (oldDataDescriptor.exists()) {
                oldDataDescriptor.delete()
                oldDataDir.resolve("README.md").delete()
                oldDataDir.resolve("usr").deleteRecursively()
            }
        }
        synced = true
        Timber.d("Synced")
        // #region debug-point B:sync-done
        debugReport(
            "B",
            "DataManager.sync:done",
            "data sync finished",
            mapOf(
                "rimeAddonExists" to dataDir.resolve("usr/share/fcitx5/addon/rime.conf").exists(),
                "rimeInputMethodExists" to dataDir.resolve("usr/share/fcitx5/inputmethod/rime.conf").exists(),
                "rimeDefaultYamlExists" to dataDir.resolve("usr/share/rime-data/default.yaml").exists(),
                "rimeT9SchemaExists" to dataDir.resolve("usr/share/rime-data/t9.schema.yaml").exists(),
                "openccEmojiExists" to dataDir.resolve("usr/share/opencc/emoji.json").exists(),
                "rimeOpenccPathExists" to dataDir.resolve("usr/share/rime-data/opencc").exists(),
                "criticalDataHealthy" to criticalDataIssues.isEmpty(),
                "loadedPluginCount" to loadedPlugins.size,
                "failedPluginCount" to failedPlugins.size
            )
        )
        // #endregion
    }

    private fun validateCriticalData(
        expectedDescriptor: DataDescriptor,
        verifyDigest: Boolean
    ): List<String> {
        return criticalDataFiles.mapNotNull { path ->
            val expectedHash = expectedDescriptor.files[path]
            val file = dataDir.resolve(path)
            when {
                expectedHash.isNullOrBlank() -> "missing descriptor entry:$path"
                !file.exists() -> "missing file:$path"
                file.length() <= 0L -> "empty file:$path"
                verifyDigest && sha256(file) != expectedHash -> "hash mismatch:$path"
                else -> null
            }
        }
    }

    private fun clearManagedData() {
        dataDir.resolve(BuildConfig.DATA_DESCRIPTOR_NAME).delete()
        dataDir.resolve("README.md").delete()
        dataDir.resolve("usr").deleteRecursively()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return Base64.encodeToString(digest.digest(), 0).trim()
    }

    private fun removePath(path: String) =
        FileUtil.removeFile(dataDir.resolve(path))

    private fun symlink(source: String, target: String) =
        FileUtil.symlink(dataDir.resolve(source), dataDir.resolve(target))

    private fun AssetManager.copyFile(filename: String) {
        open(filename).use { i ->
            File(dataDir, filename)
                .also { it.parentFile?.mkdirs() }
                .outputStream()
                .use { o -> i.copyTo(o) }
        }
    }

    fun deleteAndSync() {
        lock.withLock {
            // #region debug-point A:delete-and-sync
            debugReport(
                "A",
                "DataManager.deleteAndSync",
                "delete and sync invoked",
                mapOf(
                    "dataDir" to dataDir.absolutePath,
                    "descriptorExistsBeforeDelete" to dataDir.resolve(BuildConfig.DATA_DESCRIPTOR_NAME).exists(),
                    "usrDirExistsBeforeDelete" to dataDir.resolve("usr").exists()
                )
            )
            // #endregion
            dataDir.resolve(BuildConfig.DATA_DESCRIPTOR_NAME).delete()
            dataDir.resolve("README.md").delete()
            dataDir.resolve("usr").deleteRecursively()
        }
        sync()
    }

}
