package com.vyllo.music.service.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Watches the physical output devices and reports transitions between "some
 * personal listening device is attached" (headphones, BT, USB) and "only the
 * speaker/HDMI remains".
 *
 * Only personal devices count: plugging in a USB DAC should behave like
 * plugging in headphones, while an HDMI handshake should not trigger a pause.
 */
class OutputDeviceWatcher(
    context: Context,
    private val onDisconnected: () -> Unit,
    private val onReconnected: () -> Unit
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (addedDevices.any { it.isPersonalSink() }) onReconnected()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.isPersonalSink() } && !hasPersonalSink()) {
                onDisconnected()
            }
        }
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    private fun hasPersonalSink(): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.isPersonalSink() }

    private fun AudioDeviceInfo.isPersonalSink(): Boolean =
        isSink && type in PERSONAL_SINK_TYPES

    companion object {
        private val PERSONAL_SINK_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            add(AudioDeviceInfo.TYPE_USB_DEVICE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AudioDeviceInfo.TYPE_HEARING_AID)
        }
    }
}
