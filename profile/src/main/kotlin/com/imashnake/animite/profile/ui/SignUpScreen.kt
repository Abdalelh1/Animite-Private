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
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Sign-up screen — Animite style (Material 3 Expressive, Manrope,
 * evening Fuji banner). UI only; wire the callbacks to your ViewModel.
 */
@Composable
fun SignUpScreen(
    onSignUpClick: (username: String, email: String, password: String) -> Unit,
    onGoogleClick: () -> Unit,
    onLoginClick: () -> Unit,
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var acceptedTerms by rememberSaveable { mutableStateOf(true) }

    val passwordsMismatch = confirmPassword.isNotEmpty() && confirmPassword != password
    val isFormValid = username.isNotBlank() &&
        email.isNotBlank() &&
        password.length >= 8 &&
        confirmPassword == password &&
        acceptedTerms

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        FujiBanner(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(top = 20.dp),
        ) {
            Text(
                text = "إنشاء حساب جديد",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "انضم إلينا وتابع أنميك المفضل وشارك مجتمعك.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp),
            )

            Spacer(Modifier.height(20.dp))

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

            PrimaryAuthButton(
                text = "إنشاء الحساب",
                onClick = { onSignUpClick(username, email, password) },
                enabled = isFormValid,
            )

            Spacer(Modifier.height(18.dp))
            AuthDivider()
            Spacer(Modifier.height(14.dp))

            GoogleAuthButton(onClick = onGoogleClick)

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "لديك حساب بالفعل؟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onLoginClick) {
                    Text("تسجيل الدخول", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
