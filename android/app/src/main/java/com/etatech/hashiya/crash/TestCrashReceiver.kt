package com.etatech.hashiya.crash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** `adb shell am broadcast -a com.etatech.hashiya.TEST_CRASH -p com.etatech.hashiya` crashes the app on purpose, to check reporting. */
class TestCrashReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Posted, so the crash takes the app down like a real one rather than being swallowed by the receiver dispatch.
        Handler(Looper.getMainLooper()).post { throw RuntimeException("Test crash requested over adb") }
    }
}
