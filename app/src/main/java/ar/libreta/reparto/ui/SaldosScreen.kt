package ar.libreta.reparto.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.libreta.reparto.data.Movimiento
import ar.libreta.reparto.data.TipoMovimiento
import ar.libreta.reparto.domain.Dinero
import ar.libreta.reparto.domain.Libreta
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
fun SaldosScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val clientes by vm.clientes.collectAsStateWithLifecycle()
    val saldos by vm.saldos.collectAsStateWithLifecycle()
    var detalleId by remember { mutableLongStateOf(0L) }
    var verTodos by remember { mutableStateOf(false) }

    val conSaldo = clientes
        .map { it to (saldos[it.id] ?: 0L) }
        .filter { verTodos || it.second != 0L }
        .sortedByDescending { it.second }
    val total = saldos.values.filter { it > 0 }.sum()
    val aFavor = saldos.values.filter { it < 0 }.sum()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total fiado", style = MaterialTheme.typography.labelLarge)
                    Text(Dinero.formatear(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (aFavor != 0L) Text("Saldos a favor de clientes: ${Dinero.formatear(-aFavor)}", style = MaterialTheme.typography.bodySmall)
                    Text("El saldo sale de los movimientos: no se edita a mano.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            TextButton(onClick = { verTodos = !verTodos }) {
                Text(if (verTodos) "Mostrar solo con saldo" else "Mostrar todos los clientes")
            }
        }
        if (conSaldo.isEmpty()) {
            item { Text("Nadie te debe. ¡Bien ahí!", modifier = Modifier.padding(8.dp)) }
        }
        items(conSaldo, key = { it.first.id }) { (c, saldo) ->
            Card(Modifier.fillMaxWidth().clickable { detalleId = c.id }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.nombre, fontWeight = FontWeight.Bold)
                        val abiertas = Libreta.entregasAbiertas(vm.movimientosDe(c.id))
                        Text(
                            "$abiertas ${if (abiertas == 1) "entrega abierta" else "entregas abiertas"}" +
                                if (c.zona.isNotBlank()) " · ${c.zona}" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        Dinero.formatear(saldo),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (saldo > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }

    if (detalleId != 0L) {
        DetalleSaldo(vm = vm, clienteId = detalleId, alCerrar = { detalleId = 0L })
    }
}

private sealed interface DialogoSaldo {
    data object Cobro : DialogoSaldo
    data object ACuenta : DialogoSaldo
    data class Anular(val m: Movimiento) : DialogoSaldo
}

@Composable
private fun DetalleSaldo(vm: AppViewModel, clienteId: Long, alCerrar: () -> Unit) {
    val clientes by vm.clientes.collectAsStateWithLifecycle()
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()
    val cliente = clientes.firstOrNull { it.id == clienteId } ?: run { alCerrar(); return }
    val movs = movimientos.filter { it.clienteId == clienteId }
    val saldo = Libreta.saldo(movs)
    val abiertas = Libreta.entregasAbiertas(movs)
    var dialogo by remember { mutableStateOf<DialogoSaldo?>(null) }

    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(cliente.nombre, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Saldo: ${Dinero.formatear(saldo)}", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Entregas abiertas: $abiertas" +
                        (if (ajustes.topeEntregasAbiertas > 0) " de ${ajustes.topeEntregasAbiertas}" else "") +
                        (if (ajustes.topeMontoCentavos > 0) " · Tope ${Dinero.formatear(ajustes.topeMontoCentavos)}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { dialogo = DialogoSaldo.Cobro }) { Text("Registrar cobro") }
                    OutlinedButton(onClick = { dialogo = DialogoSaldo.ACuenta }) { Text("Anotar a cuenta") }
                }
                Text("Movimientos", style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.weight(1f)) {
                    if (movs.isEmpty()) item { Text("Sin movimientos.") }
                    items(movs, key = { it.id }) { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (m.tipo) {
                                        TipoMovimiento.ENTREGA_A_CUENTA -> "Entrega a cuenta" + if (m.cantidad > 0) " (${m.cantidad})" else ""
                                        TipoMovimiento.COBRO_EFECTIVO -> "Cobro en efectivo"
                                        TipoMovimiento.COBRO_QR -> "Cobro por QR"
                                    },
                                )
                                Text(
                                    Instant.ofEpochMilli(m.fecha).atZone(ZoneId.systemDefault()).format(formatoFecha),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                (if (m.tipo == TipoMovimiento.ENTREGA_A_CUENTA) "+" else "−") + Dinero.formatear(m.montoCentavos),
                                color = if (m.tipo == TipoMovimiento.ENTREGA_A_CUENTA) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                            )
                            TextButton(onClick = { dialogo = DialogoSaldo.Anular(m) }) { Text("Anular") }
                        }
                        HorizontalDivider()
                    }
                }
                OutlinedButton(onClick = alCerrar, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
            }
        }
    }

    when (val d = dialogo) {
        DialogoSaldo.Cobro -> DialogoCobro(
            cliente = cliente,
            saldo = saldo,
            alGuardar = { monto, qr -> vm.registrarCobro(cliente.id, monto, qr) },
            alCerrar = { dialogo = null },
        )
        DialogoSaldo.ACuenta -> DialogoACuenta(
            cliente = cliente,
            saldo = saldo,
            precioBidon = ajustes.precioBidonCentavos,
            alGuardar = { cant, monto -> vm.anotarACuenta(cliente.id, cant, monto) },
            alCerrar = { dialogo = null },
        )
        is DialogoSaldo.Anular -> Confirmar(
            titulo = "Anular movimiento",
            texto = "¿Anulás este movimiento de ${Dinero.formatear(d.m.montoCentavos)}? Usalo solo si lo cargaste por error.",
            confirmar = "Anular",
            alConfirmar = { vm.anularMovimiento(d.m.id) },
            alCerrar = { dialogo = null },
        )
        null -> Unit
    }
}
