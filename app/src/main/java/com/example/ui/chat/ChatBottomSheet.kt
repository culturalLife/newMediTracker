package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.chat.components.DisclaimerBanner
import com.example.ui.chat.components.MessageBubble
import com.example.ui.chat.components.QuickReplyChips

/**
 * Modal bottom-sheet hosting the AI Pharmacist chat.
 *
 * @param onDismiss invoked when the user closes the sheet.
 * @param prefilledMedicine if non-null, the ViewModel auto-asks for a structured info card
 *                          for that medicine the first time the sheet opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBottomSheet(
    onDismiss: () -> Unit,
    prefilledMedicine: String? = null,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory()),
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.PartiallyExpanded }
    )

    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Auto-trigger the structured medicine query exactly once when launched
    // from the "Ask AI about this medicine" entry point.
    LaunchedEffect(prefilledMedicine) {
        if (!prefilledMedicine.isNullOrBlank() && messages.isEmpty()) {
            viewModel.askAboutMedicine(prefilledMedicine)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("chat_bottom_sheet"),
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        ChatSheetContent(
            messages = messages,
            isLoading = isLoading,
            isConfigured = viewModel.isConfigured,
            onSend = viewModel::sendUserMessage,
            onQuickReply = viewModel::sendUserMessage,
            onClose = onDismiss
        )
    }
}

@Composable
private fun ChatSheetContent(
    messages: List<ChatViewModel.UiMessage>,
    isLoading: Boolean,
    isConfigured: Boolean,
    onSend: (String) -> Unit,
    onQuickReply: (String) -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()

    // Always scroll to the newest turn (or the typing indicator) when content arrives.
    LaunchedEffect(messages.size, isLoading) {
        val lastIndex = (messages.size + if (isLoading) 1 else 0) - 1
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .navigationBarsPadding()
    ) {
        ChatHeader(onClose = onClose)
        DisclaimerBanner()
        if (!isConfigured) {
            MissingKeyBanner()
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (messages.isEmpty()) {
                item { EmptyState() }
            } else {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(message = msg)
                }
            }
            if (isLoading) {
                item { TypingIndicator() }
            }
        }

        QuickReplyChips(
            suggestions = QUICK_REPLIES,
            onChipClick = onQuickReply,
            modifier = Modifier.fillMaxWidth()
        )

        ChatInputBar(
            enabled = !isLoading && isConfigured,
            onSend = onSend,
            modifier = Modifier.imePadding()
        )
    }
}

@Composable
private fun ChatHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Rx",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AI Pharmacist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Ask anything about your medicines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.testTag("chat_close_button")
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close chat")
        }
    }
}

@Composable
private fun MissingKeyBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "⚙️  Add MISTRAL_API_KEY to your local .env to enable the assistant.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "💬", style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "How can I help today?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tap a suggestion below or type a question. I can explain what a medicine does, common side effects, food advice, and known interactions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Thinking…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun ChatInputBar(
    enabled: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Ask about a medicine…") },
            singleLine = false,
            maxLines = 4,
            modifier = Modifier
                .weight(1f)
                .testTag("chat_input"),
            enabled = enabled,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilledIconButton(
            onClick = {
                val trimmed = input.trim()
                if (trimmed.isNotEmpty()) {
                    onSend(trimmed)
                    input = ""
                }
            },
            enabled = enabled && input.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("chat_send_button")
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send")
        }
    }
}

private val QUICK_REPLIES = listOf(
    "What is Ibuprofen used for?",
    "Side effects of Metformin?",
    "Can I take Paracetamol on empty stomach?",
    "Does Lisinopril interact with NSAIDs?",
    "What does Atorvastatin do?"
)
