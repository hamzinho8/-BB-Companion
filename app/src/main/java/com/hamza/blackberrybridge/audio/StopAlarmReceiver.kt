package com.hamza.blackberrybridge.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        SoundManager.stopFindPhone(context, notifyBlackBerry = true)
    }
}
