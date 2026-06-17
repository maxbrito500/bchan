package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import tachiyomi.presentation.core.components.Pill

@Composable
fun TabText(text: String, badgeCount: Int? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (badgeCount != null) {
            // Prominent (filled) badge so pending updates clearly signal that action is
            // needed on this tab, e.g. the Extensions tab's pending-update count.
            Pill(
                text = "$badgeCount",
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                fontSize = 10.sp,
            )
        }
    }
}
