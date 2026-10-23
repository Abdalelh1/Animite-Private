package com.imashnake.animite.social.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private val manropeFontFamily = FontFamily.SansSerif

/**
 * Evening Mount Fuji banner drawn with Canvas.
 */
@Composable
fun FujiBanner(
    modifier: Modifier = Modifier,
    appName: String = "Animeta",
    onSettingsClick: (() -> Unit)? = null,
) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Evening sky
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF23355E),
                        Color(0xFF5F81E2),
                        Color(0xFFBF98B7),
                        Color(0xFFE3A98F),
                    ),
                ),
            )

            // Sun + halo
            val sunCenter = Offset(w * 0.74f, h * 0.36f)
            drawCircle(Color(0xFFFFE3B3).copy(alpha = 0.18f), radius = h * 0.22f, center = sunCenter)
            drawCircle(Color(0xFFFFE3B3), radius = h * 0.145f, center = sunCenter)

            // Cloud
            drawOval(
                color = Color.White.copy(alpha = 0.20f),
                topLeft = Offset(w * 0.08f, h * 0.26f),
                size = Size(w * 0.26f, h * 0.08f),
            )

            // Mount Fuji silhouette
            val peakX = w * 0.385f
            val fuji = Path().apply {
                moveTo(-w * 0.05f, h)
                lineTo(w * 0.30f, h * 0.52f)
                quadraticTo(peakX, h * 0.30f, w * 0.47f, h * 0.52f)
                lineTo(w * 1.05f, h)
                close()
            }
            drawPath(fuji, Color(0xFF16283D))

            // Snow cap
            val snow = Path().apply {
                moveTo(w * 0.335f, h * 0.475f)
                quadraticTo(peakX, h * 0.335f, w * 0.435f, h * 0.475f)
                quadraticTo(w * 0.405f, h * 0.53f, peakX, h * 0.475f)
                quadraticTo(w * 0.365f, h * 0.53f, w * 0.335f, h * 0.475f)
                close()
            }
            drawPath(snow, Color(0xFFEAF1F8).copy(alpha = 0.92f))
        }

        // App logo
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 28.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF101418).copy(alpha = 0.55f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp),
                )
            }
            Text(
                text = appName,
                fontFamily = manropeFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = Color.White,
            )
        }

        if (onSettingsClick != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF101418).copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 36.dp, end = 20.dp),
            ) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = Color(0xFF39404A),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var isVisible by rememberSaveable { mutableStateOf(false) }
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    imageVector = if (isVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = null,
                )
            }
        },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = Color(0xFF39404A),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PrimaryAuthButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFA5C8FE),
            contentColor = Color(0xFF003062),
            disabledContainerColor = Color(0xFFA5C8FE).copy(alpha = 0.4f),
            disabledContentColor = Color(0xFF003062).copy(alpha = 0.4f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color(0xFF003062),
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GoogleColoredIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.22f
        val radius = (w - stroke) / 2f
        val center = Offset(w / 2f, h / 2f)

        // Blue right / bottom-right
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        // Green bottom
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        // Yellow bottom-left / left
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        // Red top
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        // Blue horizontal crossbar
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(center.x, center.y),
            end = Offset(w - stroke / 2f, center.y),
            strokeWidth = stroke
        )
    }
}

