package ar.libreta.reparto.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.libreta.reparto.data.Ajustes
import ar.libreta.reparto.data.Cliente
import ar.libreta.reparto.data.EstadoVisita
import ar.libreta.reparto.data.FormaPago
import ar.libreta.reparto.data.Visita
import ar.libreta.reparto.data.tienePin
import ar.libreta.reparto.domain.Dias
import ar.libreta.reparto.domain.Dinero
import ar.libreta.reparto.domain.Mensajes
import ar.libreta.reparto.domain.ParadaCliente
import ar.libreta.reparto.domain.Ubicacion
import ar.libreta.reparto.util.Acciones
import java.util.Locale

private sealed interface DialogoHoy {
    data class Entrega(val cliente: Cliente) : DialogoHoy
    data class Cobro(val cliente: Cliente) : DialogoHoy
    data class Pin(val cliente: Cliente) : DialogoHoy
    data class Deshacer(val visita: Visita, val nombre: String) : DialogoHoy
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HoyScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val estado by vm.hoy.collectAsStateWithLifecycle()
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()
    val saldos by vm.saldos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialogo by remember { mutableStateOf<DialogoHoy?>(null) }
    var buscandoUbicacion by remember { mutableStateOf(false) }

    fun tomarUbicacion() {
        buscandoUbicacion = true
        vm.avisar("Buscando tu ubicación…")
        Acciones.ubicacionActual(context) { loc ->
            buscandoUbicacion = false
            if (loc == null) {
                vm.avisar("No pude obtener la ubicación. Revisá que el GPS esté prendido y probá de nuevo.")
            } else {
                vm.guardarSalida(loc.latitude, loc.longitude)
            }
        }
    }

    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resultado ->
        if (resultado.values.any { it }) tomarUbicacion()
        else vm.avisar("Sin permiso de ubicación no puedo guardar la salida.")
    }

    val hechas = estado.visitas.size
    val total = estado.paradas.size

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text(Dias.titulo(estado.fecha), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (total == 0) "No hay clientes para hoy." else "Paradas: $hechas de $total marcadas",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            TarjetaSalida(
                ajustes = ajustes,
                buscando = buscandoUbicacion,
                alGuardar = {
                    if (Acciones.tienePermisoUbicacion(context)) tomarUbicacion()
                    else pedirPermiso.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                },
            )
        }
        item {
            TarjetaRecarga(ajustes = ajustes, vm = vm)
        }
        if (total == 0) {
            item {
                Text(
                    "Agregá clientes en la pestaña Clientes y marcá qué días los visitás. Acá aparecen solo los de hoy.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(estado.paradas, key = { it.cliente.id }) { parada ->
            TarjetaParada(
                numero = estado.paradas.indexOf(parada) + 1 + if (ajustes.recargaActiva) 1 else 0,
                parada = parada,
                visita = estado.visitas[parada.cliente.id],
                saldo = saldos[parada.cliente.id] ?: 0L,
                ajustes = ajustes,
                alAbrirWhatsApp = { c ->
                    Acciones.abrirEnlace(
                        context,
                        Mensajes.enlaceWhatsApp(c.telefono, Mensajes.pedido(c, saldos[c.id] ?: 0L, ajustes.nombreReparto)),
                    )
                },
                alPedirPin = { c ->
                    Acciones.abrirEnlace(context, Mensajes.enlaceWhatsApp(c.telefono, Mensajes.pedirPin(c, ajustes.nombreReparto)))
                },
                alPegarPin = { dialogo = DialogoHoy.Pin(it) },
                alEntregue = { dialogo = DialogoHoy.Entrega(it) },
                alNoEstaba = { vm.registrarNoEstaba(it.id) },
                alCobrar = { dialogo = DialogoHoy.Cobro(it) },
                alDeshacer = { v, n -> dialogo = DialogoHoy.Deshacer(v, n) },
                alAbrirMapa = { c -> Acciones.abrirMapa(context, c.lat!!, c.lng!!, c.nombre) },
            )
        }
    }

    when (val d = dialogo) {
        is DialogoHoy.Entrega -> DialogoEntrega(
            cliente = d.cliente,
            saldo = saldos[d.cliente.id] ?: 0L,
            precioBidon = ajustes.precioBidonCentavos,
            alGuardar = { llenos, vacios, forma, monto -> vm.registrarEntrega(d.cliente.id, llenos, vacios, forma, monto) },
            alCerrar = { dialogo = null },
        )
        is DialogoHoy.Cobro -> DialogoCobro(
            cliente = d.cliente,
            saldo = saldos[d.cliente.id] ?: 0L,
            alGuardar = { monto, qr -> vm.registrarCobro(d.cliente.id, monto, qr) },
            alCerrar = { dialogo = null },
        )
        is DialogoHoy.Pin -> DialogoPin(
            titulo = "Pin de ${d.cliente.nombre}",
            alGuardar = { vm.guardarPinCliente(d.cliente.id, it) },
            alCerrar = { dialogo = null },
        )
        is DialogoHoy.Deshacer -> Confirmar(
            titulo = "Deshacer parada",
            texto = "¿Deshacés lo que marcaste para ${d.nombre} hoy? Si fue una entrega a cuenta, se anula ese movimiento y se reponen los bidones.",
            confirmar = "Deshacer",
            alConfirmar = { vm.deshacerVisita(d.visita.id) },
            alCerrar = { dialogo = null },
        )
        null -> Unit
    }
}

