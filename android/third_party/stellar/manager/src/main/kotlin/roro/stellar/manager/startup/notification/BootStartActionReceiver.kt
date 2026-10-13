package roro.stellar.manager.startup.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.receiver.StellarReceiverStarter
import roro.stellar.manager.startup.worker.AdbStartWorker
import roro.stellar.shared.StellarHostEnvironment

class BootStartActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_RETRY -> {
                        when (StellarSettings.getBootMode()) {
                            StellarSettings.BootMode.BROADCAST -> StellarReceiverStarter.start(context)
                            StellarSettings.BootMode.TCPIP_PREWARM -> AdbStartWorker.enqueuePrepareTcpip(context)
                            StellarSettings.BootMode.NONE,
                            StellarSettings.BootMode.SCRIPT -> Unit
                        }
                    }
                    ACTION_CANCEL -> {
                        AdbStartWorker.cancel(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        val ACTION_RETRY: String = StellarHostEnvironment.bootStartRetryAction
        val ACTION_CANCEL: String = StellarHostEnvironment.bootStartCancelAction
    }
}
