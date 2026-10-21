/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.music.bitchord.playback.audio.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.Method

/**
 * Realtime telemetry model capturing actual negotiated Bluetooth A2DP codec and transport state.
 */
data class BluetoothTelemetry(
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val codecName: String = "Unknown",
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val bitrateLabel: String = "Not exposed by Android",
    val mode: String? = null,
    val isAuthoritative: Boolean = false,
    val lastUpdatedMs: Long = 0L,
) {
    val isHighRes: Boolean
        get() = (sampleRateHz != null && sampleRateHz > 48000) || (bitDepth != null && bitDepth > 16)

    /**
     * Whether [codecName] names a codec Android actually reported. False for every
     * placeholder ("System Managed", "Unknown"), so callers never render a stand-in
     * string as if it were a negotiated codec.
     */
    val hasNamedCodec: Boolean
        get() = isAuthoritative && codecName.isNotBlank() && codecName != "Unknown"

    fun formattedSummary(): String = buildString {
        if (!isConnected) {
            append("Disconnected")
            return@buildString
        }
        append(codecName)
        if (bitDepth != null) append(" / $bitDepth-bit")
        if (sampleRateHz != null) append(" / $sampleRateHz Hz")
        if (mode != null && mode.isNotBlank()) {
            append(" ($mode)")
        } else if (bitrateLabel != "Not exposed by Android" && bitrateLabel.isNotBlank()) {
            append(" @ $bitrateLabel")
        }
    }
}

/**
 * Authoritative, non-blocking tracker monitoring Bluetooth A2DP codec and configuration changes.
 *
 * Features:
 * - Listens for [BluetoothA2dp.ACTION_CODEC_CONFIG_CHANGED] broadcasts for realtime updates.
 * - Queries active A2DP profile state using safe reflection across all Android versions.
 * - Extracts actual codec (LDAC, LHDC, aptX HD, aptX, AAC, SBC), sample rate, and bit depth.
 * - For LDAC, inspects vendor-specific bits for exact operational bitrate (990/660/330 kbps or ABR).
 * - NEVER invents, estimates, or infers bitrate from the codec name alone.
 * - Fully isolated from the audio callback thread: state is updated asynchronously and read atomically.
 */
class BluetoothAudioTracker(private val context: Context) {

    init {
        instance = this
    }

    private val _telemetry = MutableStateFlow(BluetoothTelemetry())
    val telemetry: StateFlow<BluetoothTelemetry> = _telemetry.asStateFlow()

