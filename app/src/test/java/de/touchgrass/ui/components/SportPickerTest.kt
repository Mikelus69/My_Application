package de.touchgrass.ui.components

import de.touchgrass.data.dto.SportDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SportPickerTest {
    private val sports = listOf("Wandern", "Fußball", "badminton", "Rugby", "Rückenschwimmen", "Rudern", "Basketball", "Joggen")
        .map { SportDto(code = it.uppercase(), name = it, emoji = "🏅", met = 5.0) }

    private fun List<SportDto>.names() = map { it.name }

    @Test
    fun `ohne Suchtext stehen alle Sportarten von A bis Z, Umlaute wie im Deutschen`() {
        assertEquals(
            listOf("badminton", "Basketball", "Fußball", "Joggen", "Rückenschwimmen", "Rudern", "Rugby", "Wandern"),
            sports.search("").names(),
        )
    }

    @Test
    fun `die Suche findet Treffer überall im Namen`() {
        assertEquals(listOf("Basketball", "Fußball"), sports.search("ball").names())
    }

    @Test
    fun `Groß- und Kleinschreibung, Umlaute, ß und Leerzeichen am Rand zählen nicht`() {
        for (query in listOf(" FUSS ", "fuß", "Fussball")) {
            assertEquals(listOf("Fußball"), sports.search(query).names())
        }
        assertEquals(listOf("Rückenschwimmen"), sports.search("ruck").names())
    }

    @Test
    fun `ohne Treffer bleibt die Liste leer`() {
        assertEquals(emptyList<String>(), sports.search("Curling").names())
    }
}
