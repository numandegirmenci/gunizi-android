package com.numandegirmenci.belgeizi.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Size

/**
 * Reports JPEG resolutions actually advertised to third-party Android apps.
 * The physical sensor marketing resolution is not a substitute for this map.
 */
object CameraCapabilities {
    data class Info(
        val id: String,
        val normal: Size?,
        val ultra: Size?,
        val hasMaximumResolutionMode: Boolean
    ) {
        val maximum: Size? get() = listOfNotNull(normal, ultra).maxByOrNull(::pixels)
        val megapixels: Double get() = (maximum?.let(::pixels) ?: 0L) / 1_000_000.0
        val canRequest200Mp: Boolean get() =
            hasMaximumResolutionMode && ultra != null && pixels(ultra) >= 195_000_000L
    }

    fun pixels(size: Size): Long = size.width.toLong() * size.height.toLong()

    fun backCameras(context: Context): List<Info> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return manager.cameraIdList.mapNotNull { id ->
            runCatching {
                val c = manager.getCameraCharacteristics(id)
                if (c.get(CameraCharacteristics.LENS_FACING) !=
                    CameraCharacteristics.LENS_FACING_BACK) return@runCatching null
                val ordinary = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.JPEG)
                    ?.maxByOrNull(::pixels)
                val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.toSet().orEmpty()
                val maximumMode = Build.VERSION.SDK_INT >= 31 &&
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR
                    in capabilities
                val ultra = if (Build.VERSION.SDK_INT >= 31 && maximumMode) {
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                        ?.getOutputSizes(ImageFormat.JPEG)
                        ?.maxByOrNull(::pixels)
                } else null
                Info(id, ordinary, ultra, maximumMode)
            }.getOrNull()
        }.sortedByDescending { it.megapixels }
    }

    fun infoFor(context: Context, cameraId: String): Info? =
        backCameras(context).firstOrNull { it.id == cameraId }
}