@Composable
private fun TarjetaSalida(ajustes: Ajustes, buscando: Boolean, alGuardar: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Salida", fontWeight = FontWeight.SemiBold)
            Text(
                if (ajustes.salidaLat != null && ajustes.salidaLng != null) {
                    "Guardada: ${Ubicacion.formatear(ajustes.salidaLat, ajustes.salidaLng)}"
                } else {
                    "Todavía no guardaste la salida. La ruta arranca desde acá."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = alGuardar, enabled = !buscando) {
                Text(if (buscando) "Buscando ubicación…" else "Guardar ubicación actual como salida")
            }
        }
    }
}

@Composable
private fun TarjetaRecarga(ajustes: Ajustes, vm: AppViewModel) {
    val context = LocalContext.current
    if (ajustes.recargaActiva) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1. Recarga: ${ajustes.plantaNombre.ifBlank { "Planta" }}", fontWeight = FontWeight.Bold)
                if (ajustes.plantaDireccion.isNotBlank()) Text(ajustes.plantaDireccion)
                if (ajustes.plantaTelefono.isNotBlank()) Text("Tel.: ${ajustes.plantaTelefono}", style = MaterialTheme.typography.bodySmall)
                if (!ajustes.plantaTienePin) {
                    Text("La planta no tiene pin: cargalo en Ajustes para ordenar mejor la ruta.", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ajustes.plantaLat != null && ajustes.plantaLng != null) {
                        OutlinedButton(onClick = {
                            Acciones.abrirMapa(context, ajustes.plantaLat, ajustes.plantaLng, ajustes.plantaNombre)
                        }) { Text("Mapa") }
                    }
                    Button(onClick = { vm.activarRecarga(false) }) { Text("Listo") }
                }
            }
        }
    } else {
        OutlinedButton(
            onClick = {
                if (ajustes.plantaConfigurada) vm.activarRecarga(true)
                else vm.avisar("Primero cargá la planta de recarga en Ajustes.")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Agregar recarga") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaParada(
    numero: Int,
    parada: ParadaCliente,
    visita: Visita?,
    saldo: Long,
    ajustes: Ajustes,
    alAbrirWhatsApp: (Cliente) -> Unit,
    alPedirPin: (Cliente) -> Unit,
    alPegarPin: (Cliente) -> Unit,
    alEntregue: (Cliente) -> Unit,
    alNoEstaba: (Cliente) -> Unit,
    alCobrar: (Cliente) -> Unit,
    alDeshacer: (Visita, String) -> Unit,
    alAbrirMapa: (Cliente) -> Unit,
) {
    val c = parada.cliente
    val color = when (visita?.estado) {
        EstadoVisita.ENTREGUE -> MaterialTheme.colorScheme.secondaryContainer
        EstadoVisita.NO_ESTABA -> MaterialTheme.colorScheme.surfaceVariant
        null -> MaterialTheme.colorScheme.surface
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$numero. ${c.nombre}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                parada.distanciaKm?.let {
                    Text(String.format(Locale("es", "AR"), "%.1f km", it), style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(listOf(c.direccion, c.zona).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Sin dirección" })
            Text(
                "Lleva ${c.cantidadHabitual} · En domicilio ${c.bidonesEnDomicilio} · Saldo ${Dinero.formatear(saldo)}" +
                    if (c.tienePin) "" else " · Sin pin",
                style = MaterialTheme.typography.bodySmall,
            )
            if (visita != null) {
                val detalle = when (visita.estado) {
                    EstadoVisita.ENTREGUE -> "Entregué ${visita.llenos} llenos, retiré ${visita.vacios} vacíos · " +
                        when (visita.formaPago) {
                            FormaPago.EFECTIVO -> "pagó en efectivo ${Dinero.formatear(visita.montoCentavos)}"
                            FormaPago.QR -> "pagó por QR ${Dinero.formatear(visita.montoCentavos)}"
                            FormaPago.A_CUENTA -> "a cuenta ${Dinero.formatear(visita.montoCentavos)}"
                            null -> ""
                        }
                    EstadoVisita.NO_ESTABA -> "No estaba"
                }
                Text(detalle, fontWeight = FontWeight.SemiBold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { alAbrirWhatsApp(c) }, label = { Text("WhatsApp") })
                if (c.tienePin) {
                    AssistChip(onClick = { alAbrirMapa(c) }, label = { Text("Mapa") })
                } else {
                    AssistChip(onClick = { alPedirPin(c) }, label = { Text("Pedir pin") })
                }
                AssistChip(onClick = { alPegarPin(c) }, label = { Text(if (c.tienePin) "Cambiar pin" else "Pegar pin") })
                if (saldo > 0) AssistChip(onClick = { alCobrar(c) }, label = { Text("Cobrar") })
            }
            if (visita == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { alEntregue(c) }, modifier = Modifier.weight(1f)) { Text("Entregué") }
                    OutlinedButton(onClick = { alNoEstaba(c) }, modifier = Modifier.weight(1f)) { Text("No estaba") }
                }
            } else {
                TextButton(onClick = { alDeshacer(visita, c.nombre) }) { Text("Deshacer") }
            }
        }
    }
}
