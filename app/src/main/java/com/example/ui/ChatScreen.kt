package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// High Density Design Theme Colors
val ColorHdBackground = Color(0xFFFDFBFF)
val ColorHdBorder = Color(0xFFE1E2EC)
val ColorHdText = Color(0xFF1A1C1E)
val ColorHdPrimary = Color(0xFF3F5F91)
val ColorHdBotBubble = Color(0xFFE1E2EC)
val ColorHdWarningBg = Color(0xFFFFDAD6)
val ColorHdWarningText = Color(0xFF410002)
val ColorHdWarningBorder = Color(0xFFFFB4AB)
val ColorHdInputBg = Color(0xFFF1F0F4)
val ColorHdInputBorder = Color(0xFFC4C6D0)
val ColorHdTimestamp = Color(0xFF74777F)

fun parseMarkdownToAnnotatedString(text: String, isUser: Boolean): AnnotatedString {
    // Determine distinct high-contrast colors for inline code
    val inlineCodeBg = if (isUser) Color(0xFF2C4974) else Color(0xFFD0D3DE)
    val inlineCodeColor = if (isUser) Color(0xFFFFDAD6) else Color(0xFFBA1A1A)

    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val nextBold = text.indexOf("**", cursor)
            val nextInlineCode = text.indexOf("`", cursor)

            val firstMarker = minOf(
                if (nextBold == -1) Int.MAX_VALUE else nextBold,
                if (nextInlineCode == -1) Int.MAX_VALUE else nextInlineCode
            )

            if (firstMarker == Int.MAX_VALUE) {
                append(text.substring(cursor))
                break
            }

            append(text.substring(cursor, firstMarker))

            if (firstMarker == nextBold) {
                val boldEnd = text.indexOf("**", nextBold + 2)
                if (boldEnd == -1) {
                    append("**")
                    cursor = nextBold + 2
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(nextBold + 2, boldEnd))
                    }
                    cursor = boldEnd + 2
                }
            } else {
                val codeEnd = text.indexOf("`", nextInlineCode + 1)
                if (codeEnd == -1) {
                    append("`")
                    cursor = nextInlineCode + 1
                } else {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeBg,
                            color = inlineCodeColor
                        )
                    ) {
                        append(text.substring(nextInlineCode + 1, codeEnd))
                    }
                    cursor = codeEnd + 1
                }
            }
        }
    }
}

sealed class MessageBlock {
    data class Text(val content: String) : MessageBlock()
    data class Code(val content: String, val language: String) : MessageBlock()
}

