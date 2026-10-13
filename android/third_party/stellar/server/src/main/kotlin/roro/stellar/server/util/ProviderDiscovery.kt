package roro.stellar.server.util

import android.content.pm.PackageInfo

object ProviderDiscovery {
    private const val AUTHORITY_SEPARATOR = ";"
    private const val MANAGER_PROVIDER_CLASS = "roro.stellar.manager.StellarManagerProvider"

    fun hasProvider(packageInfo: PackageInfo, suffix: String): Boolean {
        val packageName = packageInfo.packageName
        val expectedAuthority = "$packageName$suffix"
        return packageInfo.providers?.any { provider ->
            provider.authority
                ?.split(AUTHORITY_SEPARATOR)
                ?.any { it.trim() == expectedAuthority } == true
        } == true
    }

    fun hasStellarProvider(packageInfo: PackageInfo): Boolean = hasProvider(packageInfo, ".stellar")

    fun hasShizukuProvider(packageInfo: PackageInfo): Boolean = hasProvider(packageInfo, ".shizuku")

    fun hasManagerProvider(packageInfo: PackageInfo): Boolean {
        val packageName = packageInfo.packageName
        val expectedAuthority = "$packageName.stellar"
        return packageInfo.providers?.any { provider ->
            provider.name == MANAGER_PROVIDER_CLASS &&
                provider.authority
                    ?.split(AUTHORITY_SEPARATOR)
                    ?.any { it.trim() == expectedAuthority } == true
        } == true
    }
}
