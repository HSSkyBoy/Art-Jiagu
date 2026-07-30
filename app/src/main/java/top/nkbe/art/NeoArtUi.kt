package top.nkbe.art

import androidx.activity.ComponentActivity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController

/**
 * Small state bridge that lets the existing Java hardening workflow drive the new Compose UI.
 */
class NeoArtUiController internal constructor(initialLog: String = "等待文件访问授权…") {
    internal var logText by mutableStateOf(initialLog)
        private set
    internal var selectButtonEnabled by mutableStateOf(true)
        private set

    fun appendLog(message: String) {
        logText = if (logText.isBlank()) message else "$logText\n$message"
    }

    fun setSelectEnabled(enabled: Boolean) {
        selectButtonEnabled = enabled
    }
}

object NeoArtUi {
    /**
     * Hosts the COUI Compose screen without rewriting the existing Java processing pipeline.
     */
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
            logText = controller.logText,
            selectEnabled = controller.selectButtonEnabled,
            onSelectApk = onSelectApk,
            onOpenSettings = onOpenSettings,
        )
    }
}

/**
 * COUI replacement for activity_main.xml. All mutable state and actions are hoisted for testing.
 */
@Composable
internal fun NeoArtScreen(
    logText: String,
    selectEnabled: Boolean,
    onSelectApk: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val logScrollState = rememberScrollState()
    LaunchedEffect(logText) {
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
                    text = if (selectEnabled) "选择 APK" else "正在处理…",
                    onClick = onSelectApk,
                    enabled = selectEnabled,
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
                    text = if (selectEnabled) "就绪" else "处理中",
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
                        text = logText,
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
