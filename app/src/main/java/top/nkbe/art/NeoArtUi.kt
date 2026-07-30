package top.nkbe.art

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════
// DATA MODELS
// ═══════════════════════════════════════════════════════════

data class ArkSettingsData(
    val soName: String = "ArkStub",
    val stubClassName: String = "com.ark.safe.StubApp",
    val savePath: String = "",
    val autoSign: Boolean = false,
    val fake360Type: Int = 0,
    val useCustomJks: Boolean = false,
    val jksPath: String = "",
    val jksStorePass: String = "",
    val jksAlias: String = "",
    val jksKeyPass: String = ""
)

data class NeoArtDialogState(
    val title: String,
    val message: String,
    val confirmText: String,
    val dismissText: String? = null,
    val cancelable: Boolean = true,
    val onConfirm: () -> Unit,
    val onDismiss: (() -> Unit)? = null,
)

/**
 * Status colors following the Vector design spec:
 * Green=Active, Gray=Disabled, Orange=Warning, Red=Error, Blue=Info
 */
enum class StatusColor(val color: Long) {
    ACTIVE(0xFF4CAF50),
    DISABLED(0xFF9E9E9E),
    WARNING(0xFFFF9800),
    ERROR(0xFFE53935),
    INFO(0xFF2196F3),
}

enum class LogLevel(val label: String, val prefix: String) {
    ALL("全部", ""),
    INFO("信息", "[INFO]"),
    WARN("警告", "[WARN]"),
    ERROR("错误", "[ERROR]"),
    DEBUG("调试", "[DEBUG]");

    companion object {
        fun infer(message: String): LogLevel {
            val normalized = message.trim()
            entries.firstOrNull {
                it != ALL && normalized.startsWith(it.prefix, ignoreCase = true)
            }?.let { return it }

            // Recoverable fallbacks are warnings even when the underlying operation failed.
            if (listOf("使用默认", "跳过", "未开启", "没有 lib", "不支持", "为空")
                    .any(normalized::contains)) {
                return WARN
            }
            if (listOf("失败", "异常", "错误", "非法", "无效")
                    .any(normalized::contains)) {
                return ERROR
            }
            return INFO
        }

        fun format(message: String, level: LogLevel = infer(message)): String =
            message.lineSequence()
                .map { line ->
                    val trimmed = line.trimEnd()
                    if (entries.any {
                            it != ALL && trimmed.startsWith(it.prefix, ignoreCase = true)
                        }
                    ) {
                        trimmed
                    } else {
                        "${level.prefix} $trimmed"
                    }
                }
                .joinToString("\n")
    }
}

class NeoArtUiController internal constructor(initialLog: String = "等待文件访问授权…") {
    internal var selectedTab by mutableIntStateOf(0)
    internal var logText by mutableStateOf(LogLevel.format(initialLog))
        private set
    internal var selectButtonEnabled by mutableStateOf(true)
        private set
    internal var selectedApkPath by mutableStateOf<String?>(null)
    internal var showPresetDialog by mutableStateOf(false)
    internal var settingsState by mutableStateOf(ArkSettingsData())
    internal var logFilter by mutableStateOf(LogLevel.ALL)
    internal var snackbarText by mutableStateOf<String?>(null)
    internal var snackbarStatus by mutableStateOf(StatusColor.INFO)
    internal var dialogState by mutableStateOf<NeoArtDialogState?>(null)

    var onSaveSettingsHandler: ((ArkSettingsData) -> String?)? = null

    fun appendLog(message: String, level: LogLevel = LogLevel.infer(message)) {
        val formatted = LogLevel.format(message, level)
        logText = if (logText.isBlank()) formatted else "$logText\n$formatted"
    }
    fun clearLog() { logText = LogLevel.format("日志已清空", LogLevel.INFO) }
    fun setSelectEnabled(enabled: Boolean) { selectButtonEnabled = enabled }
    fun updateSelectedApk(path: String) { selectedApkPath = path }
    fun loadSettings(data: ArkSettingsData) { settingsState = data }
    fun showSnackbar(text: String, status: StatusColor = StatusColor.INFO) {
        snackbarText = text; snackbarStatus = status
    }
    fun showDialog(state: NeoArtDialogState) { dialogState = state }
    fun dismissDialog() { dialogState = null }
}

