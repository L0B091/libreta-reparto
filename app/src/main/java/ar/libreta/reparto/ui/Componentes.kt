package ar.libreta.reparto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun Titulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    unaLinea: Boolean = true,
    ayuda: String? = null,
    error: Boolean = false,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = unaLinea,
        isError = error,
        supportingText = ayuda?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Selector de cantidad con botones − y +. */
@Composable
fun SelectorCantidad(etiqueta: String, valor: Int, alCambiar: (Int) -> Unit, minimo: Int = 0) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(etiqueta, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = { if (valor > minimo) alCambiar(valor - 1) }) { Text("−") }
        Text(valor.toString(), style = MaterialTheme.typography.titleLarge)
        FilledTonalButton(onClick = { alCambiar(valor + 1) }) { Text("+") }
    }
}

@Composable
fun Confirmar(
    titulo: String,
    texto: String,
    confirmar: String,
    alConfirmar: () -> Unit,
    alCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = { alConfirmar(); alCerrar() }) { Text(confirmar) } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

@Composable
fun TextoError(texto: String?) {
    if (texto != null) {
        Text(texto, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}
