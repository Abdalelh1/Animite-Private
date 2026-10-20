package com.imashnake.animite.profile.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imashnake.animite.core.ui.LocalPaddings
import com.imashnake.animite.profile.R

@Composable
fun Login(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1E2630),
            contentColor = Color.White
        ),
        modifier = modifier
    ) {
        Row(Modifier.wrapContentWidth()) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = stringResource(R.string.log_in),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(R.string.log_in),
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = LocalPaddings.current.tiny)
            )
        }
    }
}
