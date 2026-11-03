package de.touchgrass.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import de.touchgrass.data.dto.SportDto
import de.touchgrass.ui.activities.label
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

private val accents = Regex("\\p{Mn}+")

private fun String.folded(): String =
    Normalizer.normalize(trim().lowercase().replace("ß", "ss"), Normalizer.Form.NFD).replace(accents, "")

/** Sportarten nach deutschem Alphabet, die den Suchtext irgendwo im Namen enthalten. Groß- und Kleinschreibung, Umlaute und ß zählen nicht. */
fun List<SportDto>.search(query: String): List<SportDto> {
    val wanted = query.folded()
    return filter { wanted in it.name.folded() }.sortedWith(compareBy(Collator.getInstance(Locale.GERMAN)) { it.name })
}

/** Mit [allLabel] steht über den Sportarten ein Eintrag, der `null` wählt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportPicker(
    sports: List<SportDto>,
    selectedCode: String?,
    onSelect: (String?) -> Unit,
    enabled: Boolean = true,
    error: String? = null,
    allLabel: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val hits = remember(sports, query) { sports.search(query) }
    val usable = enabled && sports.isNotEmpty()
    val focusManager = LocalFocusManager.current

    fun close() {
        expanded = false
        focusManager.clearFocus()
    }

    fun choose(code: String?) {
        onSelect(code)
        close()
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { open -> if (open) { expanded = true } else { close() } }) {
        OutlinedTextField(
            value = if (focused) query else sports.firstOrNull { it.code == selectedCode }?.label() ?: allLabel.orEmpty(),
            onValueChange = {
                query = it
                expanded = true
            },
            enabled = usable,
            singleLine = true,
            label = { Text("Sportart") },
            placeholder = { Text("Suchen …") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, usable)
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) query = ""
                },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { close() }) {
            if (allLabel != null && query.isBlank()) {
                DropdownMenuItem(
                    text = { Text(allLabel) },
                    onClick = { choose(null) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
            hits.forEach { sport ->
                DropdownMenuItem(
                    text = { Text(sport.label()) },
                    onClick = { choose(sport.code) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
            if (hits.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Keine Sportart gefunden") },
                    onClick = {},
                    enabled = false,
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