fun parseMessage(text: String): List<MessageBlock> {
    val blocks = mutableListOf<MessageBlock>()
    val parts = text.split("```")
    for (i in parts.indices) {
        val part = parts[i]
        if (i % 2 == 1) {
            val lines = part.trim().lines()
            val language = if (lines.isNotEmpty() && lines.first().length < 15 && !lines.first().contains(" ")) {
                lines.first()
            } else {
                ""
            }
            val content = if (language.isNotEmpty()) {
                lines.drop(1).joinToString("\n")
            } else {
                part
            }
            blocks.add(MessageBlock.Code(content.trim(), language))
        } else {
            if (part.isNotEmpty()) {
                blocks.add(MessageBlock.Text(part))
            }
        }
    }
    return blocks
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    
    var inputText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive or keyboard opens
    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val suggestions = listOf(
        "Explain Big O notation simply",
        "How do REST APIs work?",
        "What is a deadlock in OS?",
        "Difference between TCP and UDP"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ColorHdBackground,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            // Rounded-full avatar matching design: w-10 h-10 bg-[#D6E3FF] text-[#001B3D]
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFD6E3FF), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "C",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF001B3D)
                                    )
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column {
                                Text(
                                    text = "CS Assistant",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = ColorHdText
                                    )
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFF22C55E), shape = CircleShape)
                                    )
                                    Text(
                                        text = "ONLINE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF44474E),
                                            letterSpacing = 0.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (messages.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearChat() },
                                modifier = Modifier.testTag("clear_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear History",
                                    tint = ColorHdTimestamp
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ColorHdBackground
                    )
                )
                HorizontalDivider(color = ColorHdBorder, thickness = 1.dp)
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Chat messages or empty state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = Color(0xFFD6E3FF)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = "Computer Science",
                                    modifier = Modifier.size(32.dp),
                                    tint = Color(0xFF001B3D)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Computer Science Assistant",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ColorHdText
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Ask me anything about programming, algorithms, databases, networking, or computer architectures. I answer strictly computer science topics!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ColorHdTimestamp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = "Try asking:",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = ColorHdPrimary,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(horizontal = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        suggestions.forEach { suggestion ->
                            Card(
                                onClick = { viewModel.sendMessage(suggestion) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                                    .testTag("suggestion_chip_${suggestion.replace(" ", "_")}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = ColorHdBotBubble.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, ColorHdBorder),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = "Suggested Query",
                                        modifier = Modifier.size(16.dp),
                                        tint = ColorHdPrimary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = ColorHdText
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { message ->
                            MessageItem(message = message)
                        }

                        if (isSending) {
                            item {
                                ThinkingBubble()
                            }
                        }
                    }
                }
            }

            // High Density Footer Input Box
            Surface(
                color = ColorHdBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ColorHdInputBg, shape = RoundedCornerShape(28.dp))
                        .border(1.dp, ColorHdInputBorder, shape = RoundedCornerShape(28.dp))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp)
                            .testTag("message_input_field"),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = ColorHdText,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(ColorHdPrimary),
                        decorationBox = { innerTextField ->
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Ask about code or theory...",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color(0xFF44474E),
                                        fontSize = 15.sp
                                    )
                                )
                            }
                            innerTextField()
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() && !isSending) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                    focusManager.clearFocus()
                                }
                            }
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isSending) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                                focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (inputText.isNotBlank() && !isSending) {
                                    ColorHdPrimary
                                } else {
                                    ColorHdPrimary.copy(alpha = 0.5f)
                                },
                                shape = CircleShape
                            )
                            .testTag("send_button"),
                        enabled = inputText.isNotBlank() && !isSending
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: MessageEntity) {
    val isUser = message.sender == "user"
    
    // Check if bot message is a CS rejection warning
    val isWarning = !isUser && (
        message.text.contains("sorry", ignoreCase = true) || 
        message.text.contains("programmed to assist only", ignoreCase = true)
    )

    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalAlignment = alignment
        ) {
            if (isWarning) {
                // Warning System Notification Bubble (Light Pink/Red with Red Border)
                Surface(
                    color = ColorHdWarningBg,
                    border = BorderStroke(1.dp, ColorHdWarningBorder),
                    shape = RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    tonalElevation = 1.dp
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 20.sp
                            ),
                            color = ColorHdWarningText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SYSTEM NOTIFICATION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = ColorHdWarningText,
                    modifier = Modifier.padding(start = 4.dp)
                )
            } else {
                // Normal User or Bot Bubble
                val bubbleColor = if (isUser) ColorHdPrimary else ColorHdBotBubble
                val contentColor = if (isUser) Color.White else ColorHdText
                val bubbleShape = if (isUser) {
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 0.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                } else {
                    RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                }

                Surface(
                    color = bubbleColor,
                    shape = bubbleShape,
                    tonalElevation = 1.dp
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        if (isUser) {
                            Text(
                                text = parseMarkdownToAnnotatedString(message.text, isUser = true),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                ),
                                color = contentColor
                            )
                        } else {
                            BotMessageContent(text = message.text, contentColor = contentColor)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isUser) "YOU • $timeString" else "BOT • $timeString",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = ColorHdTimestamp,
                    modifier = Modifier.padding(
                        start = if (isUser) 0.dp else 4.dp,
                        end = if (isUser) 4.dp else 0.dp
                    )
                )
            }
        }
    }
}

@Composable
fun BotMessageContent(text: String, contentColor: Color) {
    val blocks = remember(text) { parseMessage(text) }
    Column {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MessageBlock.Text -> {
                    Text(
                        text = parseMarkdownToAnnotatedString(block.content, isUser = false),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        color = contentColor,
                        modifier = Modifier.padding(bottom = if (index < blocks.lastIndex) 8.dp else 0.dp)
                    )
                }
                is MessageBlock.Code -> {
                    CodeBlock(code = block.content, language = block.language)
                }
            }
        }
    }
}

@Composable
fun CodeBlock(code: String, language: String) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ColorHdInputBg)
            .border(1.dp, ColorHdInputBorder, shape = RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorHdBotBubble)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.uppercase().ifEmpty { "CODE" },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = ColorHdText,
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                onClick = {
                    val clip = ClipData.newPlainText("Copied Code", code)
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Code",
                    tint = ColorHdText,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = ColorHdText,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun ThinkingBubble() {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .padding(vertical = 4.dp)
    ) {
        Surface(
            color = ColorHdBotBubble,
            shape = RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = ColorHdPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = ColorHdText
                )
            }
        }
    }
}
