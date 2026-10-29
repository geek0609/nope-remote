package com.monuk7735.nope.remote.composables

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.monuk7735.nope.remote.infrared.IRController
import com.monuk7735.nope.remote.models.AirConditionerButtonGroup
import com.monuk7735.nope.remote.models.airConditionerButtonGroup
import com.monuk7735.nope.remote.models.database.RemoteDataDBModel

/** Displays only saved codes. AC IR often sends a complete state, not a relative command. */
@Composable
internal fun AirConditionerRemote(remote: RemoteDataDBModel?) {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    }
    val controller = remember(manager) { manager?.let(::IRController) }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val groups = remember(remote) {
        (
            (remote?.onScreenRemoteButtonDBS ?: emptyList()) +
                (remote?.offScreenRemoteButtonDBS ?: emptyList())
            ).groupBy {
            airConditionerButtonGroup(it.name)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Air conditioner", style = MaterialTheme.typography.headlineSmall)
        Text(
            "These are the saved signals for this remote. The app cannot read the AC's " +
                "current settings; some signals may also change other settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AirConditionerButtonGroup.entries.forEach { group ->
            val buttons = groups[group].orEmpty()
            if (buttons.isNotEmpty()) {
                Text(group.title, style = MaterialTheme.typography.titleMedium)
                buttons.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { button ->
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    try {
                                        if (manager?.hasIrEmitter() != true || controller == null) {
                                            Toast.makeText(
                                                context,
                                                "No infrared transmitter available",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else if (vibrator != null) {
                                            button.transmit(controller, vibrator)
                                        } else {
                                            controller.transmit(button.irPattern)
                                        }
                                    } catch (_: RuntimeException) {
                                        Toast.makeText(
                                            context,
                                            "Could not send this infrared signal",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                            ) {
                                Text(button.name)
                            }
                        }
                        if (row.size == 1) {
                            androidx.compose.foundation.layout.Spacer(
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
