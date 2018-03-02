package com.omarea.shared

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.support.v7.app.NotificationCompat
import com.omarea.shell.SuDo
import com.omarea.vboot.R
import com.omarea.vboot.ServiceBattery

/**
 * Created by Hello on 2017/12/27.
 */

class BootService : Service() {

    private var handler = Handler()
    private lateinit var chargeConfig:SharedPreferences
    private lateinit var swapConfig:SharedPreferences
    private lateinit var globalConfig:SharedPreferences

    private fun autoBoot() {
        //判断是否开启了充电加速和充电保护，如果开启了，自动启动后台服务
        if (chargeConfig.getBoolean(SpfConfig.CHARGE_SPF_QC_BOOSTER, false) || chargeConfig.getBoolean(SpfConfig.CHARGE_SPF_BP, false)) {
            try {
                val i = Intent(this, ServiceBattery::class.java)
                this.startService(i)
            } catch (ex: Exception) {
            }
        }

        val sb = StringBuilder("setenforce 0;")

        if (globalConfig.getBoolean(SpfConfig.GLOBAL_SPF_MAC_AUTOCHANGE, false)) {
            val mac = globalConfig.getString(SpfConfig.GLOBAL_SPF_MAC, "")
            if (mac != "") {
                sb.append("chmod 0644 /sys/class/net/wlan0/address;" +
                        "svc wifi disable;" +
                        "ifconfig wlan0 down;" +
                        "echo '$mac' > /sys/class/net/wlan0/address;" +
                        "ifconfig wlan0 hw ether '$mac';" +
                        "chmod 0644 /sys/devices/soc/a000000.qcom,wcnss-wlan/wcnss_mac_addr\n" +
                        "echo '$mac' > /sys/devices/soc/a000000.qcom,wcnss-wlan/wcnss_mac_addr\n" +
                        "ifconfig wlan0 up;" +
                        "svc wifi enable;\n\n")
            }
        }

        if (swapConfig.getBoolean(SpfConfig.SWAP_SPF_SWAP, false) || swapConfig.getBoolean(SpfConfig.SWAP_SPF_ZRAM, false)) {
            if (swapConfig.getBoolean(SpfConfig.SWAP_SPF_ZRAM, false)) {
                sb.append("if [ `cat /sys/block/zram0/disksize` != '" + swapConfig.getInt(SpfConfig.SWAP_SPF_ZRAM_SIZE, 0) + "000000' ] ; then ")
                sb.append("swapoff /dev/block/zram0 >/dev/null 2>&1;")
                sb.append("echo 1 > /sys/block/zram0/reset;")
                sb.append("echo " + swapConfig.getInt(SpfConfig.SWAP_SPF_ZRAM_SIZE, 0) + "000000 > /sys/block/zram0/disksize;")
                sb.append("fi;\n")
            }
            sb.append("mkswap /dev/block/zram0 >/dev/null 2>&1;")
            sb.append("swapon /dev/block/zram0 >/dev/null 2>&1;")

            //sb.append("swapon /data/swapfile -p 32767;")
            if (swapConfig.getBoolean(SpfConfig.SWAP_SPF_SWAP_FIRST, false))
                sb.append("swapon /data/swapfile -p 32760;")
            else
                sb.append("swapon /data/swapfile;")
            sb.append("echo 3 > /sys/block/zram0/max_comp_streams;")

            sb.append("echo 65 > /proc/sys/vm/swappiness;")
            sb.append("echo " + swapConfig.getInt(SpfConfig.SWAP_SPF_SWAPPINESS, 65) + " > /proc/sys/vm/swappiness;")
        }
        sb.append("sh /data/data/me.piebridge.brevent/brevent.sh;");
        SuDo(this).execCmdSync(sb.toString())
        val nm =  getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, Notification.Builder(this).setSmallIcon(R.drawable.ic_menu_digital).setContentText("微工具箱已完成开机自启动").build())
        handler.postDelayed({
            stopSelf()
        }, 2000)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        chargeConfig = this.getSharedPreferences(SpfConfig.CHARGE_SPF, Context.MODE_PRIVATE)
        swapConfig = this.getSharedPreferences(SpfConfig.SWAP_SPF, Context.MODE_PRIVATE)
        globalConfig = getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)

        if (globalConfig.getBoolean(SpfConfig.GLOBAL_SPF_START_DELAY, false)) {
            handler.postDelayed({
                autoBoot()
            }, 25000)
        } else {
            handler.postDelayed({
                autoBoot()
            }, 1000)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
