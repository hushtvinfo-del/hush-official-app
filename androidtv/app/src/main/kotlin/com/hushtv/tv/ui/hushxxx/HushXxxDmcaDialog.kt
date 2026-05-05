package com.hushtv.tv.ui.hushxxx

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hushtv.tv.data.HushXxxApi
import kotlinx.coroutines.launch

/**
 * DMCA takedown form. Required fields mirror the US DMCA §512(c)(3)
 * designated-agent notice. A successful submission auto-flags the
 * reported scene(s) as `pending` server-side so they stop serving
 * while staff review the case.
 *
 * Kept as a modal dialog because the form is long and benefits from
 * its own frame; escape / Back closes.
 */
@Composable
fun HushXxxDmcaDialog(
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var reportedUrls by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    var affirm by remember { mutableStateOf(false) }

    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss,
           properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Color(0xFF07050A),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE91E63).copy(alpha = 0.35f)),
            modifier = Modifier.widthIn(max = 640.dp).padding(16.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
            ) {
                Text("DMCA", color = Color(0xFFE91E63), fontSize = 11.sp,
                     fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
                Spacer(Modifier.height(8.dp))
                Text("Report a copyright violation", color = Color.White,
                     fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Fill out this form to file a DMCA takedown notice. Reported content is taken offline pending review. We'll email you within 72 hours.",
                    color = Color(0xFF94A3B8), fontSize = 12.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(20.dp))

                Field("Full legal name *", name) { name = it }
                Field("Email address *", email) { email = it }
                Field("Organisation (optional)", org) { org = it }
                Field("Phone (optional)", phone) { phone = it }
                Field(
                    "URL(s) or scene title(s) being reported *",
                    reportedUrls,
                    multiline = true,
                ) { reportedUrls = it }
                Field(
                    "Describe the infringement — include proof of ownership *",
                    description,
                    multiline = true,
                ) { description = it }

                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.clickable { affirm = !affirm },
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .background(
                                if (affirm) Color(0xFFE91E63) else Color.Transparent,
                                RoundedCornerShape(3.dp),
                            )
                            .border(1.dp,
                                    if (affirm) Color(0xFFE91E63) else Color(0x55FFFFFF),
                                    RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (affirm) {
                            Text("✓", color = Color.Black, fontSize = 12.sp,
                                 fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "I state, under penalty of perjury, that the information in this notice is accurate, and that I am the copyright owner or authorised to act on behalf of the owner of an exclusive right that is allegedly infringed.",
                        color = Color(0xFFE5E7EB), fontSize = 11.sp, lineHeight = 16.sp,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Field("Electronic signature (type your name) *", signature) { signature = it }

                result?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    Text(msg, color = Color(0xFF80CFF5), fontSize = 12.sp, lineHeight = 18.sp)
                }

                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val canSubmit = !submitting && name.length >= 2 && email.contains("@") &&
                        reportedUrls.isNotBlank() && description.length >= 10 &&
                        affirm && signature.isNotBlank()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                if (canSubmit) Color(0xFFE91E63) else Color(0x55E91E63),
                                RoundedCornerShape(99.dp),
                            )
                            .padding(horizontal = 22.dp, vertical = 12.dp)
                            .clickable(enabled = canSubmit) {
                                submitting = true
                                scope.launch {
                                    val res = HushXxxApi.submitDmca(
                                        HushXxxApi.DmcaBody(
                                            claimant_name = name,
                                            claimant_email = email,
                                            claimant_org = org,
                                            claimant_phone = phone,
                                            reported_urls = reportedUrls,
                                            description = description,
                                            swear_under_penalty = affirm,
                                            signature = signature,
                                        ),
                                    )
                                    submitting = false
                                    result = if (res.ok)
                                        "✓ Case #${res.case_id} received. ${res.message}"
                                    else "Submission failed — ${res.message}"
                                }
                            },
                    ) {
                        Text(
                            if (submitting) "Submitting…" else "Submit DMCA notice",
                            color = Color.Black, fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0x14FFFFFF), RoundedCornerShape(99.dp))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(99.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .clickable { onDismiss() },
                    ) {
                        Text("Close", color = Color.White, fontSize = 13.sp,
                             fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    multiline: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 11.sp,
             fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            maxLines = if (multiline) 6 else 1,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color(0xFFE91E63)),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF140B14), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
