/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.music.bitchord.playback.audio.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Monitors USB DAC and Type-C IEM connections strictly while the app is in the foreground.
 *
 * Requirements:
 * 1. Only prompts when the app is OPEN (started / resumed), never when closed.
 * 2. Only prompts if the Direct Bit-Perfect Mode toggle is turned ON in Settings.
 * 3. On new connection (or reconnection after earphone removal/detachment where Android
 *    revokes USB host permission), automatically prompts the user for exclusive USB access.
 * 4. On permission granted, immediately triggers [PlaybackService.requestOutputReconfiguration]
 *    to promote the live audio pipeline to Bit-Perfect userspace Direct USB.
 * 5. On detachment, resets connection tracking state and reconfigures playback back to system fallback.
 */
class UsbConnectionObserver(private val activity: AppCompatActivity) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var observerScope: CoroutineScope? = null
    private var isObserving = false
    private var lastPromptedDeviceKey: String? = null
    private var isPromptPending = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                AudioManager.ACTION_HEADSET_PLUG -> {
                    Log.d(TAG, "USB/Headset attach broadcast received: ${intent.action}")
                    // Post shortly to allow Android to enumerate USB interfaces
                    mainHandler.postDelayed({ checkAndPromptPermission() }, 300)
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = extractUsbDevice(intent)
                    Log.d(TAG, "USB device detached: ${device?.productName}")
                    resetDeviceState()
                    PlaybackService.requestOutputReconfiguration(activity)
                }

                UsbDirectManager.ACTION_USB_PERMISSION -> {
                    isPromptPending = false
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val device = extractUsbDevice(intent)
                    val productName = device?.productName ?: "USB Audio DAC"
                    Log.i(TAG, "USB permission result for '$productName': granted=$granted")

                    if (granted) {
                        Toast.makeText(
                            activity,
                            "Bit-Perfect Mode: $productName authorized",
                            Toast.LENGTH_SHORT,
                        ).show()
                        PlaybackService.requestOutputReconfiguration(activity)
                    } else {
                        Log.w(TAG, "User declined exclusive USB access for '$productName'")
                    }
                }
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val hasUsb = addedDevices?.any {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            } == true

            if (hasUsb) {
                Log.d(TAG, "AudioDeviceCallback: USB audio device added")
                mainHandler.postDelayed({ checkAndPromptPermission() }, 300)
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val hasUsb = removedDevices?.any {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            } == true

            if (hasUsb) {
                Log.d(TAG, "AudioDeviceCallback: USB audio device removed")
                resetDeviceState()
                PlaybackService.requestOutputReconfiguration(activity)
            }
        }
    }

    /**
     * Call in [AppCompatActivity.onStart].
     */
    fun start() {
        if (isObserving) return
        isObserving = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        observerScope = scope

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbDirectManager.ACTION_USB_PERMISSION)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                activity,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(usbReceiver, filter)
        }

        val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

        // Observe Direct Bit-Perfect Mode toggle: if enabled while DAC is plugged in, prompt!
        scope.launch {
            AppSettings.directBitPerfectMode.collect { enabled ->
                if (enabled) {
                    mainHandler.postDelayed({ checkAndPromptPermission() }, 300)
                }
            }
        }

        // Initial check in case device was plugged in before app opened
        mainHandler.postDelayed({ checkAndPromptPermission() }, 400)
    }

    /**
     * Call in [AppCompatActivity.onStop].
     */
    fun stop() {
        if (!isObserving) return
        isObserving = false

        runCatching { activity.unregisterReceiver(usbReceiver) }
        val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        runCatching { audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback) }
        mainHandler.removeCallbacksAndMessages(null)
        observerScope?.cancel()
        observerScope = null
    }

    private fun checkAndPromptPermission() {
        if (!isObserving || activity.isFinishing || activity.isDestroyed) return

        // Requirement 2: Only ask for exclusive USB access when the Direct Bit-Perfect Mode toggle is ON
        if (!AppSettings.directBitPerfectMode.value) {
            Log.d(TAG, "Direct Bit-Perfect mode toggle is OFF; skipping exclusive USB permission prompt")
            return
        }

        val usbManager = activity.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        val device = UsbDirectManager.findFirstUsbAudioDevice(activity)

        if (device == null) {
            lastPromptedDeviceKey = null
            isPromptPending = false
            return
        }

        val deviceKey = "${device.vendorId}:${device.productId}:${device.deviceName}"

        // If permission is already granted, ensure PlaybackService knows
        if (usbManager.hasPermission(device)) {
            if (lastPromptedDeviceKey != deviceKey) {
                lastPromptedDeviceKey = deviceKey
                isPromptPending = false
                Log.i(TAG, "Exclusive USB permission already active for '${device.productName}'")
                PlaybackService.requestOutputReconfiguration(activity)
            }
            return
        }

        // Avoid repeated prompt if already prompting for this device session
        if (lastPromptedDeviceKey == deviceKey && isPromptPending) {
            return
        }

        lastPromptedDeviceKey = deviceKey
        isPromptPending = true
        val (prompted, msg) = UsbDirectManager.requestUsbPermission(activity)
        Log.i(TAG, "Requested USB exclusive access: prompted=$prompted, msg='$msg'")
        if (!prompted) {
            isPromptPending = false
        }
    }

    private fun resetDeviceState() {
        lastPromptedDeviceKey = null
        isPromptPending = false
    }

    private fun extractUsbDevice(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    companion object {
        private const val TAG = "UsbConnectionObserver"
    }
}
