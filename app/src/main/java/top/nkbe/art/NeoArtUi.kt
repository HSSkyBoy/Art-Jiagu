package top.nkbe.art

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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

/**
 * Settings data model for Neo Art UI
 */
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

/**
 * State bridge connecting Java workflow with Compose 3-page UI.
 */
class NeoArtUiController internal constructor(initialLog: String = "等待文件访问授权…") {
    internal var selectedTab by mutableIntStateOf(0)
    internal var logText by mutableStateOf(initialLog)
        private set
    internal var selectButtonEnabled by mutableStateOf(true)
        private set

    internal var selectedApkPath by mutableStateOf<String?>(null)
    internal var showPresetDialog by mutableStateOf(false)
    internal var settingsState by mutableStateOf(ArkSettingsData())

    var onSaveSettingsHandler: ((ArkSettingsData) -> String?)? = null

    fun appendLog(message: String) {
        logText = if (logText.isBlank()) message else "$logText\n$message"
    }

    fun clearLog() {
        logText = "日志已清空"
    }

    fun setSelectEnabled(enabled: Boolean) {
        selectButtonEnabled = enabled
    }

    fun updateSelectedApk(path: String) {
        selectedApkPath = path
    }

    fun loadSettings(data: ArkSettingsData) {
        settingsState = data
    }
}

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
            if (initial != null) {
                controller.settingsState = initial
            }
        } catch (_: Exception) {}

        val content = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NeoArtApp(
                    controller = controller,
                    onSelectApk = onSelectApk::run,
                    onLoadSettings = {
                        try {
                            val updated = onLoadSettings.call()
                            if (updated != null) {
                                controller.settingsState = updated
                            }
                        } catch (_: Exception) {}
                    },
                )
            }
        }
        activity.setContentView(content)
        return controller
    }
}

@Composable
private fun NeoArtApp(
    controller: NeoArtUiController,
    onSelectApk: () -> Unit,
    onLoadSettings: () -> Unit,
) {
    val context = LocalContext.current
    val keyColor = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Color(ContextCompat.getColor(context, android.R.color.system_accent1_500))
            } catch (_: Exception) {
                Color(0xFF4B70F5)
            }
        } else {
            Color(0xFF4B70F5)
        }
    }

    val themeController = remember(keyColor) {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.MonetSystem,
            keyColor = keyColor,
        )
    }

    COUITheme(controller = themeController) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(COUITheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (controller.selectedTab) {
                    0 -> ManagementPage(
                        controller = controller,
                        onSelectApk = onSelectApk,
                        onGoToLogs = { controller.selectedTab = 2 },
                        onGoToSettings = { controller.selectedTab = 1 }
                    )
                    1 -> SettingsPage(
                        controller = controller,
                        onOpenPresetDialog = { controller.showPresetDialog = true }
                    )
                    2 -> LogsPage(controller = controller)
                }
            }

            NeoArtBottomBar(
                selectedTab = controller.selectedTab,
                onSelectTab = { index ->
                    if (index == 1) {
                        onLoadSettings()
                    }
                    controller.selectedTab = index
                }
            )
        }

        if (controller.showPresetDialog) {
            SoNamePresetDialog(
                onSelectPreset = { preset ->
                    controller.settingsState = controller.settingsState.copy(soName = preset)
                    controller.showPresetDialog = false
                },
                onDismiss = { controller.showPresetDialog = false }
            )
        }
    }
}

/**
 * Page 1: 管理 (Management Workbench)
 */
