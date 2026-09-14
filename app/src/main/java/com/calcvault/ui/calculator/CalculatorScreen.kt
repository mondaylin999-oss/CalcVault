package com.calcvault.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calcvault.calc.HistoryItem
import com.calcvault.ui.theme.DisplayExpressionStyle
import com.calcvault.ui.theme.DisplayNumberStyle

/**
 * The calculator. To anyone who does not know the PIN this is the whole app.
 */
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onEquals: () -> Unit,
    onEqualsLongPress: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.angleUnit.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
            if (state.memory != 0.0) {
                Text(
                    text = "  M",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::onToggleHistory) {
                Icon(Icons.Filled.History, contentDescription = "History")
            }
        }

        Display(
            expression = state.expression,
            preview = state.preview,
            error = state.error,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        MemoryRow(viewModel)

        Keys(
            state = state,
            viewModel = viewModel,
            onEquals = onEquals,
            onEqualsLongPress = onEqualsLongPress,
            modifier = Modifier
                .fillMaxWidth()
                .weight(3.2f),
        )
    }

    if (state.showHistory) {
        HistorySheet(
            history = state.history,
            onPick = viewModel::onHistoryItemPicked,
            onClear = viewModel::onClearHistory,
            onDismiss = viewModel::onToggleHistory,
        )
    }
}

@Composable
private fun Display(
    expression: String,
    preview: String,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = expression.ifEmpty { "0" },
            style = DisplayNumberStyle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        )
        Text(
            text = error ?: preview.let { if (it.isEmpty()) "" else "= $it" },
            style = DisplayExpressionStyle,
            color = if (error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.secondary
            },
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun MemoryRow(viewModel: CalculatorViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TextButton(onClick = viewModel::onMemoryClear) { Text("MC") }
        TextButton(onClick = viewModel::onMemoryRecall) { Text("MR") }
        TextButton(onClick = { viewModel.onMemoryAdd(1) }) { Text("M+") }
        TextButton(onClick = { viewModel.onMemoryAdd(-1) }) { Text("M−") }
    }
}

@Composable
private fun Keys(
    state: CalculatorState,
    viewModel: CalculatorViewModel,
    onEquals: () -> Unit,
    onEqualsLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Keypad.rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key ->
                    val useSecond = state.secondFunction && key.secondAction != null
                    val label = when {
                        key.action == KeyAction.ToggleAngleUnit -> state.angleUnit.name
                        useSecond -> key.secondLabel ?: key.label
                        else -> key.label
                    }
                    val action = if (useSecond) key.secondAction!! else key.action
                    val highlighted = key.action == KeyAction.ToggleSecond && state.secondFunction

                    KeyButton(
                        label = label,
                        style = key.style,
                        highlighted = highlighted,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        onLongClick = if (action == KeyAction.Equals) onEqualsLongPress else null,
                        onClick = {
                            when (action) {
                                is KeyAction.Insert -> viewModel.onInput(action.text)
                                KeyAction.Clear -> viewModel.onClear()
                                KeyAction.Backspace -> viewModel.onBackspace()
                                KeyAction.ToggleSecond -> viewModel.onToggleSecondFunction()
                                KeyAction.ToggleAngleUnit -> viewModel.onToggleAngleUnit()
                                KeyAction.Equals -> onEquals()
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyButton(
    label: String,
    style: KeyStyle,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        highlighted -> colors.primary
        style == KeyStyle.ACCENT -> colors.primary
        style == KeyStyle.DIGIT -> colors.surfaceVariant
        style == KeyStyle.OPERATOR -> colors.primaryContainer
        style == KeyStyle.DESTRUCTIVE -> colors.errorContainer
        else -> colors.surfaceContainerHighest
    }
    val foreground = when {
        highlighted || style == KeyStyle.ACCENT -> colors.onPrimary
        style == KeyStyle.DIGIT -> colors.onSurfaceVariant
        style == KeyStyle.OPERATOR -> colors.onPrimaryContainer
        style == KeyStyle.DESTRUCTIVE -> colors.onErrorContainer
        else -> colors.onSurface
    }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = foreground,
            fontSize = if (label.length > 3) 15.sp else 20.sp,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    history: List<HistoryItem>,
    onPick: (HistoryItem) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("History", style = MaterialTheme.typography.titleMedium)
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = onClear, enabled = history.isNotEmpty()) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear history")
            }
        }
        if (history.isEmpty()) {
            Text(
                text = "Nothing calculated yet.",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(20.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(history) { item ->
                Surface(
                    onClick = { onPick(item) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(
                            text = item.expression,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(text = item.result, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        Box(modifier = Modifier.size(24.dp))
    }
}
