package io.github.hilbert0619.coursetable.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Shows tabs as spaces in a text field (Compose draws a tab with no width, so pasted spreadsheet rows ran together).
 * The text itself keeps its tabs; one character for one, so the cursor needs no mapping.
 */
object TabsAsSpaces : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(AnnotatedString(text.text.replace('\t', ' ')), OffsetMapping.Identity)
}