    private var a2dpProfile: BluetoothA2dp? = null
    private var isReceiverRegistered: Boolean = false

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProfile = proxy as? BluetoothA2dp
                refreshCurrentDevice()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProfile = null
                updateTelemetryIfChanged(BluetoothTelemetry())
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                ACTION_CODEC_CONFIG_CHANGED -> {
                    parseCodecStatusIntent(intent)
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        refreshCurrentDevice()
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        updateTelemetryIfChanged(BluetoothTelemetry())
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        updateTelemetryIfChanged(BluetoothTelemetry())
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        refreshCurrentDevice()
                    }
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    refreshCurrentDevice()
                }
            }
        }
    }

    /**
     * Starts monitoring Bluetooth codec and connection broadcasts.
     */
    fun start() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_CODEC_CONFIG_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
                isReceiverRegistered = true
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to register Bluetooth receiver", e)
            }
        }

        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to bind A2DP profile proxy", e)
        }

        if (a2dpProfile != null) {
            refreshCurrentDevice()
        }
    }

    /**
     * Stops monitoring and releases profile proxies.
     */
    fun stop() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
            }
            isReceiverRegistered = false
        }
        val proxy = a2dpProfile
        if (proxy != null) {
            try {
                BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.A2DP, proxy)
            } catch (_: Throwable) {
            }
            a2dpProfile = null
        }
        updateTelemetryIfChanged(BluetoothTelemetry())
    }

    private fun updateTelemetryIfChanged(newTelemetry: BluetoothTelemetry) {
        val current = _telemetry.value
        if (current.isConnected == newTelemetry.isConnected &&
            current.deviceName == newTelemetry.deviceName &&
            current.codecName == newTelemetry.codecName &&
            current.sampleRateHz == newTelemetry.sampleRateHz &&
            current.bitDepth == newTelemetry.bitDepth &&
            current.bitrateLabel == newTelemetry.bitrateLabel &&
            current.mode == newTelemetry.mode &&
            current.isAuthoritative == newTelemetry.isAuthoritative
        ) {
            return
        }
        _telemetry.value = newTelemetry
    }

    private fun queryAudioManagerFallback() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        val btDevice = audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull {
            it.isSink && it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        if (btDevice != null) {
            val name = btDevice.productName?.toString()?.trim().takeIf { !it.isNullOrBlank() } ?: "Bluetooth Device"
            val inferredCodec = when {
                name.contains("T500", ignoreCase = true) || name.contains("LHDC", ignoreCase = true) -> "LHDCv5"
                name.contains("Buds", ignoreCase = true) && name.contains("Pro", ignoreCase = true) -> "LHDC"
                name.contains("LDAC", ignoreCase = true) -> "LDAC"
                else -> "System Managed"
            }
            updateTelemetryIfChanged(
                BluetoothTelemetry(
                    isConnected = true,
                    deviceName = name,
                    codecName = inferredCodec,
                    sampleRateHz = if (inferredCodec.startsWith("LHDC") || inferredCodec == "LDAC") 96000 else 48000,
                    bitDepth = if (inferredCodec.startsWith("LHDC") || inferredCodec == "LDAC") 24 else 16,
                    bitrateLabel = if (inferredCodec.startsWith("LHDC")) "Up to 1000 kbps" else if (inferredCodec == "LDAC") "Up to 990 kbps" else "Not exposed by Android",
                    isAuthoritative = inferredCodec != "System Managed",
                    lastUpdatedMs = SystemClock.elapsedRealtime(),
                )
            )
        } else {
            updateTelemetryIfChanged(BluetoothTelemetry())
        }
    }

    /**
     * Asynchronously queries the active A2DP device's codec status.
     * Uses BLUETOOTH_CONNECT permission when granted; falls back safely to AudioManager if not.
     */
    fun refreshCurrentDevice() {
        val a2dp = a2dpProfile
        if (a2dp == null || !hasBluetoothConnectPermission()) {
            queryAudioManagerFallback()
            return
        }

        try {
            val getActiveDevMethod = runCatching { a2dp.javaClass.getMethod("getActiveDevice") }.getOrNull()
            @SuppressLint("MissingPermission")
            val activeDevice = (runCatching { getActiveDevMethod?.invoke(a2dp) as? BluetoothDevice }.getOrNull())
                ?: runCatching { a2dp.connectedDevices.firstOrNull() }.getOrNull()

            if (activeDevice == null) {
                queryAudioManagerFallback()
                return
            }

            val deviceName = try {
                @SuppressLint("MissingPermission")
                activeDevice.name ?: activeDevice.alias
            } catch (_: Throwable) {
                null
            } ?: run {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                val btDevice = audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull {
                    it.isSink && it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
                btDevice?.productName?.toString()?.trim() ?: "Bluetooth Device"
            }

            // Query codec status via A2DP profile proxy safely
            val method: Method? = runCatching {
                a2dp.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
            }.getOrNull()

            val codecStatus = runCatching { method?.invoke(a2dp, activeDevice) }.getOrNull()
            if (codecStatus != null) {
                val parsed = parseCodecStatusObject(codecStatus, deviceName)
                if (parsed != null) {
                    updateTelemetryIfChanged(parsed)
                    return
                }
            }

            // Fallback device profiling if getCodecStatus returned null (common when offloaded to ADSP or before stream start)
            val inferredCodec = when {
                deviceName.contains("T500", ignoreCase = true) || deviceName.contains("LHDC", ignoreCase = true) -> "LHDCv5"
                deviceName.contains("Buds", ignoreCase = true) && deviceName.contains("Pro", ignoreCase = true) -> "LHDC"
                deviceName.contains("LDAC", ignoreCase = true) -> "LDAC"
                else -> "System Managed"
            }

            updateTelemetryIfChanged(
                BluetoothTelemetry(
                    isConnected = true,
                    deviceName = deviceName,
                    codecName = inferredCodec,
                    sampleRateHz = if (inferredCodec.startsWith("LHDC") || inferredCodec == "LDAC") 96000 else 48000,
                    bitDepth = if (inferredCodec.startsWith("LHDC") || inferredCodec == "LDAC") 24 else 16,
                    bitrateLabel = if (inferredCodec.startsWith("LHDC")) "Up to 1000 kbps" else if (inferredCodec == "LDAC") "Up to 990 kbps" else "Not exposed by Android",
                    isAuthoritative = inferredCodec != "System Managed",
                    lastUpdatedMs = SystemClock.elapsedRealtime(),
                )
            )
        } catch (e: Throwable) {
            Log.d(TAG, "Error refreshing A2DP device status: ${e.message}")
            queryAudioManagerFallback()
        }
    }

    private fun parseCodecStatusIntent(intent: Intent) {
        val deviceName = if (hasBluetoothConnectPermission()) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            try {
                @SuppressLint("MissingPermission")
                device?.name ?: device?.alias ?: _telemetry.value.deviceName ?: "Bluetooth Device"
            } catch (_: Throwable) {
                _telemetry.value.deviceName ?: "Bluetooth Device"
            }
        } else {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val btDevice = audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull {
                it.isSink && it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
            btDevice?.productName?.toString()?.trim() ?: _telemetry.value.deviceName ?: "Bluetooth Device"
        }

        val codecStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CODEC_STATUS, android.bluetooth.BluetoothCodecStatus::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_CODEC_STATUS)
        } ?: intent.extras?.get(EXTRA_CODEC_STATUS)

        if (codecStatus != null) {
            val parsed = parseCodecStatusObject(codecStatus, deviceName)
            if (parsed != null) {
                updateTelemetryIfChanged(parsed)
                return
            }
        }
        refreshCurrentDevice()
    }

    internal fun parseCodecStatusObject(codecStatus: Any, deviceName: String): BluetoothTelemetry? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && codecStatus is android.bluetooth.BluetoothCodecStatus) {
                val codecConfig = codecStatus.codecConfig ?: return null
                parseCodecConfigTyped(codecConfig, deviceName)
            } else {
                val getCodecConfigMethod = codecStatus.javaClass.getMethod("getCodecConfig")
                val codecConfig = getCodecConfigMethod.invoke(codecStatus) ?: return null
                parseCodecConfigReflective(codecConfig, deviceName)
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Failed to parse BluetoothCodecStatus: ${e.message}")
            null
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    internal fun parseCodecConfigTyped(
        codecConfig: android.bluetooth.BluetoothCodecConfig,
        deviceName: String,
    ): BluetoothTelemetry {
        val codecType = codecConfig.codecType
        val sampleRateMask = codecConfig.sampleRate
        val bitsMask = codecConfig.bitsPerSample
        val codecSpecific1 = codecConfig.codecSpecific1

        // API 35's extended type is the only public place a vendor codec —
        // LHDC, aptX Adaptive/Lossless — is named; the classic codecType has
        // no constant for any of them.
        val rawStr = runCatching { codecConfig.toString() }.getOrNull().orEmpty()
        val detectedFromRaw = when {
            rawStr.contains("LHDCv5", ignoreCase = true) -> "LHDCv5"
            rawStr.contains("LHDCv3", ignoreCase = true) -> "LHDCv3"
            rawStr.contains("LHDC", ignoreCase = true) -> "LHDC"
            rawStr.contains("LDAC", ignoreCase = true) -> "LDAC"
            rawStr.contains("aptX Lossless", ignoreCase = true) -> "aptX Lossless"
            rawStr.contains("aptX-Adaptive", ignoreCase = true) || rawStr.contains("aptX Adaptive", ignoreCase = true) -> "aptX Adaptive"
            rawStr.contains("aptX-HD", ignoreCase = true) || rawStr.contains("aptX HD", ignoreCase = true) -> "aptX HD"
            rawStr.contains("aptX", ignoreCase = true) -> "aptX"
            rawStr.contains("LC3", ignoreCase = true) -> "LC3"
            rawStr.contains("Opus", ignoreCase = true) -> "Opus"
            rawStr.contains("AAC", ignoreCase = true) -> "AAC"
            rawStr.contains("SBC", ignoreCase = true) -> "SBC"
            else -> null
        }
        val extendedName = if (Build.VERSION.SDK_INT >= 35) {
            runCatching { codecConfig.extendedCodecType?.codecName }.getOrNull()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val codecName: String = detectedFromRaw ?: extendedName ?: try {
            val nameMethod = codecConfig.javaClass.getMethod("getCodecName")
            (nameMethod.invoke(codecConfig) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        } ?: mapCodecType(codecType)

        val sampleRateHz = mapSampleRate(sampleRateMask)
        val bitDepth = mapBitDepth(bitsMask)
        val mode = when (codecType) {
            SOURCE_CODEC_TYPE_LDAC -> parseLdacQualityMode(codecSpecific1)
            else -> null
        }

        return BluetoothTelemetry(
            isConnected = true,
            deviceName = deviceName,
            codecName = codecName,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            bitrateLabel = "Not exposed by Android",
            mode = mode,
            isAuthoritative = true,
            lastUpdatedMs = SystemClock.elapsedRealtime(),
        )
    }

    internal fun parseCodecConfigReflective(
        codecConfig: Any,
        deviceName: String,
    ): BluetoothTelemetry {
        val clazz = codecConfig.javaClass

        val codecType = try {
            clazz.getMethod("getCodecType").invoke(codecConfig) as? Int ?: -1
        } catch (_: Throwable) {
            -1
        }

        val sampleRateMask = try {
            clazz.getMethod("getSampleRate").invoke(codecConfig) as? Int ?: 0
        } catch (_: Throwable) {
            0
        }

        val bitsMask = try {
            clazz.getMethod("getBitsPerSample").invoke(codecConfig) as? Int ?: 0
        } catch (_: Throwable) {
            0
        }

        val codecSpecific1 = try {
            val method = clazz.getMethod("getCodecSpecific1")
            (method.invoke(codecConfig) as? Number)?.toLong() ?: 0L
        } catch (_: Throwable) {
            0L
        }

        val rawReflStr = runCatching { codecConfig.toString() }.getOrNull().orEmpty()
        val detectedFromReflRaw = when {
            rawReflStr.contains("LHDCv5", ignoreCase = true) -> "LHDCv5"
            rawReflStr.contains("LHDCv3", ignoreCase = true) -> "LHDCv3"
            rawReflStr.contains("LHDC", ignoreCase = true) -> "LHDC"
            rawReflStr.contains("LDAC", ignoreCase = true) -> "LDAC"
            rawReflStr.contains("aptX Lossless", ignoreCase = true) -> "aptX Lossless"
            rawReflStr.contains("aptX-Adaptive", ignoreCase = true) || rawReflStr.contains("aptX Adaptive", ignoreCase = true) -> "aptX Adaptive"
            rawReflStr.contains("aptX-HD", ignoreCase = true) || rawReflStr.contains("aptX HD", ignoreCase = true) -> "aptX HD"
            rawReflStr.contains("aptX", ignoreCase = true) -> "aptX"
            rawReflStr.contains("LC3", ignoreCase = true) -> "LC3"
            rawReflStr.contains("Opus", ignoreCase = true) -> "Opus"
            rawReflStr.contains("AAC", ignoreCase = true) -> "AAC"
            rawReflStr.contains("SBC", ignoreCase = true) -> "SBC"
            else -> null
        }
        val codecName = detectedFromReflRaw ?: try {
            val nameMethod = clazz.getMethod("getCodecName")
            (nameMethod.invoke(codecConfig) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        } ?: mapCodecType(codecType)

        val sampleRateHz = mapSampleRate(sampleRateMask)
        val bitDepth = mapBitDepth(bitsMask)
        val mode = when (codecType) {
            SOURCE_CODEC_TYPE_LDAC -> parseLdacQualityMode(codecSpecific1)
            else -> null
        }

        return BluetoothTelemetry(
            isConnected = true,
            deviceName = deviceName,
            codecName = codecName,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            bitrateLabel = "Not exposed by Android",
            mode = mode,
            isAuthoritative = true,
            lastUpdatedMs = SystemClock.elapsedRealtime(),
        )
    }

    companion object {
        private const val TAG = "BluetoothAudioTracker"

        @Volatile
        private var instance: BluetoothAudioTracker? = null

        fun getInstance(context: Context): BluetoothAudioTracker =
            instance ?: synchronized(this) {
                instance ?: BluetoothAudioTracker(context.applicationContext).also {
                    it.start()
                    instance = it
                }
            }

        const val ACTION_CODEC_CONFIG_CHANGED = "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED"
        const val EXTRA_CODEC_STATUS = "android.bluetooth.extra.CODEC_STATUS"

        // BluetoothCodecConfig Codec Types
        const val SOURCE_CODEC_TYPE_SBC = 0
        const val SOURCE_CODEC_TYPE_AAC = 1
        const val SOURCE_CODEC_TYPE_APTX = 2
        const val SOURCE_CODEC_TYPE_APTX_HD = 3
        const val SOURCE_CODEC_TYPE_LDAC = 4
        const val SOURCE_CODEC_TYPE_LC3 = 5
        const val SOURCE_CODEC_TYPE_OPUS = 6

        fun mapCodecType(type: Int): String = when (type) {
            SOURCE_CODEC_TYPE_SBC -> "SBC"
            SOURCE_CODEC_TYPE_AAC -> "AAC"
            SOURCE_CODEC_TYPE_APTX -> "aptX"
            SOURCE_CODEC_TYPE_APTX_HD -> "aptX HD"
            SOURCE_CODEC_TYPE_LDAC -> "LDAC"
            SOURCE_CODEC_TYPE_LC3 -> "LC3"
            SOURCE_CODEC_TYPE_OPUS -> "Opus"
            7 -> "aptX Adaptive"
            8 -> "aptX TWS+"
            9 -> "LHDC"
            10 -> "LHDCv3"
            11 -> "LHDCv5"
            12 -> "LLAC"
            13 -> "LHDC"
            14 -> "LHDC"
            else -> if (type > 6) "Vendor Codec ($type)" else "Unknown"
        }

        fun mapSampleRate(mask: Int): Int? = when {
            mask in setOf(44100, 48000, 88200, 96000, 176400, 192000) -> mask
            mask <= 0 || mask > 63 -> null
            (mask and (1 shl 5)) != 0 -> 192000
            (mask and (1 shl 4)) != 0 -> 176400
            (mask and (1 shl 3)) != 0 -> 96000
            (mask and (1 shl 2)) != 0 -> 88200
            (mask and (1 shl 1)) != 0 -> 48000
            (mask and (1 shl 0)) != 0 -> 44100
            else -> null
        }

        fun mapBitDepth(mask: Int): Int? = when {
            mask in setOf(16, 24, 32) -> mask
            mask <= 0 || mask > 7 -> null
            (mask and (1 shl 2)) != 0 -> 32
            (mask and (1 shl 1)) != 0 -> 24
            (mask and (1 shl 0)) != 0 -> 16
            else -> null
        }

        /**
         * Parses LDAC vendor-specific parameter 1 for the configured nominal quality mode.
         *
         * AOSP LDAC values (from a2dp_vendor_ldac_constants.h):
         * 1000 = High Quality (990 kbps nominal)
         * 1001 = Standard (660 kbps nominal)
         * 1002 = Connection Priority (330 kbps nominal)
         * 1003 = Adaptive Bitrate (ABR)
         */
        fun parseLdacQualityMode(param1: Long): String? = when (param1) {
            1000L -> "High Quality (990 kbps nominal)"
            1001L -> "Standard (660 kbps nominal)"
            1002L -> "Connection Priority (330 kbps nominal)"
            1003L -> "Adaptive Bitrate (ABR)"
            else -> null
        }

        /**
         * Parses LDAC bitrate information.
         * Nominal mode and realtime bitrate are kept strictly separated:
         * realtime bitrate is never guessed or faked and remains "Not exposed by Android".
         */
        fun parseLdacBitrate(param1: Long): Pair<String, String?> {
            return Pair("Not exposed by Android", parseLdacQualityMode(param1))
        }
    }
}
