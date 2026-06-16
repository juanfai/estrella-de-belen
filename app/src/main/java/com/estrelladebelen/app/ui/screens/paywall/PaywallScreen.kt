package com.estrelladebelen.app.ui.screens.paywall

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.estrelladebelen.app.R
import com.estrelladebelen.app.ui.theme.Moonbeam

private enum class Plan(
    val productId: String,
    val nameRes: Int,
    val priceRes: Int,
    val savingsRes: Int? = null,
    val isHighlighted: Boolean = false
) {
    ANNUAL(
        "estrella_annual",
        R.string.paywall_plan_annual,
        R.string.paywall_plan_annual_price,
        R.string.paywall_plan_annual_savings,
        isHighlighted = true
    ),
    QUARTERLY(
        "estrella_quarterly",
        R.string.paywall_plan_quarterly,
        R.string.paywall_plan_quarterly_price
    ),
    MONTHLY(
        "estrella_monthly",
        R.string.paywall_plan_monthly,
        R.string.paywall_plan_monthly_price
    ),
}

@Composable
fun PaywallScreen(
    onDismiss: () -> Unit,
    viewModel: PaywallViewModel = viewModel()
) {
    val activity = LocalContext.current as Activity
    var selectedPlan by remember { mutableStateOf(Plan.ANNUAL) }
    val snackbarHostState = remember { SnackbarHostState() }
    val restoreMsg = stringResource(R.string.paywall_already_subscribed)
    val nothingMsg = stringResource(R.string.paywall_nothing_to_restore)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PaywallEvent.Error          -> snackbarHostState.showSnackbar(event.message)
                is PaywallEvent.RestoreSuccess -> { snackbarHostState.showSnackbar(restoreMsg); onDismiss() }
                is PaywallEvent.NothingToRestore -> snackbarHostState.showSnackbar(nothingMsg)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Image(
                painter = painterResource(R.drawable.logo_luz),
                contentDescription = null,
                modifier = Modifier.size(130.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.paywall_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(36.dp))

            Plan.entries.forEach { plan ->
                PlanCard(
                    plan = plan,
                    isSelected = selectedPlan == plan,
                    onClick = { selectedPlan = plan }
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = { viewModel.purchase(activity, selectedPlan.productId) },
                enabled = !viewModel.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.paywall_subscribe),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = { viewModel.restorePurchases() },
                enabled = !viewModel.isLoading
            ) {
                Text(
                    text = stringResource(R.string.paywall_restore),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                TextButton(onClick = {}) {
                    Text(
                        text = stringResource(R.string.paywall_terms),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(text = "·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {}) {
                    Text(
                        text = stringResource(R.string.paywall_privacy),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (plan.isHighlighted && isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainer

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(plan.nameRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (plan.isHighlighted) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Moonbeam.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = stringResource(R.string.paywall_best_value),
                                style = MaterialTheme.typography.labelSmall,
                                color = Moonbeam,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                plan.savingsRes?.let { res ->
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodySmall,
                        color = Moonbeam
                    )
                }
            }
            Text(
                text = stringResource(plan.priceRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
