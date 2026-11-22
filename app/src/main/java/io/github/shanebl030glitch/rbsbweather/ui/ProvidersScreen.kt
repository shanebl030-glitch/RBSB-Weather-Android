// Settings → Weather Providers: choose the primary and the backups, enter your own API keys,
// test a connection, and decide when to go back to the primary after it fails.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.ALL_PROVIDERS
import io.github.shanebl030glitch.rbsbweather.data.ProviderInfo
import io.github.shanebl030glitch.rbsbweather.data.RECOVERY_CHOICES
import java.text.DateFormat
import java.util.Date

fun recoveryLabel(m: Int) = when (m) {
    0 -> "Try the primary again at the next update"
    -1 -> "Stay on the backup until I change it"
    else -> "Try the primary again after $m minutes"
}

@Composable
fun ProvidersScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier) {
    val chain = state.settings.providers
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("providers"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("The primary is asked first. If it fails, the backups are tried in order. " +
            "Services marked \"key\" need your own free API key from their website.", fontSize = 14.sp)
        val ordered = chain.mapNotNull { id -> ALL_PROVIDERS.firstOrNull { it.id == id } } +
            ALL_PROVIDERS.filter { it.id !in chain }
        ordered.forEach { info -> ProviderCard(info, state, model) }
        Text("When the primary fails", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Choice("", RECOVERY_CHOICES.map { it to recoveryLabel(it) }, state.settings.recovery) { model.setRecovery(it) }
        OutlinedButton(onClick = { model.usePrimaryNow() }, modifier = Modifier.testTag("usePrimaryNow")) { Text("Use the primary now") }
        Text("To protect small free allowances, Tomorrow.io is asked at most every 10 minutes per place, and OpenWeather and Visual Crossing at most every 5 minutes; a refresh in between shows the forecast it already has.",
            fontSize = 12.sp)
        Text("Planned", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        io.github.shanebl030glitch.rbsbweather.data.PLANNED_PROVIDERS.forEach { (name, why, _) ->
            Text("… $name", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(why, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProviderCard(info: ProviderInfo, state: UiState, model: AppModel) {
    val chain = state.settings.providers
    val pos = chain.indexOf(info.id)
    val uri = LocalUriHandler.current
    var open by rememberSaveable(info.id) { mutableStateOf(false) }
    val hasKey = info.id in state.keysSaved
    Card(Modifier.fillMaxWidth().testTag("provider-${info.id}")) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(info.name + if (info.needsKey) "  · key" else "", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(when {
                        pos == 0 -> "Primary"
                        pos > 0 -> "Backup $pos"
                        else -> "Not used"
                    } + when {
                        info.needsKey && !hasKey -> " · no key yet"
                        info.needsKey -> " · key saved"
                        else -> " · no key needed"
                    }, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("providerRole-${info.id}"))
                }
                if (pos > 0) {
                    IconButton(onClick = { model.moveProvider(info.id, -1) }, enabled = pos > 1) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${info.name} up") }
                    IconButton(onClick = { model.moveProvider(info.id, 1) }, enabled = pos < chain.size - 1) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${info.name} down") }
                }
            }
            val st = model.providerStatus(info.id)
            val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
            if (st.lastOk > 0 || st.lastError != null) {
                Text(when {
                    st.lastError != null && st.lastErrorAt > st.lastOk -> "Last try ${fmt.format(Date(st.lastErrorAt))}: ${st.lastError}"
                    else -> "Last worked ${fmt.format(Date(st.lastOk))}"
                }, fontSize = 13.sp)
            }
            // usage or limits the service reported with its last reply (2.x desktop feature)
            if (st.usage.isNotEmpty()) Text("Usage: " + st.usage.values.joinToString("; "), fontSize = 13.sp,
                modifier = Modifier.testTag("usage-${info.id}"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)) {
                if (pos != 0) {
                    OutlinedButton(onClick = { model.makePrimary(info.id) }, modifier = Modifier.testTag("primary-${info.id}")) {
                        Text("Make primary") }
                }
                if (pos != 0) {
                    Text("Backup", fontSize = 14.sp)
                    Switch(checked = pos > 0, onCheckedChange = { model.setBackup(info.id, it) },
                        modifier = Modifier.testTag("backup-${info.id}"))
                }
            }
            TextButton(onClick = { open = !open }, modifier = Modifier.testTag("details-${info.id}")) {
                Text(if (open) "Hide details" else "Details, key and test")
            }
            if (open) {
                Detail("Coverage", info.coverage)
                Detail("Forecast", info.forecastDays)
                Detail("Hourly", info.hourly)
                Detail("Free use", info.limits)
                Detail("Credit", info.credit)
                if (info.needsKey) {
                    var key by rememberSaveable(info.id + "key") { mutableStateOf("") }
                    Text(info.keyHelp, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    OutlinedTextField(value = key, onValueChange = { key = it.take(200) }, singleLine = true,
                        label = { Text(if (hasKey) "Replace the saved key" else "Your API key") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("key-${info.id}"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { model.setKey(info.id, key); key = "" }, enabled = key.isNotBlank(),
                            modifier = Modifier.testTag("saveKey-${info.id}")) { Text("Save key") }
                        if (hasKey) TextButton(onClick = { model.setKey(info.id, "") }) { Text("Remove key") }
                        if (info.signupUrl.isNotBlank()) TextButton(onClick = { try { uri.openUri(info.signupUrl) } catch (e: Exception) { } }) {
                            Text("Get a key") }
                    }
                }
                info.option?.let { (optId, label, _) ->
                    var v by rememberSaveable(info.id + optId) { mutableStateOf(state.settings.option(info.id, optId) ?: "") }
                    OutlinedTextField(value = v, onValueChange = { v = it.take(24) }, singleLine = true, label = { Text(label) },
                        modifier = Modifier.fillMaxWidth().testTag("option-${info.id}"))
                    TextButton(onClick = { model.setProviderOption(info.id, optId, v) }) { Text("Save") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = { model.testProvider(info.id) }, label = { Text("Test connection") },
                        enabled = info.id !in state.testing && (!info.needsKey || hasKey), modifier = Modifier.testTag("test-${info.id}"))
                    if (info.id in state.testing) CircularProgressIndicator(Modifier.padding(start = 10.dp).padding(2.dp), strokeWidth = 2.dp)
                }
                state.providerTests[info.id]?.let {
                    Text(it, fontSize = 13.sp, modifier = Modifier.testTag("testResult-${info.id}"),
                        color = if (it.startsWith("Working")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { try { uri.openUri(info.website) } catch (e: Exception) { } }) { Text(info.website) }
            }
        }
    }
}

@Composable
private fun Detail(label: String, text: String) {
    Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    Text(text, fontSize = 13.sp)
}