@Composable
private fun ManagementPage(
    controller: NeoArtUiController,
    onSelectApk: () -> Unit,
    onGoToLogs: () -> Unit,
    onGoToSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Neo Art 加固",
                color = COUITheme.colorScheme.onBackground,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "By: HSSkyBoy · 合法软件保护与安全研究",
                color = COUITheme.colorScheme.onBackgroundVariant,
                style = COUITheme.textStyles.paragraph,
            )
        }

        // Card 1: APK Workbench
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column {
                Text(
                    text = "APK 保护工作台",
                    color = COUITheme.colorScheme.onSurface,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (controller.selectedApkPath != null)
                        "已选择: ${controller.selectedApkPath}"
                    else
                        "选择目标 APK 文件后将自动执行 DEX 保护、加固及可选签名。",
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = if (controller.selectButtonEnabled) "选择 APK 并加固" else "正在处理…",
                        onClick = onSelectApk,
                        enabled = controller.selectButtonEnabled,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }

        // Card 2: Quick Settings Overview
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "当前加固策略",
                        color = COUITheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(
                        text = "修改配置",
                        onClick = onGoToSettings
                    )
                }
                Spacer(Modifier.height(8.dp))
                val s = controller.settingsState
                Text(
                    text = "• 壳 SO 名称: ${s.soName}\n" +
                            "• 壳类名: ${s.stubClassName}\n" +
                            "• 自动签名: ${if (s.autoSign) "开启" else "关闭"}\n" +
                            "• 偽360特征: ${getFake360Name(s.fake360Type)}\n" +
                            "• 签名证书: ${if (s.useCustomJks) "自定义 JKS" else "内置 npatch.key"}",
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }

        // Card 3: Recent Log Snippet
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最新日志",
                        color = COUITheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(
                        text = "查看完整日志",
                        onClick = onGoToLogs
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(COUITheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = controller.logText.takeLast(300),
                        color = COUITheme.colorScheme.onSurfaceContainer,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Page 2: 设置 (Settings)
 */
@Composable
private fun SettingsPage(
    controller: NeoArtUiController,
    onOpenPresetDialog: () -> Unit,
) {
    var settings by remember(controller.settingsState) { mutableStateOf(controller.settingsState) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "加固设置",
            color = COUITheme.colorScheme.onBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))

        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFD2D2), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = errorMessage!!,
                    color = Color(0xFFD32F2F),
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Basic Config
            CardFieldSection(title = "自定义 SO 与壳类名") {
                InputFieldLabel("SO 名称")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledTextField(
                        value = settings.soName,
                        onValueChange = { settings = settings.copy(soName = it) },
                        hint = "ArkStub",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        text = "预设",
                        onClick = onOpenPresetDialog,
                        modifier = Modifier.height(44.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                InputFieldLabel("自定义壳类名 (支持中文包名)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledTextField(
                        value = settings.stubClassName,
                        onValueChange = { settings = settings.copy(stubClassName = it) },
                        hint = "例如：com.ark.safe.StubApp",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        text = "清空",
                        onClick = { settings = settings.copy(stubClassName = "") },
                        modifier = Modifier.height(44.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                InputFieldLabel("文件保存路径")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StyledTextField(
                        value = settings.savePath,
                        onValueChange = { settings = settings.copy(savePath = it) },
                        hint = "默认输出至原文件同目录",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        text = "清空",
                        onClick = { settings = settings.copy(savePath = "") },
                        modifier = Modifier.height(44.dp)
                    )
                }
            }

            // Section 2: Auto Sign Switch
            CardFieldSection(title = "签名配置") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自动签名",
                            color = COUITheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "开启后自动生成 APK 签名并绑定 C++ 签名校验",
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp
                        )
                    }
                    StyledSwitch(
                        checked = settings.autoSign,
                        onCheckedChange = { settings = settings.copy(autoSign = it) }
                    )
                }
            }

            // Section 3: Fake 360 Signatures
            CardFieldSection(title = "360 伪加固识别特征") {
                Text(
                    text = "仅添加工具识别特征，启用后壳类名将自动使用 com.stub.StubApp",
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                val options = listOf(
                    0 to "关闭",
                    1 to "普通 (assets/libjiagu.so)",
                    2 to "付费 (assets/libjiagu_mips.a)",
                    3 to "企业 (assets/libjiagu_vip.so)"
                )
                options.forEach { (type, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { settings = settings.copy(fake360Type = type) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StyledRadioButton(
                            selected = settings.fake360Type == type,
                            onClick = { settings = settings.copy(fake360Type = type) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = COUITheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Section 4: Custom JKS Certificate
            CardFieldSection(title = "自定义 JKS 证书签名") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "使用自定义 JKS 证书",
                            color = COUITheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (settings.useCustomJks) "使用自订 JKS 校验指纹" else "使用内置 npatch.key 校验指纹",
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp
                        )
                    }
                    StyledSwitch(
                        checked = settings.useCustomJks,
                        onCheckedChange = { settings = settings.copy(useCustomJks = it) }
                    )
                }

                AnimatedVisibility(visible = settings.useCustomJks) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InputFieldLabel("JKS 证书路径")
                        StyledTextField(
                            value = settings.jksPath,
                            onValueChange = { settings = settings.copy(jksPath = it) },
                            hint = "e.g. D:\\mykey.jks"
                        )

                        InputFieldLabel("Store 密码")
                        StyledTextField(
                            value = settings.jksStorePass,
                            onValueChange = { settings = settings.copy(jksStorePass = it) },
                            hint = "Store Password"
                        )

                        InputFieldLabel("Alias 别名")
                        StyledTextField(
                            value = settings.jksAlias,
                            onValueChange = { settings = settings.copy(jksAlias = it) },
                            hint = "Key Alias"
                        )

                        InputFieldLabel("Key 密码")
                        StyledTextField(
                            value = settings.jksKeyPass,
                            onValueChange = { settings = settings.copy(jksKeyPass = it) },
                            hint = "Key Password"
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(
            text = "保存设置",
            onClick = {
                val err = controller.onSaveSettingsHandler?.invoke(settings)
                errorMessage = err
                if (err == null) {
                    controller.loadSettings(settings)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }
}

/**
 * Page 3: 日志 (Logs Console)
 */
@Composable
private fun LogsPage(controller: NeoArtUiController) {
    val context = LocalContext.current
    val logScrollState = rememberScrollState()

    LaunchedEffect(controller.logText) {
        logScrollState.animateScrollTo(logScrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "运行日志主控台",
                color = COUITheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "复制",
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("NeoArtLog", controller.logText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
                    }
                )
                TextButton(
                    text = "清空",
                    onClick = { controller.clearLog() }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(COUITheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            SelectionContainer {
                Text(
                    text = controller.logText,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(logScrollState),
                    color = COUITheme.colorScheme.onSurfaceContainer,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                )
            }
        }
    }
}

/**
 * Liquid Glassmorphic Bottom Navigation Bar
 */
@Composable
private fun NeoArtBottomBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val glassBg = COUITheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
        val glassBorder = COUITheme.colorScheme.primary.copy(alpha = 0.18f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(glassBg)
                .border(
                    width = 1.dp,
                    color = glassBorder,
                    shape = RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val items = listOf(
                    Triple(0, "管理", "⚡"),
                    Triple(1, "设置", "⚙️"),
                    Triple(2, "日志", "📜")
                )

                items.forEach { (index, label, icon) ->
                    val selected = selectedTab == index
                    val animColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selected) COUITheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent,
                        label = "tabBg"
                    )
                    val animTextColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selected) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurfaceVariantSummary,
                        label = "tabText"
                    )

                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(animColor)
                            .clickable { onSelectTab(index) }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = icon,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = label,
                            color = animTextColor,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private data class SoNamePresetItem(val title: String, val name: String)

@Composable
private fun rememberSoNamePresets(context: Context): List<SoNamePresetItem> {
    return remember(context) {
        try {
            val json = context.assets.open("so_name_presets.json").bufferedReader().use { it.readText() }
            val array = org.json.JSONArray(json)
            val list = mutableListOf<SoNamePresetItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val title = obj.optString("title", "").trim()
                val name = obj.optString("name", "").trim()
                if (title.isNotEmpty() && name.isNotEmpty()) {
                    list.add(SoNamePresetItem(title, name))
                }
            }
            list
        } catch (_: Exception) {
            listOf(
                SoNamePresetItem("Ark默认", "ArkStub"),
                SoNamePresetItem("腾讯乐固", "tup"),
                SoNamePresetItem("梆梆安全", "DexHelper"),
                SoNamePresetItem("爱加密", "exec"),
                SoNamePresetItem("阿里聚安全", "mobisec")
            )
        }
    }
}

@Composable
private fun SoNamePresetDialog(
    onSelectPreset: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val presets = rememberSoNamePresets(context)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f)
                .padding(16.dp),
            insideMargin = PaddingValues(18.dp)
        ) {
            Column {
                Text(
                    text = "选择预设 SO 名称",
                    color = COUITheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "包含主流加固厂商特征名称（点击选择）",
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectPreset(preset.name) }
                                .padding(vertical = 10.dp, horizontal = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = preset.title,
                                        color = COUITheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "SO 文件名: lib${preset.name}.so",
                                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    text = preset.name,
                                    color = COUITheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun CardFieldSection(
    title: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(COUITheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = title,
                color = COUITheme.colorScheme.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InputFieldLabel(text: String) {
    Text(
        text = text,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(COUITheme.colorScheme.background, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty() && hint.isNotEmpty()) {
            Text(
                text = hint,
                color = COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = COUITheme.colorScheme.onSurface,
                fontSize = 14.sp
            ),
            cursorBrush = SolidColor(COUITheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StyledSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (checked) COUITheme.colorScheme.primary
                else COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.3f)
            )
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun StyledRadioButton(
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(20.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) COUITheme.colorScheme.primary
                else COUITheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f)
            )
            .clickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
            )
        }
    }
}

private fun getFake360Name(type: Int): String {
    return when (type) {
        1 -> "普通 (libjiagu.so)"
        2 -> "付费 (libjiagu_mips.a)"
        3 -> "企业 (libjiagu_vip.so)"
        else -> "关闭"
    }
}