// ═══════════════════════════════════════════════════════════
// ENTRY POINT
// ═══════════════════════════════════════════════════════════

object NeoArtUi {
    @JvmStatic
    fun install(
        activity: ComponentActivity,
        onSelectApk: Runnable,
        onLoadSettings: java.util.concurrent.Callable<ArkSettingsData>,
    ): NeoArtUiController {
        val controller = NeoArtUiController()
        try {
            val initial = onLoadSettings.call()
            if (initial != null) controller.settingsState = initial
        } catch (_: Exception) {}
        val content = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NeoArtApp(controller, onSelectApk = onSelectApk::run, onLoadSettings = {
                    try {
                        val updated = onLoadSettings.call()
                        if (updated != null) controller.settingsState = updated
                    } catch (_: Exception) {}
                })
            }
        }
        activity.setContentView(content)
        return controller
    }
}

// ═══════════════════════════════════════════════════════════
// APP SHELL
// ═══════════════════════════════════════════════════════════

@Composable
private fun NeoArtApp(
    controller: NeoArtUiController,
    onSelectApk: () -> Unit,
    onLoadSettings: () -> Unit,
) {
    val context = LocalContext.current
    val keyColor = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { Color(ContextCompat.getColor(context, android.R.color.system_accent1_500)) }
            catch (_: Exception) { Color(0xFF4B70F5) }
        } else Color(0xFF4B70F5)
    }
    val themeController = remember(keyColor) {
        ThemeController(colorSchemeMode = ColorSchemeMode.MonetSystem, keyColor = keyColor)
    }

    COUITheme(controller = themeController) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(COUITheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (controller.selectedTab) {
                        0 -> ManagementPage(controller, onSelectApk,
                            onGoSettings = { controller.selectedTab = 1 },
                            onGoLogs = { controller.selectedTab = 2 })
                        1 -> SettingsPage(controller,
                            onOpenPreset = { controller.showPresetDialog = true })
                        2 -> LogsPage(controller)
                    }
                }
                BottomBar(controller.selectedTab) { idx ->
                    if (idx == 1) onLoadSettings()
                    controller.selectedTab = idx
                }
            }

            // Snackbar
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp, start = 20.dp, end = 20.dp)) {
                Snackbar(controller)
            }

            // Preset dialog
            if (controller.showPresetDialog) {
                PresetSheet(
                    onPick = { preset ->
                        controller.settingsState = controller.settingsState.copy(soName = preset)
                        controller.showPresetDialog = false
                    },
                    onDismiss = { controller.showPresetDialog = false }
                )
            }

            controller.dialogState?.let { state ->
                NeoArtDialog(
                    state = state,
                    onDismiss = { controller.dismissDialog() },
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// SHARED: PanelHeader
// ═══════════════════════════════════════════════════════════

@Composable
private fun PanelHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title, color = COUITheme.colorScheme.onBackground,
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        Text(
            subtitle, color = COUITheme.colorScheme.onBackgroundVariant,
            fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
        )
        extra()
    }
}

// ═══════════════════════════════════════════════════════════
// SHARED: Status Dot
// ═══════════════════════════════════════════════════════════

@Composable
private fun StatusDot(status: StatusColor, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Color(status.color))
    )
}

// ═══════════════════════════════════════════════════════════
// SHARED: ToggleRow
// ═══════════════════════════════════════════════════════════

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle(!checked) }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = COUITheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(description, color = COUITheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onToggle)
    }
}

// ═══════════════════════════════════════════════════════════
// SHARED: ChoiceRow
// ═══════════════════════════════════════════════════════════

@Composable
private fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = COUITheme.colorScheme.onSurface, fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (subtitle != null) {
                Text(subtitle, color = COUITheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) COUITheme.colorScheme.primary
            else COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
    }
}

