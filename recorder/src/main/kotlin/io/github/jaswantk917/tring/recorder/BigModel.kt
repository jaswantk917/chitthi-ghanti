package io.github.jaswantk917.tring.recorder

import android.app.ActivityManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager

/**
 * Whether the clearer model can take a finished call by itself. It holds over 1 GB and runs the
 * fast cores, so it only starts with room to spare and never beside another call.
 */
object BigModel {
    const val LEAST_FREE_RAM = 2_500_000_000L
    /** longer calls keep the phone hot for too long without anyone asking for it */
    const val LONGEST_CALL_MS = 20 * 60_000L

    fun hasRoom(freeRam: Long, lowMemory: Boolean, thermal: Int, batterySaver: Boolean, onCall: Boolean, callMs: Long) =
        freeRam >= LEAST_FREE_RAM && !lowMemory && thermal < PowerManager.THERMAL_STATUS_MODERATE &&
            !batterySaver && !onCall && callMs <= LONGEST_CALL_MS

    fun canTake(context: Context, callMs: Long): Boolean {
        if (!IndicModel.ready(context) || !IndicModel.runsHere(context)) return false
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val power = context.getSystemService(PowerManager::class.java)
        val onCall = context.getSystemService(AudioManager::class.java).mode != AudioManager.MODE_NORMAL
        return hasRoom(memory.availMem, memory.lowMemory, power.currentThermalStatus, power.isPowerSaveMode, onCall, callMs)
    }
}
