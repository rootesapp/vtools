package com.omarea.shell_utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.omarea.common.shell.KeepShellPublic

// 飞行模式
class NetworkUtils(private val context: Context) {
    fun mobileDataOn() {
        KeepShellPublic.doCmdSync("svc data enable")
    }

    fun mobileDataOff() {
        KeepShellPublic.doCmdSync("svc data disable")
    }

    fun wifiOn() {
        KeepShellPublic.doCmdSync("svc wifi enable")
    }

    fun wifiOff() {
        KeepShellPublic.doCmdSync("svc wifi disable")
    }



    /*
    // <uses-permission android:name="android.permission.MODIFY_PHONE_STATE" />
    fun setMobileDataState(context: Context, enabled: Boolean) {
        val telephonyService = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            val setDataEnabled = telephonyService.javaClass.getDeclaredMethod("setDataEnabled", Boolean::class.javaPrimitiveType!!)
            setDataEnabled.invoke(telephonyService, enabled)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    fun getMobileDataState(context: Context): Boolean {
        val telephonyService = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            val getDataEnabled = telephonyService.javaClass.getDeclaredMethod("getDataEnabled")
            return getDataEnabled.invoke(telephonyService) as Boolean
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }
    */

    private val COMMAND_AIRPLANE_ON = "settings put global airplane_mode_on 1 \n " + "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true\n "
    private val COMMAND_AIRPLANE_OFF = "settings put global airplane_mode_on 0 \n" + " am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false\n "

    fun airModeOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
            Settings.Global.putInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1);
        } else {
            KeepShellPublic.doCmdSync(COMMAND_AIRPLANE_ON)
        }
    }

    fun airModeOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
            Settings.Global.putInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
        } else {
            KeepShellPublic.doCmdSync(COMMAND_AIRPLANE_OFF)
        }
    }
}
