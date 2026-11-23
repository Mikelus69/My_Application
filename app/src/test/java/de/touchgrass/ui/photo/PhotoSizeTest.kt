package de.touchgrass.ui.photo

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoSizeTest {
    @Test
    fun `Querformat wird auf 512 Pixel Breite verkleinert`() {
        assertEquals(PhotoSize(512, 384), fitWithin(4000, 3000))
    }

    @Test
    fun `Hochformat wird auf 512 Pixel Höhe verkleinert`() {
        assertEquals(PhotoSize(384, 512), fitWithin(3000, 4000))
    }

    @Test
    fun `kleinere Fotos werden nicht vergrößert`() {
        assertEquals(PhotoSize(300, 200), fitWithin(300, 200))
    }

    @Test
    fun `genau 512 Pixel bleiben unverändert`() {
        assertEquals(PhotoSize(512, 384), fitWithin(512, 384))
    }

    @Test
    fun `sehr schmale Fotos behalten mindestens ein Pixel`() {
        assertEquals(PhotoSize(512, 1), fitWithin(10_000, 5))
    }

    @Test
    fun `Abtastrate ist die größte Zweierpotenz, bei der die längste Seite mindestens 512 bleibt`() {
        assertEquals(4, sampleSizeFor(4000, 3000))
        assertEquals(1, sampleSizeFor(1000, 800))
        assertEquals(2, sampleSizeFor(1024, 768))
    }
}
