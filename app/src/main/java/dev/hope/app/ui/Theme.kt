package dev.hope.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val bg = Color(0xFF0E0F11)
    val surface = Color(0xFF15171A)
    val surface2 = Color(0xFF1B1E22)
    val surface3 = Color(0xFF23272C)
    val line = Color(0xFF2A2E33)
    val text = Color(0xFFE7E9EC)
    val muted = Color(0xFF8B9199)
    val accent = Color(0xFF6BAED6)
    val good = Color(0xFF5FCB7A)
    val warn = Color(0xFFD9A441)
    val bad = Color(0xFFC96B6B)
}

val Mono = FontFamily.Monospace

private val scheme = darkColorScheme(
    primary = C.accent, onPrimary = C.bg,
    background = C.bg, onBackground = C.text,
    surface = C.surface, onSurface = C.text,
    surfaceVariant = C.surface2, onSurfaceVariant = C.muted,
    outline = C.line, error = C.bad
)

@Composable
fun HopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography()) {
        Surface(color = C.bg, contentColor = C.text, modifier = Modifier.fillMaxSize(), content = content)
    }
}

// ---------- логотип ----------

private val GLYPHS = mapOf(
    'H' to listOf("X...X", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"),
    'O' to listOf(".XXX.", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."),
    'P' to listOf("XXXX.", "X...X", "X...X", "XXXX.", "X....", "X....", "X...."),
    'E' to listOf("XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "XXXXX")
)

private val PLANE = listOf(
    "........X",
    "......XXX",
    "....XXXXX",
    "..XXXXXXX",
    "XXXXXXXXX",
    "..XXXXXX.",
    "....XXX..",
    "....XX..."
)

@Composable
fun PixelLogo(modifier: Modifier = Modifier, cell: Dp = 4.dp) {
    val word = "HOPE"
    val cols = word.length * 6 - 1 + 2 + 9
    Canvas(modifier.size(cell * cols, cell * 8)) {
        val c = cell.toPx()
        fun px(x: Int, y: Int, color: Color) =
            drawRect(color, Offset(x * c, y * c), Size(c, c))
        word.forEachIndexed { i, ch ->
            GLYPHS.getValue(ch).forEachIndexed { y, row ->
                row.forEachIndexed { x, v -> if (v == 'X') px(i * 6 + x, y + 1, C.text) }
            }
        }
        val ox = word.length * 6 - 1 + 2
        PLANE.forEachIndexed { y, row ->
            row.forEachIndexed { x, v -> if (v == 'X') px(ox + x, y, C.accent) }
        }
    }
}

// ---------- компоненты ----------

@Composable
fun Header(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelLogo(cell = 4.dp)
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Text(
            title.uppercase(), color = C.muted, fontFamily = Mono, fontSize = 11.sp,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun Pick(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.clip(shape)
            .background(if (on) C.surface3 else Color.Transparent)
            .border(1.dp, if (on) C.accent else C.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (on) C.text else C.muted, fontFamily = Mono, fontSize = 13.sp)
    }
}

@Composable
fun Btn(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.height(44.dp).clip(shape)
            .background(if (primary) C.accent else Color.Transparent)
            .border(1.dp, if (primary) C.accent else C.line, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                !enabled -> C.muted
                primary -> C.bg
                else -> C.text
            },
            fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun Tog(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = C.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.bg, checkedTrackColor = C.accent, checkedBorderColor = C.accent,
                uncheckedThumbColor = C.muted, uncheckedTrackColor = C.surface2, uncheckedBorderColor = C.line
            )
        )
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    minLines: Int = 1,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Ascii
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier,
        label = { Text(label, fontFamily = Mono, fontSize = 12.sp) },
        singleLine = singleLine, minLines = minLines,
        shape = RoundedCornerShape(8.dp),
        textStyle = TextStyle(fontFamily = Mono, fontSize = 14.sp, color = C.text),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.accent, unfocusedBorderColor = C.line,
            focusedLabelColor = C.accent, unfocusedLabelColor = C.muted,
            cursorColor = C.accent, focusedTextColor = C.text, unfocusedTextColor = C.text
        )
    )
}

@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = C.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = C.accent, fontFamily = Mono, fontSize = 14.sp)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.line,
                activeTickColor = C.bg, inactiveTickColor = C.surface3
            )
        )
    }
}

@Composable
fun IconBtn(res: Int, onClick: () -> Unit, tint: Color = C.muted) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(res), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun Tag(text: String, color: Color, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        text, color = color, fontFamily = Mono, fontSize = 11.sp,
        modifier = Modifier.clip(shape)
            .border(1.dp, color.copy(alpha = 0.4f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
fun Hint(text: String, color: Color = C.muted) {
    Text(text, color = color, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
}
