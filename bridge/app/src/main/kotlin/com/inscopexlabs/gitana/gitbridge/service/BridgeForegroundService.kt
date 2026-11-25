package com.inscopexlabs.gitana.gitbridge.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * User-started foreground service (spec 9.1).
 *
 * TODO: choose the foreground service type for the target API level and actual use case, call
 * startForeground with a persistent notification, and declare the matching permission in the manifest.
 */
class BridgeForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
}
