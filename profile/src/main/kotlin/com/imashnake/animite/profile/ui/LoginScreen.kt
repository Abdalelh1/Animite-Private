package com.imashnake.animite.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Login screen — Animite style (Material 3 Expressive, Manrope,
 * evening Fuji banner). UI only; wire the callbacks to your ViewModel.
 */
@Composable
fun LoginScreen(
    onLoginClick: (email: String, password: String) -> Unit,
    onGoogleClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        FujiBanner(
            modifier = Modifier
                .fillMaxWidth()
                .height(238.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(top = 24.dp),
        ) {
            Text(
                text = "أهلاً بعودتك",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "سجّل دخولك لمتابعة الأنمي والمانغا ومزامنة قوائمك.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(24.dp))

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
                TextButton(onClick = onForgotPasswordClick) {
                    Text("نسيت كلمة المرور؟")
                }
            }

            Spacer(Modifier.height(14.dp))

            PrimaryAuthButton(
                text = "تسجيل الدخول",
                onClick = { onLoginClick(email, password) },
                enabled = email.isNotBlank() && password.isNotBlank(),
            )

            Spacer(Modifier.height(22.dp))
            AuthDivider()
            Spacer(Modifier.height(16.dp))

            GoogleAuthButton(onClick = onGoogleClick)

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "ليس لديك حساب؟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onSignUpClick) {
                    Text("إنشاء حساب", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
