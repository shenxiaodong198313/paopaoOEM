package roro.stellar.server.bootstrap

import android.content.pm.ApplicationInfo
import android.ddm.DdmHandleAppName
import android.os.Looper
import android.os.ServiceManager
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.StellarService
import roro.stellar.server.util.Logger
import roro.stellar.server.util.PackageManagerCompat

object ServerBootstrap {
    private val LOGGER = Logger("ServerBootstrap")
    private const val MANAGER_PACKAGE_ARG = "--manager-package="
    private const val SERVER_PROCESS_SUFFIX = ":stellar_server"

    private fun normalizePackageName(candidate: String?): String? {
        return candidate
            ?.substringBefore(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() && '.' in it }
    }

    fun buildServerProcessName(managerPackageName: String?): String {
        val normalized = normalizePackageName(managerPackageName)
        return if (normalized != null) {
            "$normalized$SERVER_PROCESS_SUFFIX"
        } else {
            "stellar_server"
        }
    }

    val serverProcessName: String
        get() = buildServerProcessName(MANAGER_APPLICATION_ID)

    @JvmStatic
    fun main(args: Array<String>) {
        args.firstOrNull { it.startsWith(MANAGER_PACKAGE_ARG) }
            ?.substringAfter(MANAGER_PACKAGE_ARG)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { System.setProperty("stellar.manager.applicationId", it) }
        DdmHandleAppName.setAppName(serverProcessName, 0)
        Looper.prepareMainLooper()
        StellarService()
        Looper.loop()
    }

    fun waitSystemService(name: String) {
        while (ServiceManager.getService(name) == null) {
            try {
                LOGGER.i("服务 $name 尚未启动，等待 1 秒。")
                Thread.sleep(1000)
            } catch (_: InterruptedException) {
            }
        }
    }

    val managerApplicationInfo: ApplicationInfo?
        get() = PackageManagerCompat.getApplicationInfo(MANAGER_APPLICATION_ID, 0, 0)
}
