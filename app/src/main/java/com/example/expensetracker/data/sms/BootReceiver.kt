package com.example.expensetracker.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Static SMS BroadcastReceiver is already registered in Manifest
            // to listen for incoming messages as soon as the system boots.
        }
    }
}
