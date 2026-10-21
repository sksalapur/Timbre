package com.music.bitchord.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.ic_google_g
import com.music.bitchord.sharedui.resources.ic_logo
import org.jetbrains.compose.resources.painterResource
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.MESH_CYCLE_MILLIS
import com.music.bitchord.ui.player.MeshPalette
import com.music.bitchord.ui.player.cyclingMeshPalette

/** Timbre signature brand colors */
private val TimbreCyan = Color(0xFF00E5FF)
private val TimbreCyanMuted = Color(0xFF00B0FF)
private val TimbreSapphire = Color(0xFF0D47A1)
private val TimbreObsidian = Color(0xFF07090E)

/**
 * Timbre signature audiophile mesh palettes: electric cyan, cobalt sapphire,
 * emerald hi-res, and deep obsidian black.
 * Replaces upstream BitChord red, pink, and ember gradients with Timbre's dark studio identity.
 */
private val TimbreWelcomePalettes: List<MeshPalette> = listOf(
    // Timbre Signature: Electric Cyan, Deep Sapphire, Studio Violet Glow, Obsidian Abyss
    MeshPalette(listOf(Color(0xFF00E5FF), Color(0xFF0D47A1), Color(0xFF7C4DFF), Color(0xFF07090E))),
    // Ocean Hi-Res: Sky Cyan, Royal Navy, Deep Teal, Midnight
    MeshPalette(listOf(Color(0xFF00B0FF), Color(0xFF1565C0), Color(0xFF00BFA5), Color(0xFF0A1128))),
    // Aurora Studio: Hi-Res Emerald, Cyan Glow, Deep Cobalt, Cosmic Indigo
    MeshPalette(listOf(Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFF311B92), Color(0xFF07090E))),
    // Deep Master: Electric Azure, Radiant Purple, Sapphire, Dark Void
    MeshPalette(listOf(Color(0xFF29B6F6), Color(0xFF651FFF), Color(0xFF1E88E5), Color(0xFF050B14))),
)

/** The account connect page: blends YouTube connection with Timbre's signature sapphire & cyan backdrop. */
private val TimbreConnectPalettes: List<MeshPalette> = listOf(
    MeshPalette(listOf(Color(0xFFE53935), Color(0xFF00E5FF), Color(0xFF1E88E5), Color(0xFF07090E))),
    MeshPalette(listOf(Color(0xFFFF3D00), Color(0xFF0D47A1), Color(0xFF7C4DFF), Color(0xFF0A1128))),
    MeshPalette(listOf(Color(0xFFD32F2F), Color(0xFF00B0FF), Color(0xFF311B92), Color(0xFF050B14))),
    MeshPalette(listOf(Color(0xFFFF5252), Color(0xFF00E5FF), Color(0xFF1565C0), Color(0xFF07090E))),
)

/**
 * The two welcome pages: a name for Listen Together, then the YouTube sign-in.
 * Styled in Timbre's audiophile dark theme with glowing cyan accents and bit-perfect aesthetics.
 */
