package com.music.bitchord.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArtworkPaletteTest {
    @Test
    fun grayscaleDoesNotAcquireTheDefaultRedHue() {
        assertEquals(0f, adaptedArtworkSaturation(0f, minimum = 0.20f, maximum = 0.62f), 0f)
        assertEquals(0f, adaptedArtworkSaturation(0f, minimum = 0.55f, maximum = 1f), 0f)
    }

    @Test
    fun nearlyNeutralArtworkKeepsItsSubtleSaturation() {
        assertEquals(0.08f, adaptedArtworkSaturation(0.08f, minimum = 0.20f, maximum = 0.62f), 0f)
    }

    @Test
    fun genuinelyChromaticArtworkIsStillStrengthenedAndCapped() {
        assertEquals(0.55f, adaptedArtworkSaturation(0.25f, minimum = 0.55f, maximum = 1f), 0f)
        assertEquals(0.62f, adaptedArtworkSaturation(0.90f, minimum = 0.20f, maximum = 0.62f), 0f)
    }

    @Test
    fun nearBlackWithATraceOfColourIsTreatedAsGrey() {
        // A sleeve that is mostly black: high HSL saturation, no visible colour.
        val swatch = Color(red = 22, green = 12, blue = 26).neutralizedIfGrey()
        assertEquals(swatch.red, swatch.green, 0.01f)
        assertEquals(swatch.green, swatch.blue, 0.01f)
    }

    @Test
    fun visiblyColouredSwatchIsLeftAlone() {
        val swatch = Color(red = 40, green = 90, blue = 60)
        assertEquals(swatch, swatch.neutralizedIfGrey())
        assertNotEquals(swatch.red, swatch.green)
    }
}
