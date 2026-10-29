package com.monuk7735.nope.remote.composables

import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.monuk7735.nope.remote.infrared.IRController
import com.monuk7735.nope.remote.infrared.RemoteMatcher
import com.monuk7735.nope.remote.infrared.patterns.IRPatternDecoder
import com.monuk7735.nope.remote.models.retrofit.DeviceCodesRetrofitModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An optional assistant: users still verify a suggested profile in the normal browser. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GuidedRemoteMatcher(
    profiles: List<DeviceCodesRetrofitModel>,
    onReview: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val manager =
        remember(context) {
            context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        }
    val scope = rememberCoroutineScope()
    val initial = remember(profiles) { RemoteMatcher.Step(profiles.indices.toSet(), emptySet()) }
    var session by remember(profiles) { mutableStateOf(RemoteMatcher.Session(initial)) }
    val step = session.step
    var sent by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val test = remember(step) { RemoteMatcher.next(profiles, step.candidates, step.tried) }

    fun advance(next: RemoteMatcher.Step) {
        session = session.advance(next)
        sent = false
        failure = null
    }

    Scaffold(topBar = { AppBar(title = "Find a remote", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(
                padding,
            ).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "${step.candidates.size} possible profiles",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Point your phone at the device. Check its state and " +
                    "listen or look for a response. " +
                    "A missed signal can hide a match; " +
                    "use Undo or start over if unsure.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (step.candidates.isEmpty()) {
                Text("No profiles remain. Start again or browse all profiles manually.")
            } else if (test != null) {
                Text("Try ${test.action}", style = MaterialTheme.typography.titleLarge)
                Text(
                    "The same recorded signal appears in ${test.matching.size} " +
                        "of the remaining profiles.",
                )
                Button(
                    onClick = {
                        sent = false
                        failure = null
                        sending = true
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    val emitter =
                                        checkNotNull(manager) { "No IR emitter available" }
                                    check(emitter.hasIrEmitter()) { "No IR emitter available" }
                                    IRController(
                                        emitter,
                                    ).transmit(IRPatternDecoder(test.code).irPattern)
                                }
                            }
                            sending = false
                            result.onSuccess { sent = true }.onFailure {
                                failure =
                                    it.message ?: "Unable to send signal"
                            }
                        }
                    },
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (sending) "Sending…" else "Send ${test.action}") }
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Did the device respond?", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = sent && !sending,
                        onClick = {
                            advance(
                                RemoteMatcher.Step(
                                    RemoteMatcher.answer(step.candidates, test, true),
                                    step.tried + test.id,
                                ),
                            )
                        },
                    ) { Text("Yes") }
                    OutlinedButton(
                        enabled = sent && !sending,
                        onClick = {
                            advance(
                                RemoteMatcher.Step(
                                    RemoteMatcher.answer(step.candidates, test, false),
                                    step.tried + test.id,
                                ),
                            )
                        },
                    ) { Text("No") }
                    TextButton(enabled = !sending, onClick = {
                        advance(
                            step.copy(
                                tried =
                                step.tried + test.id,
                            ),
                        )
                    }) { Text("Skip") }
                }
            } else {
                Text(
                    "No more comparable test buttons. Review the remaining profiles " +
                        "and try their other buttons.",
                )
            }

            if (step.candidates.isNotEmpty()) {
                Text("Review a profile", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A response is a clue, not a guarantee. Devices may accept more than one " +
                        "signal. Test several buttons before saving.",
                )
                step.candidates.sorted().take(10).forEach { index ->
                    val profile = profiles[index]
                    val name = profile.model?.takeIf { it.isNotBlank() } ?: "Profile ${index + 1}"
                    OutlinedButton(onClick = {
                        onReview(index)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(name)
                    }
                }
                if (step.candidates.size > 10) {
                    Text(
                        "Showing the first 10; narrow the list " +
                            "or browse all profiles.",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = session.history.isNotEmpty() && !sending, onClick = {
                    session = session.undo()
                    sent = false
                    failure = null
                }) { Text("Undo") }
                TextButton(enabled = !sending, onClick = {
                    session = session.reset()
                    sent = false
                    failure = null
                }) { Text("Start over") }
                TextButton(enabled = !sending, onClick = onBack) { Text("Browse all") }
            }
        }
    }
}
