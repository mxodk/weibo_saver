package com.mxodk.weibosaver

import com.mxodk.weibosaver.ui.imagePanBounds
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGeometryTest {
    @Test fun portraitKeepsShortSideCenteredUntilItExceedsViewport() {
        val bounds = imagePanBounds(400, 800, 600, 1200, 2f)
        assertEquals(200f, bounds.first, .01f)
        assertEquals(400f, bounds.second, .01f)
    }
    @Test fun landscapeInPortraitViewportDoesNotPanIntoVerticalBlankSpace() {
        val bounds = imagePanBounds(400, 800, 1600, 900, 2f)
        assertEquals(200f, bounds.first, .01f)
        assertEquals(0f, bounds.second, .01f)
    }
    @Test fun tallImageInLandscapeViewportKeepsHorizontalAxisCentered() {
        val bounds = imagePanBounds(800, 400, 600, 1200, 2f)
        assertEquals(0f, bounds.first, .01f)
        assertEquals(200f, bounds.second, .01f)
    }
    @Test fun originalScaleAndUnloadedImageCannotPan() {
        assertEquals(0f to 0f, imagePanBounds(400, 800, 600, 1200, 1f))
        assertEquals(0f to 0f, imagePanBounds(400, 800, 0, 0, 3f))
        assertEquals(0f to 0f, imagePanBounds(0, 0, 600, 1200, 3f))
    }
}
