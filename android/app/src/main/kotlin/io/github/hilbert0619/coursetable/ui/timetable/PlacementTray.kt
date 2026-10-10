package io.github.hilbert0619.coursetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursekit.CourseTemplate
import io.github.hilbert0619.coursekit.WeekParity
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.ChoiceRow
import io.github.hilbert0619.coursetable.ui.components.StepperRow
import io.github.hilbert0619.coursetable.ui.rememberNames
import io.github.hilbert0619.coursetable.ui.theme.CoursePalette
import java.util.UUID

/** Bottom panel while placing: choose a course, set its length and weeks, then tap empty slots. */
@Composable
fun PlacementTray(
    templates: List<CourseTemplate>,
    /** Templates from the course list (removable), as opposed to ones taken from placed courses. */
    savedIds: Set<UUID>,
    selection: UUID?,
    onSelect: (UUID?) -> Unit,
    length: Int,
    onLength: (Int) -> Unit,
    parity: WeekParity,
    onParity: (WeekParity) -> Unit,
    /** `null` when the weekend is always shown because there are weekend classes. */
    showsWeekend: Boolean?,
    onShowsWeekend: (Boolean) -> Unit,
    periodsPerDay: Int,
    onAddCourses: () -> Unit,
    onRemove: (UUID) -> Unit,
) {
    val names = rememberNames()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 12.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                val selected = templates.firstOrNull { it.id == selection }
                Text(
                    when {
                        selected != null -> stringResource(R.string.tap_slots_to_place, selected.name)
                        templates.isEmpty() -> stringResource(R.string.add_courses_first)
                        else -> stringResource(R.string.choose_a_course)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (showsWeekend != null) {
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = showsWeekend,
                        onClick = { onShowsWeekend(!showsWeekend) },
                        label = { Text(stringResource(R.string.weekend)) },
                    )
                }
            }

            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(templates, key = { it.id }) { template ->
                    TemplateChip(
                        template,
                        isSelected = template.id == selection,
                        removable = template.id in savedIds,
                        onClick = { onSelect(if (template.id == selection) null else template.id) },
                        onRemove = { onRemove(template.id) },
                    )
                }
                item("add") {
                    Row(
                        Modifier.heightIn(min = 40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
                            .combinedClickable(onClick = onAddCourses).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppIcon(R.drawable.ic_add, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.add_courses), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            StepperRow(
                label = pluralStringResource(R.plurals.length_periods, length, length),
                value = length,
                range = 1..maxOf(periodsPerDay, 1),
                onChange = onLength,
            )
            ChoiceRow(WeekParity.entries, parity, label = { names.parity(it) }, onSelect = onParity)
        }
    }
}

@Composable
private fun TemplateChip(template: CourseTemplate, isSelected: Boolean, removable: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    val color = CoursePalette.color(template.colorIndex)
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.heightIn(min = 40.dp).clip(CircleShape)
                .background(if (isSelected) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(2.dp, if (isSelected) color else Color.Transparent, CircleShape)
                .combinedClickable(onClick = onClick, onLongClick = if (removable) ({ menu = true }) else null)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Column {
                Text(
                    template.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (template.room.isNotEmpty()) {
                    Text(
                        template.room,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_from_list), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menu = false
                    onRemove()
                },
            )
        }
    }
}