@Composable
fun GoogleAuthButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF1E242B),
            contentColor = Color.White
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        GoogleColoredIcon(modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = "المتابعة عبر Google",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AuthDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        Text(
            text = "أو تابع عبر",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
}

/**
 * Unified Auth Content embedding the Fuji banner with Login & SignUp flows.
 */
@Composable
fun AuthContent(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
    onAuthSuccess: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var isSignUpMode by rememberSaveable { mutableStateOf(false) }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(false) }
    var acceptedTerms by rememberSaveable { mutableStateOf(true) }

    val isLoading = uiState is AuthUiState.Loading

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (!idToken.isNullOrBlank()) {
                viewModel.signInWithGoogle(idToken)
            } else {
                viewModel.setErrorMessage("تعذر استرجاع رمز المصادقة من Google.")
            }
        } catch (e: ApiException) {
            viewModel.setErrorMessage("فشل تسجيل الدخول عبر Google (رمز ${e.statusCode})")
        } catch (e: Exception) {
            viewModel.setErrorMessage(e.localizedMessage ?: "حدث خطأ أثناء الاتصال بحساب Google")
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onAuthSuccess?.invoke()
            viewModel.clearState()
        }
    }

    val onGoogleClick = {
        val webClientId = runCatching {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else null
        }.getOrNull() ?: "924518113687-jge5r6p0tm7ln69bi3rtn8ivnrgk6cl0.apps.googleusercontent.com"
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        FujiBanner(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isSignUpMode) 148.dp else 238.dp),
            onSettingsClick = onSettingsClick,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(top = if (isSignUpMode) 20.dp else 24.dp),
        ) {
            Text(
                text = if (isSignUpMode) "إنشاء حساب جديد" else "أهلاً بعودتك",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (isSignUpMode)
                    "انضم إلينا وتابع أنميك المفضل وشارك مجتمعك."
                else
                    "سجّل دخولك لمتابعة الأنمي والمانغا ومزامنة قوائمك.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            // Status / Error Messages
            AnimatedVisibility(visible = uiState is AuthUiState.Error) {
                val error = (uiState as? AuthUiState.Error)?.error.orEmpty()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = uiState is AuthUiState.Message) {
                val msg = (uiState as? AuthUiState.Message)?.message.orEmpty()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (isSignUpMode) {
                // Sign Up Fields
                AuthTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "اسم المستخدم",
                    leadingIcon = Icons.Rounded.Person,
                )

                Spacer(Modifier.height(12.dp))

                AuthTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "البريد الإلكتروني",
                    leadingIcon = Icons.Rounded.Email,
                    keyboardType = KeyboardType.Email,
                )

                Spacer(Modifier.height(12.dp))

                AuthPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "كلمة المرور",
                    supportingText = "٨ أحرف على الأقل",
                )

                Spacer(Modifier.height(12.dp))

                val passwordsMismatch = confirmPassword.isNotEmpty() && confirmPassword != password
                AuthPasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "تأكيد كلمة المرور",
                    isError = passwordsMismatch,
                    supportingText = if (passwordsMismatch) "كلمتا المرور غير متطابقتين" else null,
                )

                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(
                        checked = acceptedTerms,
                        onCheckedChange = { acceptedTerms = it },
                    )
                    val termsText = buildAnnotatedString {
                        append("أوافق على ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                            append("الشروط والأحكام")
                        }
                        append(" و")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                            append("سياسة الخصوصية")
                        }
                        append(" الخاصة بالتطبيق.")
                    }
                    Text(
                        text = termsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))

                val isFormValid = username.isNotBlank() &&
                    email.isNotBlank() &&
                    password.length >= 8 &&
                    confirmPassword == password &&
                    acceptedTerms

                PrimaryAuthButton(
                    text = "إنشاء الحساب",
                    onClick = {
                        viewModel.signUp(email, password, username, username)
                    },
                    enabled = isFormValid,
                    isLoading = isLoading,
                )
            } else {
                // Login Fields
                AuthTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "البريد الإلكتروني",
                    leadingIcon = Icons.Rounded.Email,
                    keyboardType = KeyboardType.Email,
                )

                Spacer(Modifier.height(14.dp))

                AuthPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "كلمة المرور",
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                    )
                    Text(
                        text = "تذكّرني",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.resetPassword(email) }) {
                        Text("نسيت كلمة المرور؟")
                    }
                }

                Spacer(Modifier.height(14.dp))

                PrimaryAuthButton(
                    text = "تسجيل الدخول",
                    onClick = {
                        viewModel.signIn(email, password)
                    },
                    enabled = email.isNotBlank() && password.isNotBlank(),
                    isLoading = isLoading,
                )
            }

            Spacer(Modifier.height(20.dp))
            AuthDivider()
            Spacer(Modifier.height(16.dp))

            GoogleAuthButton(
                onClick = onGoogleClick,
                enabled = !isLoading
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (isSignUpMode) "لديك حساب بالفعل؟" else "ليس لديك حساب؟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    isSignUpMode = !isSignUpMode
                    viewModel.clearState()
                }) {
                    Text(
                        text = if (isSignUpMode) "تسجيل الدخول" else "إنشاء حساب",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
