package com.mxodk.weibosaver.ui

// Fit 显示的图片放大后，仅允许移动超出视口的部分，窄边保持居中。
internal fun imagePanBounds(viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int,
                            zoom: Float): Pair<Float, Float> {
    if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return 0f to 0f
    val fit = minOf(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
    val horizontal = ((imageWidth * fit * zoom - viewWidth) / 2f).coerceAtLeast(0f)
    val vertical = ((imageHeight * fit * zoom - viewHeight) / 2f).coerceAtLeast(0f)
    return horizontal to vertical
}
