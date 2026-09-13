package com.partridgeman.sudokusolver.ui

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.partridgeman.sudokusolver.automation.AssistantState
import com.partridgeman.sudokusolver.automation.AssistantStore
import com.partridgeman.sudokusolver.automation.OverlayPosition
import com.partridgeman.sudokusolver.automation.SudokuAccessibilityService
import com.partridgeman.sudokusolver.vision.ImageRect
import kotlin.math.roundToInt

class AssistantOverlay(private val service: SudokuAccessibilityService) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private val preferences = service.getSharedPreferences("overlay", 0)
    private val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = preferences.getInt("x", 16)
        y = preferences.getInt("y", 96)
    }

    fun show() {
        if (view != null) { view?.visibility = View.VISIBLE; return }
        val panel = ComposeView(service).apply {
            setViewTreeLifecycleOwner(service)
            setViewTreeSavedStateRegistryOwner(service)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val state by AssistantStore.state.collectAsState()
                MaterialTheme { FloatingAssistant(state, service::scan, service::fill, service::stop,
                    onClose = { service.stop(); close() }, onDrag = ::move,
                    onAuto = { value -> AssistantStore.update { it.copy(auto = value, reviewed = false) } },
                    onReviewed = { value -> AssistantStore.update { it.copy(reviewed = value) } }) }
            }
        }
        view = panel
        panel.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) reclamp()
        }
        try { manager.addView(panel, params); panel.post { move(0f, 0f) } }
        catch (error: Exception) { view = null; panel.disposeComposition(); throw error }
    }

    fun hideForCapture(hidden: Boolean) { view?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE }

    fun bounds(): ImageRect? {
        val panel = view?.takeIf { it.visibility == View.VISIBLE && it.width > 0 && it.height > 0 } ?: return null
        val location = IntArray(2)
        panel.getLocationOnScreen(location)
        return ImageRect(location[0].toDouble(), location[1].toDouble(), (location[0] + panel.width).toDouble(), (location[1] + panel.height).toDouble())
    }

    fun reclamp() { view?.post { move(0f, 0f) } }

    private fun move(dx: Float, dy: Float) {
        val panel = view ?: return
        if ((dx != 0f || dy != 0f) && AssistantStore.state.value.filling) service.stop()
        val (width, height) = service.displaySize()
        val position = OverlayPosition(params.x + dx.roundToInt(), params.y + dy.roundToInt()).clamped(width, height, panel.width, panel.height)
        params.x = position.x; params.y = position.y
        manager.updateViewLayout(panel, params)
        preferences.edit().putInt("x", params.x).putInt("y", params.y).apply()
    }

    fun close() {
        val panel = view ?: return
        view = null
        manager.removeView(panel)
        panel.disposeComposition()
    }
}

@Composable
private fun FloatingAssistant(state: AssistantState, onScan: () -> Unit, onFill: () -> Unit, onStop: () -> Unit,
    onClose: () -> Unit, onDrag: (Float, Float) -> Unit, onAuto: (Boolean) -> Unit, onReviewed: (Boolean) -> Unit) {
    var minimized by remember { mutableStateOf(false) }
    var solution by remember { mutableStateOf(false) }
    val compact = minimized || state.filling
    Surface(modifier = Modifier.width(if (minimized || state.filling) 200.dp else 260.dp), shadowElevation = 8.dp,
        shape = MaterialTheme.shapes.medium) {
        Column {
            Row(Modifier.fillMaxWidth().height(40.dp).background(MaterialTheme.colorScheme.primaryContainer)) {
                Text("Sudoku · drag", Modifier.weight(1f).pointerInput(Unit) {
                    detectDragGestures { change, amount -> change.consume(); onDrag(amount.x, amount.y) }
                }.padding(8.dp), fontSize = 14.sp, maxLines = 1)
                TextButton(onClick = { minimized = !minimized }, modifier = Modifier.width(40.dp), contentPadding = PaddingValues(0.dp)) { Text(if (minimized) "+" else "−") }
                TextButton(onClick = onClose, modifier = Modifier.width(40.dp), contentPadding = PaddingValues(0.dp)) { Text("×") }
            }
            Column(Modifier.padding(10.dp).heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                Text(state.message, fontSize = 12.sp, maxLines = if (compact) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                if (state.busy) Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(0.dp)) { Text("Stop") }
                else {
                    Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Scan puzzle") }
                    if (!minimized) {
                        Row {
                            Checkbox(state.auto, onCheckedChange = onAuto)
                            Text("Auto: fill after Scan\nCell-first input; notes off", fontSize = 12.sp)
                        }
                        val analysis = state.analysis
                        if (analysis != null) {
                            TextButton(onClick = { solution = !solution }) { Text(if (solution) "Show scanned digits" else "Show solution") }
                            PuzzleDigits(analysis.cells.map { it.value }, if (solution) analysis.plan?.solution?.cells else null)
                            if (!state.auto && analysis.plan != null && analysis.keypad != null) {
                                Row {
                                    Checkbox(state.reviewed, onCheckedChange = onReviewed)
                                    Text("Digits checked; cell-first input and pencil mode off", fontSize = 12.sp)
                                }
                            }
                            Button(onClick = onFill, modifier = Modifier.fillMaxWidth(), enabled =
                                analysis.plan?.entries?.isNotEmpty() == true && analysis.keypad != null && (state.auto || state.reviewed)) {
                                Text("Autofill blanks")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PuzzleDigits(scanned: List<Int?>, solution: List<Int>? = null) {
    Column {
        repeat(9) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(9) { column ->
                    val index = row * 9 + column
                    val clue = scanned.getOrNull(index)
                    val value = solution?.getOrNull(index) ?: clue
                    Text(if (value == null) "?" else if (value == 0) "·" else value.toString(), Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace, color = when {
                            clue == null -> Color.Red
                            solution != null && clue == 0 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        })
                }
            }
        }
    }
}
