package pe.vozruta.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Background = Color(0xFF101416)
internal val Panel = Color(0xFF1A2325)
internal val Raised = Color(0xFF232E30)
internal val Line = Color(0xFF405052)
internal val Accent = Color(0xFFE8FB73)
internal val Ink = Color(0xFF171D0D)
internal val Snow = Color(0xFFF4F7EC)
internal val Ash = Color(0xFFBCCAC5)
internal val Warning = Color(0xFFFFD890)
internal val Shape = RoundedCornerShape(23.dp)

private val colors = darkColorScheme(
    primary = Accent, onPrimary = Ink, primaryContainer = Accent, onPrimaryContainer = Ink,
    secondaryContainer = Raised, onSecondaryContainer = Snow, background = Background, onBackground = Snow,
    surface = Panel, onSurface = Snow, surfaceVariant = Raised, onSurfaceVariant = Ash,
    outline = Line, error = Warning, onError = Ink
)
private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 41.sp, lineHeight = 43.sp, letterSpacing = (-1.7).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 29.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp)
)

@Composable internal fun Theme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = typography, content = content)

@Composable internal fun Tag(text: String, dark: Boolean = false) {
    Surface(color = if (dark) Ink else Raised, contentColor = Accent, shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable internal fun Primary(label: String, action: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, mic: Boolean = false) {
    Button(onClick = action, modifier = modifier.heightIn(min = 60.dp), enabled = enabled, shape = RoundedCornerShape(15.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        if (mic) {
            MicIcon(Ink, Modifier.size(21.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable internal fun Secondary(label: String, action: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = action, modifier = modifier.heightIn(min = 56.dp), enabled = enabled, shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Snow),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable internal fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = Shape, color = Panel) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable internal fun Section(heading: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(heading, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Text(subtitle, color = Ash, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun RouteMark(modifier: Modifier = Modifier, color: Color = Accent) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * .18f, size.height * .78f)
            lineTo(size.width * .58f, size.height * .78f)
            cubicTo(size.width * .93f, size.height * .78f, size.width * .96f, size.height * .51f, size.width * .65f, size.height * .47f)
            lineTo(size.width * .36f, size.height * .43f)
            cubicTo(size.width * .13f, size.height * .39f, size.width * .21f, size.height * .18f, size.width * .43f, size.height * .18f)
            lineTo(size.width * .83f, size.height * .18f)
        }
        drawPath(path, color, style = Stroke(size.width * .073f, cap = StrokeCap.Round))
        drawCircle(color, size.width * .11f, Offset(size.width * .18f, size.height * .78f))
        drawCircle(color, size.width * .105f, Offset(size.width * .83f, size.height * .18f))
    }
}

@Composable private fun MicIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(color, Offset(size.width * .34f, size.height * .08f), Size(size.width * .32f, size.height * .52f),
            androidx.compose.ui.geometry.CornerRadius(size.width * .16f), style = stroke)
        drawArc(color, 0f, 180f, false, Offset(size.width * .18f, size.height * .27f),
            Size(size.width * .64f, size.height * .48f), style = stroke)
        drawLine(color, Offset(size.width / 2, size.height * .75f), Offset(size.width / 2, size.height * .94f), stroke.width, StrokeCap.Round)
    }
}

@Composable internal fun Fact(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Raised, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Ash, style = MaterialTheme.typography.labelMedium)
            Text(value, color = Snow, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable internal fun ToggleRow(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = change),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
    }
}
