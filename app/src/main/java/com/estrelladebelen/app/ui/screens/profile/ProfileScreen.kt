package com.estrelladebelen.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.estrelladebelen.app.R
import com.estrelladebelen.app.ui.theme.*

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val user by viewModel.userProfile.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LavenderBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(32.dp))

        // Avatar + name
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(name = user?.displayName ?: "")
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = user?.displayName ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = PurpleTextPrimary
                )
                Text(
                    text = user?.email ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LavenderTextSecond
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Stats
        SectionLabel(stringResource(R.string.profile_stats))
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                value = "${user?.totalSessions ?: 0}",
                label = stringResource(R.string.profile_sessions),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = "${user?.totalMinutes ?: 0} min",
                label = stringResource(R.string.profile_time),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                value = "🔥 ${user?.streak ?: 0}",
                label = stringResource(R.string.profile_streak),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(28.dp))

        // Library actions
        SectionLabel(stringResource(R.string.profile_library))
        Spacer(Modifier.height(10.dp))
        ProfileActionRow(icon = Icons.Filled.Favorite, label = stringResource(R.string.profile_favorites)) {}
        ProfileActionRow(icon = Icons.Filled.Download, label = stringResource(R.string.profile_downloads)) {}

        Spacer(Modifier.height(28.dp))

        // Settings
        SectionLabel(stringResource(R.string.profile_settings))
        Spacer(Modifier.height(10.dp))

        var notificationsOn by remember { mutableStateOf(user?.notificationsEnabled ?: false) }
        LaunchedEffect(user) { notificationsOn = user?.notificationsEnabled ?: false }

        ProfileToggleRow(
            icon = Icons.Filled.Notifications,
            label = stringResource(R.string.settings_notifications),
            checked = notificationsOn,
            onCheckedChange = {
                notificationsOn = it
                viewModel.updateNotifications(it, user?.notificationTime ?: "08:00")
            }
        )
        ProfileActionRow(
            icon = Icons.Filled.Language,
            label = stringResource(R.string.settings_language),
            trailingLabel = stringResource(R.string.settings_language_soon)
        ) {}
        ProfileActionRow(icon = Icons.Filled.Info, label = stringResource(R.string.settings_about)) {}

        Spacer(Modifier.height(28.dp))

        OutlinedButton(
            onClick = { viewModel.signOut(onSignOut) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LavenderPrimaryDark)
        ) {
            Text(stringResource(R.string.profile_sign_out))
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun UserAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(LavenderContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = LavenderPrimaryDark,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LavenderSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = LavenderPrimaryDark
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = LavenderTextSecond
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LavenderTextSecond
    )
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    label: String,
    trailingLabel: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = LavenderSurface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = LavenderPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = PurpleTextPrimary, modifier = Modifier.weight(1f))
            if (trailingLabel != null) {
                Text(trailingLabel, style = MaterialTheme.typography.bodySmall, color = LavenderTextSecond)
            } else {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = LavenderTextSecond, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ProfileToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = LavenderSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = LavenderPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = PurpleTextPrimary, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = LavenderPrimaryDark, checkedTrackColor = LavenderContainer)
            )
        }
    }
}
