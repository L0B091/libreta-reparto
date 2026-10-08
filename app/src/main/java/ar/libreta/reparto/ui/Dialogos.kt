package ar.libreta.reparto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.libreta.reparto.classifier.Clasificacion
import ar.libreta.reparto.data.Cliente
import ar.libreta.reparto.data.FormaPago
import ar.libreta.reparto.domain.Dinero
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** "Entregué": cantidades, forma de pago y monto (editable). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoEntrega(
    cliente: Cliente,
    saldo: Long,
    precioBidon: Long,
    alGuardar: suspend (llenos: Int, vacios: Int, forma: FormaPago, monto: Long) -> String?,
    alCerrar: () -> Unit,
) {
    var llenos by remember { mutableIntStateOf(cliente.cantidadHabitual) }
    var vacios by remember { mutableIntStateOf(minOf(cliente.cantidadHabitual, cliente.bidonesEnDomicilio)) }
    var forma by remember { mutableStateOf(FormaPago.EFECTIVO) }
    var montoTexto by remember { mutableStateOf(Dinero.aTexto(cliente.cantidadHabitual * precioBidon)) }
    var montoEditado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(llenos) {
        if (!montoEditado) montoTexto = Dinero.aTexto(llenos * precioBidon)
    }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Entregué a ${cliente.nombre}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectorCantidad("Llenos entregados", llenos, { llenos = it })
                SelectorCantidad("Vacíos retirados", vacios, { vacios = it })
                val quedan = (cliente.bidonesEnDomicilio - vacios + llenos).coerceAtLeast(0)
                Text(
                    "Bidones en domicilio: ${cliente.bidonesEnDomicilio} → $quedan",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("¿Cómo pagó?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(forma == FormaPago.EFECTIVO, { forma = FormaPago.EFECTIVO }, { Text("Efectivo") })
                    FilterChip(forma == FormaPago.QR, { forma = FormaPago.QR }, { Text("QR") })
                    FilterChip(forma == FormaPago.A_CUENTA, { forma = FormaPago.A_CUENTA }, { Text("A cuenta") })
                }
                CampoTexto(
                    montoTexto,
                    { montoTexto = it; montoEditado = true },
                    "Monto",
                    teclado = KeyboardType.Decimal,
                    ayuda = "Precio por bidón: ${Dinero.formatear(precioBidon)}",
                )
                if (forma == FormaPago.A_CUENTA) {
                    Text(
                        "Saldo actual: ${Dinero.formatear(saldo)}. Se suma a la libreta.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Pago en el momento: no cambia el saldo. Si además te pagó deuda, usá \"Cobrar\".",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextoError(error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !guardando,
                onClick = {
                    val monto = Dinero.parsear(montoTexto)
                    if (monto == null) {
                        error = "Revisá el monto."
                        return@TextButton
                    }
                    guardando = true
                    scope.launch {
                        val e = alGuardar(llenos, vacios, forma, monto)
                        guardando = false
                        if (e == null) alCerrar() else error = e
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

/** Cobro en efectivo o QR: resta del saldo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoCobro(
    cliente: Cliente,
    saldo: Long,
    alGuardar: suspend (monto: Long, qr: Boolean) -> String?,
    alCerrar: () -> Unit,
) {
    var montoTexto by remember { mutableStateOf(if (saldo > 0) Dinero.aTexto(saldo) else "") }
    var qr by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Cobrar a ${cliente.nombre}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saldo actual: ${Dinero.formatear(saldo)}")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(!qr, { qr = false }, { Text("Efectivo") })
                    FilterChip(qr, { qr = true }, { Text("QR") })
                }
                CampoTexto(montoTexto, { montoTexto = it }, "Monto cobrado", teclado = KeyboardType.Decimal)
                TextoError(error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val monto = Dinero.parsear(montoTexto)
                if (monto == null || monto <= 0) {
                    error = "Revisá el monto."
                    return@TextButton
                }
                scope.launch {
                    val e = alGuardar(monto, qr)
                    if (e == null) alCerrar() else error = e
                }
            }) { Text("Anotar cobro") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

/** Entrega a cuenta fuera de la ruta (por ejemplo, si pasó a buscar). Respeta los topes. */
@Composable
fun DialogoACuenta(
    cliente: Cliente,
    saldo: Long,
    precioBidon: Long,
    alGuardar: suspend (cantidad: Int, monto: Long) -> String?,
    alCerrar: () -> Unit,
) {
    var cantidad by remember { mutableIntStateOf(cliente.cantidadHabitual.coerceAtLeast(1)) }
    var montoTexto by remember { mutableStateOf(Dinero.aTexto(cantidad * precioBidon)) }
    var montoEditado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(cantidad) { if (!montoEditado) montoTexto = Dinero.aTexto(cantidad * precioBidon) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Anotar a cuenta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${cliente.nombre} · saldo actual ${Dinero.formatear(saldo)}")
                SelectorCantidad("Bidones", cantidad, { cantidad = it }, minimo = 1)
                CampoTexto(montoTexto, { montoTexto = it; montoEditado = true }, "Monto", teclado = KeyboardType.Decimal)
                Text(
                    "No cambia los bidones en domicilio. Para entregas en ruta usá \"Entregué\" en Hoy.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextoError(error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val monto = Dinero.parsear(montoTexto)
                if (monto == null || monto <= 0) {
                    error = "Revisá el monto."
                    return@TextButton
                }
                scope.launch {
                    val e = alGuardar(cantidad, monto)
                    if (e == null) alCerrar() else error = e
                }
            }) { Text("Anotar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

/** Pegar enlace de Google Maps o coordenadas para guardar el pin. */
@Composable
fun DialogoPin(
    titulo: String,
    alGuardar: suspend (String) -> String?,
    alCerrar: () -> Unit,
) {
    var texto by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Pegá el enlace de Google Maps que te mandó por WhatsApp o las coordenadas (por ejemplo -34.6037, -58.3816).",
                    style = MaterialTheme.typography.bodySmall,
                )
                CampoTexto(texto, { texto = it; error = null }, "Enlace o coordenadas", unaLinea = false)
                TextoError(error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val e = alGuardar(texto)
                    if (e == null) alCerrar() else error = e
                }
            }) { Text("Guardar pin") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

private val jsonLindo = Json { prettyPrint = true; encodeDefaults = true }

/** Dolphin: pegás un texto y muestra la clasificación. No hace nada más. */
@Composable
fun DialogoDolphin(
    alClasificar: suspend (String) -> Result<Clasificacion>,
    alCerrar: () -> Unit,
) {
    var texto by remember { mutableStateOf("") }
    var resultado by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Clasificar texto (Dolphin)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Solo clasifica. No confirma pedidos, no mueve saldos y no escribe por WhatsApp.",
                    style = MaterialTheme.typography.bodySmall,
                )
                CampoTexto(texto, { texto = it }, "Texto pegado", unaLinea = false)
                if (cargando) Text("Clasificando…")
                TextoError(error)
                resultado?.let {
                    Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !cargando && texto.isNotBlank(), onClick = {
                cargando = true
                error = null
                resultado = null
                scope.launch {
                    alClasificar(texto)
                        .onSuccess { resultado = jsonLindo.encodeToString(Clasificacion.serializer(), it) }
                        .onFailure { error = it.message ?: "Error desconocido." }
                    cargando = false
                }
            }) { Text("Clasificar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
    )
}