// ═══════════════════════════════════════════════════════════
// SHARED: FilterChip
// ═══════════════════════════════════════════════════════════

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) COUITheme.colorScheme.primary.copy(alpha = 0.15f)
        else COUITheme.colorScheme.surfaceContainer,
        tween(200), label = "chipBg"
    )
    val fg by animateColorAsState(
        if (selected) COUITheme.colorScheme.primary
        else COUITheme.colorScheme.onSurfaceVariantSummary,
        tween(200), label = "chipFg"
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ═══════════════════════════════════════════════════════════
// SHARED: Snackbar
// ═══════════════════════════════════════════════════════════

@Composable
private fun Snackbar(controller: NeoArtUiController) {
    val msg = controller.snackbarText
    LaunchedEffect(msg) {
        if (msg != null) { delay(3000); controller.snackbarText = null }
    }
    AnimatedVisibility(
        visible = msg != null,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(COUITheme.colorScheme.surfaceContainer)
                .border(1.dp, Color(controller.snackbarStatus.color).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(controller.snackbarStatus)
                Spacer(Modifier.width(10.dp))
                Text(msg ?: "", color = COUITheme.colorScheme.onSurface, fontSize = 14.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// PAGE 1: MANAGEMENT (Home Dashboard)
// ═══════════════════════════════════════════════════════════

@Composable
private fun ManagementPage(
    controller: NeoArtUiController,
    onSelectApk: () -> Unit,
    onGoSettings: () -> Unit,
    onGoLogs: () -> Unit,
) {
    val hasApk = controller.selectedApkPath != null
    val busy = !controller.selectButtonEnabled
    val status = when { busy -> StatusColor.INFO; hasApk -> StatusColor.ACTIVE; else -> StatusColor.DISABLED }
    val statusText = when { busy -> "处理中"; hasApk -> "就绪"; else -> "等待文件" }

    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            title = "Neo Art 加固",
            subtitle = "DEX 保护 · 签名校验 · 代码虚拟化"
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── APK Workbench Card ──
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(status)
                        Spacer(Modifier.width(8.dp))
                        Text("APK 保护工作台", color = COUITheme.colorScheme.onSurface,
                            fontSize = 16.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f))
                        Text(statusText, color = Color(status.color), fontSize = 12.sp,
                            fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (hasApk) controller.selectedApkPath ?: ""
                        else "选择目标 APK 后进行 DEX 加密、壳合成、签名绑定",
                        color = COUITheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(
                        text = if (busy) "处理中…" else "选择 APK 并加固",
                        onClick = onSelectApk, enabled = controller.selectButtonEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }

            // ── Strategy Card ──
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("加固策略", color = COUITheme.colorScheme.onSurface,
                            fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        TextButton("配置", onClick = onGoSettings)
                    }
                    Spacer(Modifier.height(10.dp))
                    val s = controller.settingsState
                    StrategyLine("壳 SO", s.soName, s.soName.isNotBlank())
                    StrategyLine("壳类名", s.stubClassName, s.stubClassName.isNotBlank())
                    StrategyLine("自动签名", if (s.autoSign) "开启" else "关闭", s.autoSign)
                    StrategyLine("偽360", getFake360Label(s.fake360Type), s.fake360Type > 0)
                    StrategyLine("证书", if (s.useCustomJks) "自订 JKS" else "内置 npatch.key", true)
                }
            }

            // ── Log Preview Card ──
            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("最新日志", color = COUITheme.colorScheme.onSurface,
                            fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        TextButton("完整日志", onClick = onGoLogs)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(COUITheme.colorScheme.surfaceContainer)
                            .padding(10.dp)
                    ) {
                        Text(controller.logText.takeLast(300),
                            color = COUITheme.colorScheme.onSurfaceContainer,
                            fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun StrategyLine(label: String, value: String, active: Boolean) {
    val dot = if (active) StatusColor.ACTIVE else StatusColor.DISABLED
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(dot, Modifier.size(6.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = COUITheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp, modifier = Modifier.width(72.dp))
        Text(value, color = COUITheme.colorScheme.onSurface,
            fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ═══════════════════════════════════════════════════════════
// PAGE 2: SETTINGS
// ═══════════════════════════════════════════════════════════

@Composable
private fun SettingsPage(controller: NeoArtUiController, onOpenPreset: () -> Unit) {
    var s by remember(controller.settingsState) { mutableStateOf(controller.settingsState) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { delay(2500); saved = false } }

    val subtitle = when { saved -> "✓ 已保存"; error != null -> "⚠ 配置有误"; else -> "管理加固引擎参数" }

    Column(Modifier.fillMaxSize()) {
        PanelHeader("加固设置", subtitle)

        Column(
            Modifier.weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Error banner
            AnimatedVisibility(error != null) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1AE53935)).border(1.dp, Color(0x40E53935), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(StatusColor.ERROR); Spacer(Modifier.width(8.dp))
                        Text(error ?: "", color = Color(StatusColor.ERROR.color), fontSize = 13.sp)
                    }
                }
            }

            // Section: SO & Class Name
            SectionCard("自定义 SO 与壳类名") {
                Label("SO 名称")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(s.soName, { s = s.copy(soName = it) }, "ArkStub", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ChipBtn("预设", onClick = onOpenPreset)
                }
                Spacer(Modifier.height(8.dp))
                Label("壳类名")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(s.stubClassName, { s = s.copy(stubClassName = it) }, "com.ark.safe.StubApp", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ChipBtn("清空", onClick = { s = s.copy(stubClassName = "") })
                }
                Spacer(Modifier.height(8.dp))
                Label("保存路径")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(s.savePath, { s = s.copy(savePath = it) }, "默认同目录", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ChipBtn("清空", onClick = { s = s.copy(savePath = "") })
                }
            }

            // Section: Signing
            SectionCard("签名配置") {
                ToggleRow(title = "自动签名", description = "开启后自动签署 APK 并绑定 C++ 签名校验",
                    checked = s.autoSign, onToggle = { v -> s = s.copy(autoSign = v) })
            }

            // Section: Fake 360
            SectionCard("360 伪加固识别特征") {
                Text("仅添加工具识别特征，不影响加固安全性",
                    color = COUITheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                val options = listOf(
                    0 to "关闭", 1 to "普通 (libjiagu.so)",
                    2 to "付费 (libjiagu_mips.a)", 3 to "企业 (libjiagu_vip.so)"
                )
                options.forEach { (type, label) ->
                    ChoiceRow(title = label, selected = s.fake360Type == type,
                        onClick = { s = s.copy(fake360Type = type) })
                }
            }

            // Section: Custom JKS
            SectionCard("自定义 JKS 证书") {
                ToggleRow(title = "使用自订证书",
                    description = if (s.useCustomJks) "自订 JKS 校验指纹" else "使用内置 npatch.key 校验",
                    checked = s.useCustomJks, onToggle = { v -> s = s.copy(useCustomJks = v) })
                AnimatedVisibility(s.useCustomJks) {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label("JKS 路径"); TextField(s.jksPath, { s = s.copy(jksPath = it) }, "D:\\mykey.jks")
                        Label("Store 密码"); TextField(s.jksStorePass, { s = s.copy(jksStorePass = it) }, "Store Password")
                        Label("Alias"); TextField(s.jksAlias, { s = s.copy(jksAlias = it) }, "Key Alias")
                        Label("Key 密码"); TextField(s.jksKeyPass, { s = s.copy(jksKeyPass = it) }, "Key Password")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Save button
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextButton("保存设置", onClick = {
                val err = controller.onSaveSettingsHandler?.invoke(s)
                error = err
                if (err == null) {
                    saved = true; controller.loadSettings(s)
                    controller.showSnackbar("设置已保存", StatusColor.ACTIVE)
                } else controller.showSnackbar("保存失败", StatusColor.ERROR)
            }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(14.dp)) {
        Text(title, color = COUITheme.colorScheme.primary, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

// ═══════════════════════════════════════════════════════════
// PAGE 3: LOGS
// ═══════════════════════════════════════════════════════════

@Composable
private fun LogsPage(controller: NeoArtUiController) {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    val filter = controller.logFilter

    val lines = remember(controller.logText, filter) {
        val all = controller.logText.split("\n")
        if (filter == LogLevel.ALL) all
        else all.filter { it.startsWith(filter.prefix, ignoreCase = true) }
    }
    val text = lines.joinToString("\n")
    val total = controller.logText.lines().size
    val shown = lines.size

    LaunchedEffect(text) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            title = "运行日志",
            subtitle = if (filter == LogLevel.ALL) "$total 行" else "$shown / $total 行",
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton("复制", onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("NeoArtLog", controller.logText))
                        controller.showSnackbar("已复制到剪贴板", StatusColor.ACTIVE)
                    })
                    TextButton("清空", onClick = {
                        controller.clearLog(); controller.showSnackbar("已清空")
                    })
                }
            },
            extra = {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LogLevel.entries.forEach { FilterChip(label = it.label, selected = filter == it,
                        onClick = { controller.logFilter = it }) }
                }
            }
        )

        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(COUITheme.colorScheme.surfaceContainer)
                .padding(14.dp)
        ) {
            if (text.isBlank()) {
                Text("暂无日志", color = COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f),
                    fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                SelectionContainer {
                    Text(text, Modifier.fillMaxSize().verticalScroll(scroll),
                        color = COUITheme.colorScheme.onSurfaceContainer,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// BOTTOM BAR — Clean pill navigation
// ═══════════════════════════════════════════════════════════

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(COUITheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
                .border(0.5.dp, COUITheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                listOf(Triple(0, "管理", "⚡"), Triple(1, "设置", "⚙️"), Triple(2, "日志", "📜")).forEach { (i, label, icon) ->
                    val sel = selected == i
                    val bg by animateColorAsState(
                        if (sel) COUITheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        tween(220), label = "tabBg"
                    )
                    val fg by animateColorAsState(
                        if (sel) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurfaceVariantSummary,
                        tween(220), label = "tabFg"
                    )
                    Row(
                        Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(bg)
                            .clickable { onSelect(i) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(icon, fontSize = 15.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(label, color = fg, fontSize = 14.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// SHARED: Basic Components
// ═══════════════════════════════════════════════════════════

@Composable
private fun Label(text: String) {
    Text(text, color = COUITheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun TextField(value: String, onChange: (String) -> Unit, hint: String = "", modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth().height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(COUITheme.colorScheme.background)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(hint, color = COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f), fontSize = 13.sp)
        BasicTextField(value, onChange, singleLine = true,
            textStyle = TextStyle(color = COUITheme.colorScheme.onSurface, fontSize = 13.sp),
            cursorBrush = SolidColor(COUITheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Switch(checked: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        Modifier
            .width(44.dp).height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) COUITheme.colorScheme.primary
            else COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.25f))
            .clickable { onToggle(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun ChipBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(COUITheme.colorScheme.primary.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = COUITheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ═══════════════════════════════════════════════════════════
// PRESET SHEET
// ═══════════════════════════════════════════════════════════

@Composable
private fun PresetSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val presets = remember(ctx) {
        try {
            val json = ctx.assets.open("so_name_presets.json").bufferedReader().use { it.readText() }
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val t = o.optString("title", "").trim()
                val n = o.optString("name", "").trim()
                if (t.isNotEmpty() && n.isNotEmpty()) t to n else null
            }
        } catch (_: Exception) {
            listOf("Ark默认" to "ArkStub", "腾讯乐固" to "tup", "梆梆安全" to "DexHelper",
                "爱加密" to "exec", "阿里聚安全" to "mobisec")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.65f), insideMargin = PaddingValues(20.dp)) {
            Column {
                Text("选择预设 SO 名称", color = COUITheme.colorScheme.onSurface,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("包含主流加固厂商特征", color = COUITheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    presets.forEach { (title, name) ->
                        ChoiceRow(title = title, selected = false, subtitle = "lib${name}.so",
                            onClick = { onPick(name) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton("取消", onClick = onDismiss) }
            }
        }
    }
}

@Composable
private fun NeoArtDialog(state: NeoArtDialogState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = {
        if (state.cancelable) {
            onDismiss()
            state.onDismiss?.invoke()
        }
    }) {
        Card(
            Modifier.fillMaxWidth(0.88f),
            insideMargin = PaddingValues(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    state.title,
                    color = COUITheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    state.message,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    state.dismissText?.let { dismissText ->
                        TextButton(
                            dismissText,
                            onClick = {
                                onDismiss()
                                state.onDismiss?.invoke()
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    TextButton(
                        state.confirmText,
                        onClick = {
                            onDismiss()
                            state.onConfirm()
                        },
                    )
                }
            }
        }
    }
}

private fun getFake360Label(type: Int) = when (type) {
    1 -> "普通 (libjiagu.so)"; 2 -> "付费 (libjiagu_mips.a)"; 3 -> "企业 (libjiagu_vip.so)"
    else -> "关闭"
}
