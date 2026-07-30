package top.nkbe.art

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val autoSign: Boolean = true,
    val fake360Type: Int = 0,
    val useCustomJks: Boolean = false,
    val jksPath: String = "",
    val jksStorePass: String = "",
    val jksAlias: String = "",
    val jksKeyPass: String = ""
)

/**
 * State bridge connecting Java workflow with Compose UI.
 */
class NeoArtUiController internal constructor(initialLog: String = "等待文件访问授权…") {
    internal var logText by mutableStateOf(initialLog)
        private set
    internal var selectButtonEnabled by mutableStateOf(true)
        private set

    internal var showSettingsSheet by mutableStateOf(false)
    internal var showPresetDialog by mutableStateOf(false)
    internal var settingsState by mutableStateOf(ArkSettingsData())

    var onSaveSettingsHandler: ((ArkSettingsData) -> String?)? = null

    fun appendLog(message: String) {
        logText = if (logText.isBlank()) message else "$logText\n$message"
    }

    fun setSelectEnabled(enabled: Boolean) {
        selectButtonEnabled = enabled
    }

    fun openSettings(data: ArkSettingsData) {
        settingsState = data
        showSettingsSheet = true
    }

    fun closeSettings() {
        showSettingsSheet = false
    }
}

object NeoArtUi {
    @JvmStatic
    fun install(
        activity: ComponentActivity,
        onSelectApk: Runnable,
        onOpenSettings: Runnable,
    ): NeoArtUiController {
        val controller = NeoArtUiController()
        val content = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NeoArtApp(
                    controller = controller,
                    onSelectApk = onSelectApk::run,
                    onOpenSettings = onOpenSettings::run,
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
    onOpenSettings: () -> Unit,
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
        NeoArtScreen(
            controller = controller,
            onSelectApk = onSelectApk,
            onOpenSettings = onOpenSettings,
        )

        if (controller.showSettingsSheet) {
            NeoArtSettingsDialog(
                initialSettings = controller.settingsState,
                onDismiss = { controller.closeSettings() },
                onOpenPresetDialog = { controller.showPresetDialog = true },
                onSave = { data ->
                    val error = controller.onSaveSettingsHandler?.invoke(data)
                    if (error == null) {
                        controller.closeSettings()
                    }
                    error
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

@Composable
internal fun NeoArtScreen(
    controller: NeoArtUiController,
    onSelectApk: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val logScrollState = rememberScrollState()
    LaunchedEffect(controller.logText) {
        logScrollState.animateScrollTo(logScrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(COUITheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
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

        Spacer(Modifier.height(22.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            Text(
                text = "APK 保护工作台",
                color = COUITheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "选择 APK 后将执行结构校验、DEX 保护、对齐及可选签名。",
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
                style = COUITheme.textStyles.paragraph,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = if (controller.selectButtonEnabled) "选择 APK" else "正在处理…",
                    onClick = onSelectApk,
                    enabled = controller.selectButtonEnabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(
                    text = "加固设置",
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            insideMargin = PaddingValues(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "运行日志",
                    color = COUITheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (controller.selectButtonEnabled) "就绪" else "处理中",
                    color = COUITheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(COUITheme.colorScheme.surfaceContainer)
                    .padding(14.dp),
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
}

@Composable
private fun NeoArtSettingsDialog(
    initialSettings: ArkSettingsData,
    onDismiss: () -> Unit,
    onOpenPresetDialog: () -> Unit,
    onSave: (ArkSettingsData) -> String?,
) {
    var soName by remember { mutableStateOf(initialSettings.soName) }
    var stubClassName by remember { mutableStateOf(initialSettings.stubClassName) }
    var savePath by remember { mutableStateOf(initialSettings.savePath) }
    var autoSign by remember { mutableStateOf(initialSettings.autoSign) }
    var fake360Type by remember { mutableStateOf(initialSettings.fake360Type) }
    var useCustomJks by remember { mutableStateOf(initialSettings.useCustomJks) }
    var jksPath by remember { mutableStateOf(initialSettings.jksPath) }
    var jksStorePass by remember { mutableStateOf(initialSettings.jksStorePass) }
    var jksAlias by remember { mutableStateOf(initialSettings.jksAlias) }
    var jksKeyPass by remember { mutableStateOf(initialSettings.jksKeyPass) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f),
                insideMargin = PaddingValues(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "加固设置",
                        color = COUITheme.colorScheme.onSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(14.dp))

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
                                    value = soName,
                                    onValueChange = { soName = it },
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
                                    value = stubClassName,
                                    onValueChange = { stubClassName = it },
                                    hint = "例如：com.ark.safe.StubApp",
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    text = "清空",
                                    onClick = { stubClassName = "" },
                                    modifier = Modifier.height(44.dp)
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            InputFieldLabel("文件保存路径")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StyledTextField(
                                    value = savePath,
                                    onValueChange = { savePath = it },
                                    hint = "默认输出至原文件同目录",
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    text = "清空",
                                    onClick = { savePath = "" },
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
                                    checked = autoSign,
                                    onCheckedChange = { autoSign = it }
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
                                        .clickable { fake360Type = type }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StyledRadioButton(
                                        selected = fake360Type == type,
                                        onClick = { fake360Type = type }
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
                                        text = if (useCustomJks) "使用自订 JKS 校验指纹" else "使用内置 npatch.key 校验指纹",
                                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                        fontSize = 12.sp
                                    )
                                }
                                StyledSwitch(
                                    checked = useCustomJks,
                                    onCheckedChange = { useCustomJks = it }
                                )
                            }

                            AnimatedVisibility(visible = useCustomJks) {
                                Column(
                                    modifier = Modifier.padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    InputFieldLabel("JKS 证书路径")
                                    StyledTextField(
                                        value = jksPath,
                                        onValueChange = { jksPath = it },
                                        hint = "e.g. D:\\mykey.jks"
                                    )

                                    InputFieldLabel("Store 密码")
                                    StyledTextField(
                                        value = jksStorePass,
                                        onValueChange = { jksStorePass = it },
                                        hint = "Store Password"
                                    )

                                    InputFieldLabel("Alias 别名")
                                    StyledTextField(
                                        value = jksAlias,
                                        onValueChange = { jksAlias = it },
                                        hint = "Key Alias"
                                    )

                                    InputFieldLabel("Key 密码")
                                    StyledTextField(
                                        value = jksKeyPass,
                                        onValueChange = { jksKeyPass = it },
                                        hint = "Key Password"
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "保存设置",
                            onClick = {
                                val currentStubClass = if (fake360Type != 0) "com.stub.StubApp" else stubClassName
                                val data = ArkSettingsData(
                                    soName = soName,
                                    stubClassName = currentStubClass,
                                    savePath = savePath,
                                    autoSign = autoSign,
                                    fake360Type = fake360Type,
                                    useCustomJks = useCustomJks,
                                    jksPath = jksPath,
                                    jksStorePass = jksStorePass,
                                    jksAlias = jksAlias,
                                    jksKeyPass = jksKeyPass
                                )
                                val err = onSave(data)
                                errorMessage = err
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoNamePresetDialog(
    onSelectPreset: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = listOf(
        "ArkStub",
        "libdexhelper.so",
        "libsecmain.so",
        "libjiagu.so",
        "libshell.so",
        "libprotect.so",
        "libbaiduprotect.so",
        "libtxyu.so",
        "libns_apkshell.so",
        "libvmp.so"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
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
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    presets.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectPreset(preset) }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = preset,
                                color = COUITheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            )
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

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NeoArtScreenPreview() {
    val controller = remember {
        NeoArtUiController(
            "Neo Art 加固已就绪\nAPK 结构校验与安全处理将在这里显示。",
        )
    }
    NeoArtApp(
        controller = controller,
        onSelectApk = {},
        onOpenSettings = {},
    )
}
