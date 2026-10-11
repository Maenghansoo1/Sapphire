package com.sapphire.hawe.feature.mission

import org.junit.Assert.*
import org.junit.Test

class PhotoProcessingTest {
    @Test fun centerCrop() {
        assertEquals(CropRect(875, 0, 2250, 3000), centerCropRect(4000, 3000))
        assertEquals(CropRect(0, 0, 3000, 4000), centerCropRect(3000, 4000))
        assertEquals(CropRect(125, 0, 750, 1000), centerCropRect(1000, 1000))
        assertEquals(CropRect(0, 0, 1, 1), centerCropRect(1, 1))
        assertEquals(CropRect(0, 0, 0, 0), centerCropRect(0, 100))
    }

    @Test fun sampleSizesArePowersOfTwo() {
        assertEquals(1, sampleSizeFor(1024, 768))
        assertEquals(2, sampleSizeFor(4096, 2048))
        assertEquals(8, sampleSizeFor(12000, 9000))
        assertEquals(1, sampleSizeFor(0, -1))
    }
}