@Composable
fun OnboardingScreen(
    signedIn: Boolean,
    initialName: String,
    /** Saves the party name. Called on Next, before the second page. */
    onNameChosen: (String) -> Unit,
    /** Opens the Google sign-in. The caller finishes onboarding once it lands. */
    onSignIn: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableIntStateOf(PAGE_NAME) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = page == PAGE_CONNECT) { page = PAGE_NAME }
    LaunchedEffect(signedIn, page) {
        if (signedIn && page == PAGE_CONNECT) onFinish()
    }

    // Cycles through Timbre's dark audiophile cyan/sapphire mesh palettes
    val palette = cyclingMeshPalette(if (page == PAGE_NAME) TimbreWelcomePalettes else TimbreConnectPalettes)

    Box(
        modifier
            .fillMaxSize()
            .background(TimbreObsidian)
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        MeshGradientBackground(
            palette = palette,
            trackKey = page,
            continuous = true,
            driftMillis = 6_000,
            colorFadeMillis = MESH_CYCLE_MILLIS,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState > initialState
                val slide = tween<IntOffset>(420)
                (slideInHorizontally(slide) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(320, 100)))
                    .togetherWith(
                        slideOutHorizontally(slide) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)),
                    )
            },
            label = "onboardingPage",
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
        ) { shown ->
            OnboardingPage {
                when (shown) {
                    PAGE_NAME -> NamePage(
                        name = name,
                        onNameChange = { name = it.take(NAME_LIMIT) },
                        onNext = next@{
                            val chosen = name.trim()
                            if (chosen.isEmpty()) return@next
                            focusManager.clearFocus()
                            onNameChosen(chosen)
                            if (signedIn) onFinish() else page = PAGE_CONNECT
                        },
                    )
                    else -> ConnectPage(
                        onBack = { page = PAGE_NAME },
                        onSignIn = onSignIn,
                        onSkip = onFinish,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .padding(horizontal = 28.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.NamePage(
    name: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    val keyboardUp = WindowInsets.isImeVisible
    AnimatedVisibility(
        visible = !keyboardUp,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            Spacer(Modifier.height(56.dp))
            Icon(
                painter = painterResource(Res.drawable.ic_logo),
                contentDescription = "Timbre",
                tint = TimbreCyan,
                modifier = Modifier.size(width = 46.dp, height = 30.dp),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Title(stringResource(R.string.onboarding_title))

    Spacer(Modifier.weight(1f))

    Text(
        text = stringResource(R.string.onboarding_name_prompt),
        style = MaterialTheme.typography.titleMedium,
        fontSize = 17.sp,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.padding(start = 4.dp),
    )
    Spacer(Modifier.height(12.dp))
    NameField(value = name, onValueChange = onNameChange, onDone = onNext)
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.onboarding_name_footer),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    Spacer(Modifier.height(28.dp))
    PillButton(
        label = stringResource(R.string.onboarding_next),
        onClick = onNext,
        enabled = name.isNotBlank(),
        primary = true,
        primaryColor = TimbreCyan,
        primaryTextColor = TimbreObsidian,
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ColumnScope.ConnectPage(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    BackButton(onBack)
    Spacer(Modifier.height(20.dp))
    Title(stringResource(R.string.onboarding_connect_title))
    Spacer(Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.onboarding_connect_body),
        style = MaterialTheme.typography.bodyLarge,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        color = Color.White.copy(alpha = 0.75f),
    )

    Spacer(Modifier.weight(1f))

    PillButton(
        label = stringResource(R.string.onboarding_sign_in_google),
        onClick = onSignIn,
        primary = true,
        primaryColor = Color.White,
        primaryTextColor = Color.Black,
        leading = {
            Image(
                painter = painterResource(Res.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
    )
    Spacer(Modifier.height(12.dp))
    PillButton(
        label = stringResource(R.string.onboarding_skip_sign_in),
        onClick = onSkip,
        primary = false,
    )
    Spacer(Modifier.height(16.dp))
}

/** Timbre heading with brand accent highlight */
@Composable
private fun Title(text: String) {
    val annotated = remember(text) {
        if (text.contains("Timbre")) {
            val parts = text.split("Timbre")
            buildAnnotatedString {
                for (i in parts.indices) {
                    append(parts[i])
                    if (i < parts.size - 1) {
                        withStyle(SpanStyle(color = TimbreCyan, fontWeight = FontWeight.W900)) {
                            append("Timbre")
                        }
                    }
                }
            }
        } else {
            AnnotatedString(text)
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = 40.sp,
            lineHeight = 44.sp,
            letterSpacing = (-1.2).sp,
        ),
        color = Color.White,
    )
}

/**
 * Text field with sleek Timbre cyan focus border and cyan cursor.
 */
@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val style = MaterialTheme.typography.displayLarge.copy(
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.6).sp,
        color = Color.White,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(
                1.dp,
                if (value.isNotEmpty()) TimbreCyan.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.18f),
                CircleShape,
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = stringResource(R.string.onboarding_name_hint),
                style = style,
                color = Color.White.copy(alpha = 0.38f),
                maxLines = 1,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(TimbreCyan),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Full-width capsule pill button with customizable primary theme accent.
 */
@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    enabled: Boolean = true,
    primaryColor: Color = Color.White,
    primaryTextColor: Color = Color.Black,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 900f), label = "pillScale")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(180), label = "pillAlpha")
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(if (primary) primaryColor else Color.White.copy(alpha = 0.16f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 17.sp,
            fontWeight = FontWeight.W700,
            color = if (primary) primaryTextColor else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = BitChordIcons.ChevronRight,
            contentDescription = stringResource(R.string.onboarding_back),
            tint = Color.White,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { scaleX = -1f },
        )
    }
}

private const val PAGE_NAME = 0
private const val PAGE_CONNECT = 1
private const val NAME_LIMIT = 40
