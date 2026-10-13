package roro.stellar.shared

object StellarHostEnvironment {
    private const val OVERRIDE_MANAGER_APPLICATION_ID = "stellar.manager.applicationId"

    private fun normalizeRuntimePackageName(candidate: String?): String? {
        return candidate
            ?.substringBefore(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() && '.' in it }
    }

    private fun resolveRuntimeApplicationId(): String? {
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThreadClass
                .getMethod("currentApplication")
                .invoke(null) as? android.app.Application
            normalizeRuntimePackageName(currentApplication?.packageName)
                ?: normalizeRuntimePackageName(
                    activityThreadClass.getMethod("currentPackageName").invoke(null) as? String
                )
                ?: normalizeRuntimePackageName(
                    activityThreadClass.getMethod("currentProcessName").invoke(null) as? String
                )
        }.getOrNull()
    }

    private val configuredDefaultAllowedPackages: Set<String> =
        BuildConfig.STELLAR_DEFAULT_ALLOWED_PACKAGES
            .split(',')
            .mapNotNull { packageName ->
                packageName.trim().takeIf { it.isNotEmpty() }
            }
            .toSet()

    val managerApplicationId: String
        get() = normalizeRuntimePackageName(System.getProperty(OVERRIDE_MANAGER_APPLICATION_ID))
            ?: resolveRuntimeApplicationId()
            ?: BuildConfig.STELLAR_MANAGER_APPLICATION_ID

    val defaultAllowedPackages: Set<String>
        get() = buildSet {
            addAll(configuredDefaultAllowedPackages)
            add(managerApplicationId)
        }

    val binderExtra: String
        get() = "$managerApplicationId.intent.extra.BINDER"

    val clientBinderExtra: String
        get() = "$managerApplicationId.intent.extra.CLIENT_BINDER"

    val requestPermissionAction: String
        get() = "$managerApplicationId.intent.action.REQUEST_PERMISSION"

    val managerProviderAuthority: String
        get() = "$managerApplicationId.stellar"

    val bootStartRetryAction: String
        get() = "$managerApplicationId.action.BOOT_START_RETRY"

    val bootStartCancelAction: String
        get() = "$managerApplicationId.action.BOOT_START_CANCEL"
}
