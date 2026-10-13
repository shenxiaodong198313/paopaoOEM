package roro.stellar.server

import roro.stellar.shared.StellarHostEnvironment

object ServerConstants {
    const val MANAGER_APP_NOT_FOUND: Int = 50

    val MANAGER_APPLICATION_ID: String = StellarHostEnvironment.managerApplicationId

    val REQUEST_PERMISSION_ACTION: String
        get() = StellarHostEnvironment.requestPermissionAction

    const val BINDER_TRANSACTION_getApplications: Int = 10001
}
