package com.omarea.data_collection.publisher

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import com.omarea.data_collection.EventBus
import com.omarea.data_collection.EventTypes

/**
 * 监听屏幕开关事件
 * Created by Hello airModeOn 2018/01/23.
 */
class ScreenState(private var context: Context) : BroadcastReceiver() {
    private var handler = Handler()
    private var lastChange = 0L
    override fun onReceive(p0: Context?, p1: Intent?) {
        if (p1 == null) {
            return
        }
        when (p1.action) {
            Intent.ACTION_SCREEN_OFF -> {
                lastChange = System.currentTimeMillis()
                EventBus.publish(EventTypes.SCREEN_OFF)
            }
            Intent.ACTION_USER_PRESENT -> {
                lastChange = System.currentTimeMillis()
                EventBus.publish(EventTypes.SCREEN_ON)
            }
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_SCREEN_ON -> {
                val ms = System.currentTimeMillis()
                lastChange = System.currentTimeMillis()
                try {
                    handler.postDelayed({
                        if (ms == lastChange) {
                            try {
                                val mKeyguardManager = p0!!.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                                if (!(mKeyguardManager.isKeyguardLocked || mKeyguardManager.inKeyguardRestrictedInputMode())) {
                                    EventBus.publish(EventTypes.SCREEN_ON)
                                }
                            } catch (ex: Exception) {
                            }
                        }
                    }, 5500)
                } catch (ex: Exception) {
                }
            }
        }
    }

    fun autoRegister(): ScreenState {
        val c = if (context.applicationContext != null) context.applicationContext else context
        c.registerReceiver(this, IntentFilter(Intent.ACTION_SCREEN_OFF))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            c.registerReceiver(this, IntentFilter(Intent.ACTION_USER_UNLOCKED))
        }
        context.registerReceiver(this, IntentFilter(Intent.ACTION_SCREEN_ON))
        c.registerReceiver(this, IntentFilter(Intent.ACTION_USER_PRESENT))

        return this;
    }

    fun unRegister() {
        val c = if (context.applicationContext != null) context.applicationContext else context
        c.unregisterReceiver(this)
    }
}
