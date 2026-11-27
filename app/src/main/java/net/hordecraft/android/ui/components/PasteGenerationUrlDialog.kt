package net.hordeslop.studio.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.hordeslop.studio.domain.GenerationUrlParser
import net.hordeslop.studio.domain.ParsedImageMetadata
import net.hordeslop.studio.ui.theme.HordeColors

@Composable
fun PasteGenerationUrlDialog(
    initialUrl: String = "",
    allowPasteAsRawText: Boolean = false,
    onPasteAsRawText: ((String) -> Unit)? = null,
    onApplyMetadata: (ParsedImageMetadata) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    val parsed = remember(urlInput) { GenerationUrlParser.parseString(urlInput) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HordeColors.CardSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = HordeColors.TealSoft)
                Column {
                    Text(
                        text = "Generation Link Metadata",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HordeColors.TextPrimary,
                    )
                    Text(
                        text = "aihorde.net, ArtBot & StableUI (parsed locally)",
                        fontSize = 11.sp,
                        color = HordeColors.TextMuted,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    maxLines = 4,
                    label = { Text("Paste link URL", fontSize = 12.sp) },
                    placeholder = { Text("https://aihorde.net/?prompt=...", fontSize = 12.sp) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                    if (!clip.isNullOrBlank()) {
                                        urlInput = clip.trim()
                                    }
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.ContentPaste,
                                    contentDescription = "Paste from clipboard",
                                    tint = HordeColors.TealSoft,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            if (urlInput.isNotBlank()) {
                                IconButton(
                                    onClick = { urlInput = "" },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Clear,
                                        contentDescription = "Clear URL",
                                        tint = HordeColors.TextMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_url_input"),
                )

                if (parsed != null && parsed.hasMetadata) {
                    val meta = parsed.metadata
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = HordeColors.SubCardSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp)),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Detected ${meta.generatorSource} parameters",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HordeColors.TealSoft,
                            )
                            if (meta.prompt.isNotBlank()) {
                                Text("Prompt", fontSize = 10.sp, color = HordeColors.TextMuted)
                                Text(
                                    text = meta.prompt,
                                    fontSize = 12.sp,
                                    color = HordeColors.TextPrimary,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (meta.negativePrompt.isNotBlank()) {
                                Text("Negative Prompt", fontSize = 10.sp, color = HordeColors.TextMuted)
                                Text(
                                    text = meta.negativePrompt,
                                    fontSize = 11.sp,
                                    color = HordeColors.TextSecondary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            meta.model?.let { UrlParamLine("Model", it) }
                            if (meta.width != null && meta.height != null) {
                                UrlParamLine("Size", "${meta.width} × ${meta.height}")
                            }
                            meta.steps?.let { UrlParamLine("Steps", it.toString()) }
                            meta.cfg?.let { UrlParamLine("CFG", it.toString()) }
                            meta.sampler?.let { UrlParamLine("Sampler", it) }
                            meta.scheduler?.let { UrlParamLine("Scheduler", it) }
                            meta.seed?.let { UrlParamLine("Seed", it) }
                            meta.clipSkip?.let { UrlParamLine("Clip Skip", it.toString()) }
                            if (meta.loras.isNotEmpty()) {
                                UrlParamLine("LoRAs", meta.loras.joinToString(", "))
                            }
                        }
                    }
                } else if (urlInput.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = HordeColors.SubCardSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(10.dp)),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = if (parsed == null) {
                                    "Enter a valid http/https link from aihorde.net, ArtBot, or StableUI."
                                } else {
                                    "No URL-encoded generation metadata found in this link."
                                },
                                fontSize = 11.sp,
                                color = HordeColors.TextSecondary,
                            )
                            if (parsed != null) {
                                OutlinedButton(
                                    onClick = { GenerationUrlParser.forwardToBrowser(context, parsed.originalUrl) },
                                    modifier = Modifier.testTag("open_url_in_browser_button"),
                                ) {
                                    Text("Open in Browser", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (allowPasteAsRawText && onPasteAsRawText != null && urlInput.isNotBlank()) {
                    OutlinedButton(onClick = { onPasteAsRawText(urlInput) }) {
                        Text("Paste as text", fontSize = 11.sp)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = parsed?.hasMetadata == true,
                colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                onClick = {
                    val meta = parsed?.metadata ?: return@Button
                    onApplyMetadata(meta)
                },
                modifier = Modifier.testTag("apply_url_metadata_button"),
            ) {
                Text(
                    text = "Apply to Studio",
                    color = Color(0xFF020617),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                )
            }
        },
    )
}

@Composable
private fun UrlParamLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.sp, color = HordeColors.TextMuted)
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = HordeColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
