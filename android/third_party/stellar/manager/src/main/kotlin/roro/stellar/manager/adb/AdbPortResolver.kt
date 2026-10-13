package roro.stellar.manager.adb

import roro.stellar.manager.StellarSettings
import roro.stellar.manager.StellarSettings.TCPIP_PORT
import roro.stellar.manager.StellarSettings.TCPIP_PORT_ENABLED
import roro.stellar.manager.util.EnvironmentUtils

object AdbPortResolver {
    fun hasSavedPortCandidate(): Boolean {
        val preferences = StellarSettings.getPreferences()
        val customPortEnabled = preferences.getBoolean(TCPIP_PORT_ENABLED, true)
        val customPort = preferences.getString(TCPIP_PORT, "")?.toIntOrNull()
        return (customPortEnabled && customPort in 1..65535) || EnvironmentUtils.getAdbTcpPort() > 0
    }

    suspend fun resolveReachablePort(host: String = "127.0.0.1"): Int {
        val preferences = StellarSettings.getPreferences()
        val customPortEnabled = preferences.getBoolean(TCPIP_PORT_ENABLED, true)
        val customPort = preferences.getString(TCPIP_PORT, "")?.toIntOrNull()
        val adbWirelessHelper = AdbWirelessHelper()

        if (
            customPortEnabled &&
            customPort != null &&
            customPort in 1..65535 &&
            adbWirelessHelper.hasAdbPermission(host, customPort)
        ) {
            return customPort
        }

        val systemPort = EnvironmentUtils.getAdbTcpPort()
        if (systemPort in 1..65535 && adbWirelessHelper.hasAdbPermission(host, systemPort)) {
            return systemPort
        }

        return -1
    }
}
