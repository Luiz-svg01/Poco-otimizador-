package br.com.pocooptimizer.repository

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import br.com.pocooptimizer.domain.DeviceSnapshot

class DeviceRepository(private val context: Context) {
    fun snapshot(): DeviceSnapshot {
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val stat = StatFs(context.filesDir.absolutePath)
        val b = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        return DeviceSnapshot("${Build.MANUFACTURER} ${Build.MODEL}", "Android ${Build.VERSION.RELEASE} • API ${Build.VERSION.SDK_INT}",
            stat.totalBytes, stat.availableBytes, memory.totalMem, memory.availMem, memory.lowMemory,
            if(level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0,100) else null,
            if(status in 2..5) status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL else null,
            if(temp != Int.MIN_VALUE) temp / 10f else null,
            context.getSystemService(PowerManager::class.java).isPowerSaveMode,
            b?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN))
    }
}
