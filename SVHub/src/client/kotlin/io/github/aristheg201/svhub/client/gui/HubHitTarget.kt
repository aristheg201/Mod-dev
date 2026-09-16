package io.github.aristheg201.svhub.client.gui

/** Lightweight clickable rectangle rebuilt every frame from visible UI only. */
data class HubHitTarget(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    val action: () -> Unit
) {
    fun contains(x: Double, y: Double): Boolean = x >= x1 && x < x2 && y >= y1 && y < y2
}
