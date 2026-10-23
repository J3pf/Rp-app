package dev.j3pf.rp.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.ui.theme.RpShapes

@Composable
fun OnboardingContent(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("RP", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "Ve y controla todos tus agentes Pi desde el móvil.\n" +
                "Recibe preguntas y autorizaciones y respóndelas desde la propia notificación.",
            Modifier.padding(vertical = 24.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onStart,
            shape = RpShapes.pill,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Emparejar con mi relay")
        }
    }
}

/** Introducir URL + código (o llegar aquí con un deep link `rp://pair?u=…&c=…` ya relleno). */
@Composable
fun PairContent(
    state: PairUiState,
    initialUrl: String?,
    initialCode: String?,
    onScan: () -> Unit,
    onPair: (url: String, code: String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var code by rememberSaveable { mutableStateOf(initialCode.orEmpty()) }

    LaunchedEffect(initialUrl, initialCode) {
        if (!initialUrl.isNullOrBlank()) url = initialUrl
        if (!initialCode.isNullOrBlank()) code = initialCode
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Emparejar dispositivo", style = MaterialTheme.typography.headlineSmall)
        Text(
            "En el servidor ejecuta `rp-relay pair device` y escanea el QR, o escribe la URL y el código.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onScan,
            shape = RpShapes.pill,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = !state.busy,
        ) {
            Text("Escanear código QR")
        }
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                onClearError()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL del relay (https://…)") },
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it.uppercase()
                onClearError()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Código de emparejamiento") },
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(
            onClick = { onPair(url, code) },
            shape = RpShapes.pill,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = !state.busy && url.isNotBlank() && code.isNotBlank(),
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text("Emparejar")
        }
    }
}
