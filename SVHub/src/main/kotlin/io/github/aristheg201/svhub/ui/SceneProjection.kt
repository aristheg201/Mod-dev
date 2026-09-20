package io.github.aristheg201.svhub.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SceneCameraPreset(
    val id: String,
    val tileScale: Float = 1f,
    val verticalScale: Float = 1f,
    val originBiasY: Float = 0.34f,
    val pitch: Float = 34f,
    val modelZoom: Float = 1f,
    val depthBase: Double = 1000.0,
    val depthStride: Double = 3.0,
    val perspective:Boolean=false,
    val position:SceneVec3=SceneVec3(3.0,-7.0,9.0),
    val target:SceneVec3=SceneVec3(3.0,3.5,0.0),
    val fov:Double=48.0,
    val near:Double=.1,
    val far:Double=100.0
) {
    init {
        require(id.isNotBlank())
        require(tileScale in 0.5f..1.25f)
        require(verticalScale in 0.5f..1.25f)
        require(originBiasY in 0f..1f)
        require(pitch in 0f..75f)
        require(modelZoom in 0.5f..1.5f)
        require(depthStride > 0.0)
    }
}

object SceneCameras {
    val BOARD = SceneCameraPreset("board")
    val XIANGQI = SceneCameraPreset("xiangqi", tileScale = 0.94f, verticalScale = 0.94f, originBiasY = 0.30f, pitch = 32f, modelZoom = 0.94f)
    val LANE = SceneCameraPreset("lane", tileScale = 0.88f, verticalScale = 0.90f, originBiasY = 0.27f, pitch = 30f, modelZoom = 0.92f)
    val LUDO = SceneCameraPreset("ludo", tileScale = 0.92f, verticalScale = 1.0f, originBiasY = 0.31f, pitch = 33f, modelZoom = 0.92f)
    val TFT = SceneCameraPreset("tft", tileScale = 0.94f, verticalScale = 0.92f, originBiasY = 0.27f, pitch = 31f, modelZoom = 0.95f, depthStride = 4.0,perspective=true)
}

data class SceneProjectedPoint(val x: Float, val y: Float)

data class SceneProjectionMetrics(
    val area: UiRect,
    val columns: Int,
    val rows: Int,
    val originX: Float,
    val originY: Float,
    val tileWidth: Int,
    val tileHeight: Int,
    val camera: SceneCameraPreset,
    val perspective:PerspectiveBoardTransform?
) {
    fun project(x: Float, y: Float): SceneProjectedPoint = perspective?.project(SceneVec3(x.toDouble(),y.toDouble(),0.0))?:SceneProjectedPoint(
        originX + (x - y) * tileWidth * 0.5f,
        originY + (x + y) * tileHeight * 0.5f
    )

    fun depthFor(x: Float, y: Float): Double =
        camera.depthBase + (y * columns + x) * camera.depthStride
}

object SceneProjection {
    fun resolve(area: UiRect, columns: Int, rows: Int, camera: SceneCameraPreset): SceneProjectionMetrics {
        require(columns > 0 && rows > 0)
        val span = (columns + rows).coerceAtLeast(2)
        val widthBound = ((area.width - 12).coerceAtLeast(24) * 2 / span).coerceAtLeast(10)
        val heightBound = ((area.height - 28).coerceAtLeast(20) * 4 / span).coerceAtLeast(10)
        val baseTile = min(72, min(widthBound, heightBound)).coerceAtLeast(10)
        val tileW = (baseTile * camera.tileScale).roundToInt().coerceAtLeast(10)
        val tileH = max(7, (tileW * 0.5f * camera.verticalScale).roundToInt())
        val boardHeight = span * tileH / 2
        return SceneProjectionMetrics(
            area = area,
            columns = columns,
            rows = rows,
            originX = area.x + area.width / 2f,
            originY = area.y + max(tileH / 2f + 5f, (area.height - boardHeight) * camera.originBiasY),
            tileWidth = tileW,
            tileHeight = tileH,
            camera = camera,
            perspective = camera.takeIf{it.perspective}?.let{PerspectiveBoardTransform(area,it.position,it.target,it.fov,it.near,it.far)}
        )
    }
}
