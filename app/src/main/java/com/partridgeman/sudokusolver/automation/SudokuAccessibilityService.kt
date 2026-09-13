package com.partridgeman.sudokusolver.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.partridgeman.sudokusolver.capture.AccessibilityScreenCapture
import com.partridgeman.sudokusolver.recognition.CellCropAnalyzer
import com.partridgeman.sudokusolver.recognition.InkKind
import com.partridgeman.sudokusolver.recognition.OfflinePuzzleReader
import com.partridgeman.sudokusolver.recognition.PuzzleAnalysis
import com.partridgeman.sudokusolver.ui.AssistantOverlay
import com.partridgeman.sudokusolver.vision.BoardDetection
import com.partridgeman.sudokusolver.vision.BoardGeometry
import com.partridgeman.sudokusolver.vision.GridBoardScanner
import com.partridgeman.sudokusolver.vision.ImagePoint
import com.partridgeman.sudokusolver.vision.ImageRect
import com.partridgeman.sudokusolver.vision.android.toPixelImage
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Android capture/input boundary. The pure runner owns the validation and fill sequence. */
class SudokuAccessibilityService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val registry = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = registry.savedStateRegistry
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val reader by lazy { OfflinePuzzleReader() }
    private val capture by lazy { AccessibilityScreenCapture(this) }
    private val overlay by lazy { AssistantOverlay(this) }
    private var operation: Job? = null
    private var generation = 0
    private var target: TargetWindow? = null

    override fun onCreate() {
        super.onCreate()
        registry.performAttach()
        registry.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onServiceConnected() {
        instance = this
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        AssistantStore.update { it.copy(connected = true, message = "Open your Sudoku app, turn pencil mode off, then tap Scan puzzle.") }
        showOverlay()
    }

    fun showOverlay() {
        try { overlay.show() } catch (error: Exception) {
            AssistantStore.update { it.copy(message = "Could not show the overlay: ${error.message}") }
        }
    }

    fun scan() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            AssistantStore.update { it.copy(message = "Live overlay scanning and autofill require Android 11 or newer. Use Scan Puzzle in the main app for solution previews.") }
            return
        }
        launchOperation {
            AssistantStore.update { it.copy(analysis = null, reviewed = false, message = "Reading puzzle locally…") }
            val (window, analysis) = inspectAnalysis()
            target = window
            AssistantStore.update { it.copy(analysis = analysis, message = analysis.message) }
            if (AssistantStore.state.value.auto && analysis.plan?.entries?.isNotEmpty() == true && analysis.keypad != null) {
                runFill(analysis, window)
            }
        }
    }

    fun fill() {
        val state = AssistantStore.state.value
        if (state.busy || !(state.auto || state.reviewed)) return
        val analysis = state.analysis ?: return
        val window = target ?: return
        if (analysis.plan == null || analysis.keypad == null) return
        launchOperation { runFill(analysis, window) }
    }

    private suspend fun runFill(analysis: PuzzleAnalysis, window: TargetWindow) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { "Autofill requires Android 11 or newer." }
        val plan = requireNotNull(analysis.plan)
        val keys = requireNotNull(analysis.keypad)
        AssistantStore.update { it.copy(filling = true, message = "Filling ${plan.entries.size} blanks…") }
        // Allow Compose to collapse/park the overlay before the first visual snapshot.
        delay(160)

        FastAutofillRunner().run(plan, window, keys, object : FastAutofillPort {
            override suspend fun inspect(): FillVisualSnapshot = inspectFillVisual(window)
            override suspend fun tap(point: ImagePoint, expectedWindow: TargetWindow) = tapVerified(point, expectedWindow)
        }) { completed, total ->
            AssistantStore.update { it.copy(message = "Filled $completed of $total blanks. Stop anytime.") }
        }

        target = null
        AssistantStore.update { it.copy(analysis = null, reviewed = false, message = "Puzzle filled. Original clues were preserved.") }
    }

    /**
     * Lightweight autofill inspection: screenshot + grid geometry + per-cell ink only.
     * No ML Kit, no clue OCR and no Sudoku solving. The expensive recognizer runs once
     * at scan time; this path is designed to stay well under a second per interaction.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private suspend fun inspectFillVisual(expectedWindow: TargetWindow): FillVisualSnapshot {
        coroutineContext.ensureActive()
        overlay.hideForCapture(true)
        val bitmap: android.graphics.Bitmap
        val current: TargetWindow
        try {
            delay(55)
            current = currentTarget()
            check(current == expectedWindow) { "The foreground app or screen changed. Autofill stopped." }
            bitmap = capture.capture()
            check(bitmap.width == current.width && bitmap.height == current.height) {
                "The screen dimensions changed. Autofill stopped."
            }
        } finally {
            overlay.hideForCapture(false)
        }

        val visual = withContext(Dispatchers.Default) {
            val pixels = try { bitmap.toPixelImage() } finally { bitmap.recycle() }
            val detection = GridBoardScanner().scan(pixels) as? BoardDetection.Detected
                ?: error("The Sudoku grid could not be verified during autofill.")
            val normalized = detection.geometry.normalize(pixels, 900)
            val inks = (0 until 81).map { CellCropAnalyzer.analyze(normalized, it) }
            Triple(
                detection.geometry,
                inks.map { it.kind != InkKind.BLANK },
                inks.map { it.background },
            )
        }

        check(currentTarget() == expectedWindow) { "The foreground app changed. Autofill stopped." }
        coroutineContext.ensureActive()
        return FillVisualSnapshot(
            window = current,
            geometry = visual.first,
            occupied = visual.second,
            backgrounds = visual.third,
            selectedCell = selectedCell(visual.first),
        )
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private suspend fun inspectAnalysis(): Pair<TargetWindow, PuzzleAnalysis> {
        coroutineContext.ensureActive()
        overlay.hideForCapture(true)
        val bitmap: android.graphics.Bitmap
        val window: TargetWindow
        try {
            delay(160)
            window = currentTarget()
            bitmap = capture.capture()
            check(currentTarget() == window && bitmap.width == window.width && bitmap.height == window.height) {
                "The app or screen changed during capture. Rescan."
            }
        } finally { overlay.hideForCapture(false) }
        // Do not recycle a borrowed OCR bitmap on cancellation: ML Kit may still be using it.
        val parsed = reader.read(bitmap, accessibleKeys())
        check(currentTarget() == window) { "The foreground app changed. Rescan the puzzle." }
        coroutineContext.ensureActive()
        return window to parsed
    }

    private fun launchOperation(block: suspend () -> Unit) {
        val previous = operation
        previous?.cancel()
        val id = ++generation
        AssistantStore.update { it.copy(busy = true, filling = false) }
        operation = scope.launch {
            previous?.join()
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (id == generation) {
                    target = null
                    AssistantStore.update { it.copy(analysis = null, reviewed = false, message = error.message ?: "The scan failed. Try again.") }
                }
            } finally {
                if (id == generation) AssistantStore.update { it.copy(busy = false, filling = false) }
            }
        }
    }

    fun stop() {
        ++generation
        operation?.cancel()
        target = null
        AssistantStore.update { it.copy(busy = false, filling = false, reviewed = false, analysis = null,
            message = "Stopped. Scan again when ready.") }
    }

    private fun currentTarget(): TargetWindow {
        val application = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }.singleOrNull()
            ?: error("Keep one Sudoku app in the foreground, with dialogs and the keyboard closed.")
        val root = application.root ?: error("Cannot inspect the foreground app.")
        val name = try { root.packageName?.toString() } finally { root.recycle() }
        check(!name.isNullOrBlank() && name != packageName) { "Open your Sudoku app before scanning." }
        val bounds = Rect().also { application.getBoundsInScreen(it) }
        val (width, height) = displaySize()
        val rotation = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).rotation

        // Only windows that can actually replace/intercept a meaningful part of the
        // Sudoku UI should block a scan. Samsung exposes ordinary system surfaces and
        // small transient app windows (for example an ongoing-call chip) above the app.
        // Treating any one-pixel overlap as a dialog caused false positives on clean
        // puzzle screens. Keyboards always block; higher application windows only block
        // when they cover a material fraction of the foreground app.
        check(windows.none { candidate -> isBlockingWindow(candidate, application, bounds) }) {
            "Close large dialogs or the keyboard before scanning."
        }
        return TargetWindow(name, application.id, bounds.toImageRect(), width, height, rotation)
    }

    private fun isBlockingWindow(candidate: AccessibilityWindowInfo, application: AccessibilityWindowInfo, appBounds: Rect): Boolean {
        if (candidate === application || candidate.layer <= application.layer) return false
        return when (candidate.type) {
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> true
            AccessibilityWindowInfo.TYPE_APPLICATION -> {
                val other = Rect().also(candidate::getBoundsInScreen)
                val intersection = Rect()
                if (!intersection.setIntersect(other, appBounds)) return false

                val overlapArea = intersection.width().toLong() * intersection.height().toLong()
                val appArea = appBounds.width().toLong() * appBounds.height().toLong()
                if (appArea <= 0L) return false

                // A real dialog/panel generally covers far more than this. Small call
                // chips, bubbles and Samsung transient surfaces stay below the threshold.
                overlapArea.toDouble() / appArea.toDouble() >= 0.03
            }
            else -> false
        }
    }

    @Suppress("DEPRECATION")
    fun displaySize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = DisplayMetrics()
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    @Suppress("DEPRECATION")
    private fun visitNodes(visit: (AccessibilityNodeInfo) -> Unit) {
        val root = rootInActiveWindow ?: return
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        try {
            while (queue.isNotEmpty() && count++ < 4096) {
                val node = queue.removeFirst()
                try {
                    visit(node)
                    for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
                } finally { node.recycle() }
            }
        } finally { while (queue.isNotEmpty()) queue.removeFirst().recycle() }
    }

    private fun accessibleKeys(): List<NumberTarget> {
        val found = mutableListOf<NumberTarget>()
        visitNodes { node ->
            if (node.isVisibleToUser && node.isEnabled) {
                val label = (node.text ?: node.contentDescription)?.toString()?.trim()
                val digit = label?.takeIf { it.matches(Regex("[1-9]")) }?.toInt()
                if (digit != null) {
                    val parent = if (!node.isClickable) node.parent else null
                    try {
                        val clickable = if (node.isClickable) node else parent?.takeIf { it.isClickable && it.isEnabled }
                        if (clickable != null) {
                            val rect = Rect().also(clickable::getBoundsInScreen)
                            if (rect.width() > 0 && rect.height() > 0) found += NumberTarget(digit, rect.toImageRect(), 1.0)
                        }
                    } finally { parent?.recycle() }
                }
            }
        }
        return found.distinct()
    }

    private fun selectedCell(geometry: BoardGeometry): Int? {
        val found = mutableSetOf<Int>()
        val cellSize = geometry.bounds.width / 9
        visitNodes { node ->
            if (node.isSelected && node.isVisibleToUser) {
                val bounds = Rect().also(node::getBoundsInScreen)
                if (bounds.width().toDouble() in cellSize * 0.6..cellSize * 1.2 &&
                    bounds.height().toDouble() in cellSize * 0.6..cellSize * 1.2) {
                    val point = ImagePoint(bounds.exactCenterX().toDouble(), bounds.exactCenterY().toDouble())
                    geometry.cells.indexOfFirst { it.bounds.contains(point) }.takeIf { it >= 0 }?.let(found::add)
                }
            }
        }
        return found.singleOrNull()
    }

    private suspend fun tapVerified(point: ImagePoint, window: TargetWindow) {
        coroutineContext.ensureActive()
        check(currentTarget() == window) { "The foreground app changed. Autofill stopped." }
        check(window.bounds.contains(point) && point.x in 0.0..<window.width.toDouble() && point.y in 0.0..<window.height.toDouble()) {
            "The target is outside the visible puzzle. Rescan."
        }
        check(overlay.bounds()?.contains(point) != true) { "Move the overlay away from the board and number buttons, then rescan." }
        check(windows.none { candidate ->
            if (candidate.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) false else {
                val bounds = Rect().also(candidate::getBoundsInScreen)
                val root = candidate.root
                val ours = try { root?.packageName?.toString() == packageName } finally { root?.recycle() }
                !ours && bounds.contains(point.x.toInt(), point.y.toInt())
            }
        }) { "Another floating window covers the target. Move it away and rescan." }
        val path = Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
        suspendCancellableCoroutine<Unit> { continuation ->
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { if (continuation.isActive) continuation.resume(Unit) }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("A gesture was interrupted. Rescan before continuing."))
                }
            }, null)
            if (!dispatched && continuation.isActive) continuation.resumeWithException(IllegalStateException("Android rejected the gesture. Check accessibility access."))
        }
        // Give the Sudoku UI enough time to paint selection/input state without the
        // 180 ms penalty that previously compounded with full OCR after every tap.
        delay(85)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (AssistantStore.state.value.filling && target != null && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (runCatching { currentTarget() }.getOrNull() != target) stop()
        }
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        stop()
        overlay.reclamp()
    }
    override fun onInterrupt() = stop()
    override fun onDestroy() {
        stop()
        scope.cancel()
        overlay.close()
        reader.close()
        instance = null
        AssistantStore.update { it.copy(connected = false) }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
    companion object { var instance: SudokuAccessibilityService? = null; private set }
}

private fun Rect.toImageRect() = ImageRect(left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble())
