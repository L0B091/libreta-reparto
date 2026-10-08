package ar.libreta.reparto.ui

import android.Manifest
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.libreta.reparto.data.Cliente
import ar.libreta.reparto.data.tienePin
import ar.libreta.reparto.domain.Dias
import ar.libreta.reparto.domain.Dinero
import ar.libreta.reparto.domain.Telefono
import ar.libreta.reparto.domain.Ubicacion
import ar.libreta.reparto.util.ContactoTelefono
import ar.libreta.reparto.util.Contactos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClientesScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val clientes by vm.clientes.collectAsStateWithLifecycle()
    val saldos by vm.saldos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var busqueda by remember { mutableStateOf("") }
    var editando by remember { mutableStateOf<Cliente?>(null) }
    var borrando by remember { mutableStateOf<Cliente?>(null) }
    var importando by remember { mutableStateOf(false) }

    val pedirContactos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) importando = true
        else vm.avisar("Sin permiso de contactos no puedo leer la agenda. Podés elegir un contacto por vez o cargarlo a mano.")
    }

    // Selector de un solo contacto del sistema: no necesita permiso de contactos.
    val elegirContacto = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        val ct = runCatching { Contactos.leerElegido(context, uri) }.getOrNull()
        if (ct == null) {
            vm.avisar("Ese contacto no tiene número.")
        } else {
            val existente = vm.clienteConTelefono(ct.telefono)
            if (existente != null) {
                vm.avisar("${existente.nombre} ya está en tus clientes con ese número.")
                editando = existente
            } else {
                editando = Cliente(nombre = ct.nombre, telefono = ct.telefono, cantidadHabitual = 1)
            }
        }
    }

    val filtrados = remember(clientes, busqueda) {
        val q = busqueda.trim().lowercase()
        if (q.isEmpty()) clientes else clientes.filter {
            it.nombre.lowercase().contains(q) || it.direccion.lowercase().contains(q) ||
                it.zona.lowercase().contains(q) || it.telefono.contains(q)
        }
    }

    Box(modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Clientes (${clientes.size})", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editando = Cliente(nombre = "", cantidadHabitual = 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Agregar cliente")
                }
                OutlinedButton(onClick = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) importando = true else pedirContactos.launch(Manifest.permission.READ_CONTACTS)
                }) { Text("Importar de contactos") }
                OutlinedButton(onClick = {
                    try {
                        elegirContacto.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                    } catch (e: Exception) {
                        vm.avisar("No hay app de contactos para elegir.")
                    }
                }) { Text("Elegir un contacto") }
            }
        }
        item {
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                label = { Text("Buscar") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (clientes.isEmpty()) {
            item {
                Text(
                    "Todavía no tenés clientes. Tocá \"Agregar cliente\" para cargarlo a mano, o importalos de la agenda del teléfono (la misma que usa WhatsApp) y después completá dirección, zona y días.",
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(filtrados, key = { it.id }) { c ->
            val incompleto = c.direccion.isBlank() || c.diasVisita == 0
            Card(Modifier.fillMaxWidth().clickable { editando = c }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.nombre, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        val saldo = saldos[c.id] ?: 0L
                        if (saldo != 0L) Text(Dinero.formatear(saldo), color = MaterialTheme.colorScheme.tertiary)
                    }
                    Text(listOf(c.telefono, c.direccion, c.zona).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${Dias.resumen(c.diasVisita)} · Lleva ${c.cantidadHabitual} · En domicilio ${c.bidonesEnDomicilio}" +
                            if (c.tienePin) " · Con pin" else " · Sin pin",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (incompleto) {
                        Text("Falta completar dirección o días de visita", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    ExtendedFloatingActionButton(
        onClick = { editando = Cliente(nombre = "", cantidadHabitual = 1) },
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text("Agregar cliente") },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    )
    }

    editando?.let { c ->
        FormularioCliente(
            inicial = c,
            vm = vm,
            alCerrar = { editando = null },
            alBorrar = { borrando = it; editando = null },
        )
    }
    borrando?.let { c ->
        val saldo = saldos[c.id] ?: 0L
        Confirmar(
            titulo = "Borrar cliente",
            texto = "¿Borrás a ${c.nombre}? Se borran también sus movimientos y visitas." +
                if (saldo != 0L) " Ojo: tiene saldo ${Dinero.formatear(saldo)}." else "",
            confirmar = "Borrar",
            alConfirmar = { vm.borrarCliente(c) },
            alCerrar = { borrando = null },
        )
    }
    if (importando) {
        DialogoImportarContactos(
            vm = vm,
            alCerrar = { importando = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FormularioCliente(inicial: Cliente, vm: AppViewModel, alCerrar: () -> Unit, alBorrar: (Cliente) -> Unit) {
    var nombre by remember { mutableStateOf(inicial.nombre) }
    var telefono by remember { mutableStateOf(inicial.telefono) }
    var direccion by remember { mutableStateOf(inicial.direccion) }
    var zona by remember { mutableStateOf(inicial.zona) }
    var bidones by remember { mutableIntStateOf(inicial.bidonesEnDomicilio) }
    var habitual by remember { mutableIntStateOf(inicial.cantidadHabitual) }
    var dias by remember { mutableIntStateOf(inicial.diasVisita) }
    var notas by remember { mutableStateOf(inicial.notas) }
    var pinTexto by remember {
        mutableStateOf(if (inicial.lat != null && inicial.lng != null) Ubicacion.formatear(inicial.lat, inicial.lng) else "")
    }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (inicial.id == 0L) "Nuevo cliente" else "Editar cliente", style = MaterialTheme.typography.headlineSmall)
                CampoTexto(nombre, { nombre = it }, "Nombre")
                CampoTexto(
                    telefono, { telefono = it }, "Teléfono (WhatsApp)", teclado = KeyboardType.Phone,
                    ayuda = Telefono.normalizarAR(telefono)?.let { "Para WhatsApp: +$it" } ?: "Con código de área, por ejemplo 11 2345-6789",
                )
                CampoTexto(direccion, { direccion = it }, "Dirección")
                CampoTexto(zona, { zona = it }, "Zona o barrio")
                SelectorCantidad("Cantidad habitual", habitual, { habitual = it })
                SelectorCantidad("Bidones en domicilio", bidones, { bidones = it })
                Titulo("Días de visita")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dias.cortos.forEachIndexed { i, d ->
                        FilterChip(
                            selected = dias and (1 shl i) != 0,
                            onClick = { dias = Dias.alternar(dias, i) },
                            label = { Text(d) },
                        )
                    }
                }
                Titulo("Pin (opcional)")
                CampoTexto(
                    pinTexto, { pinTexto = it; error = null }, "Enlace de Google Maps o coordenadas", unaLinea = false,
                    ayuda = "Lo cargás vos: pegá el enlace completo o las coordenadas (ej. -34.6037, -58.3816). Dejalo vacío si no tiene.",
                )
                CampoTexto(notas, { notas = it }, "Notas", unaLinea = false)
                TextoError(error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (nombre.isBlank()) {
                            error = "Poné un nombre."
                            return@Button
                        }
                        var lat: Double? = null
                        var lng: Double? = null
                        if (pinTexto.isNotBlank()) {
                            when (val r = Ubicacion.interpretar(pinTexto)) {
                                is ar.libreta.reparto.domain.ResultadoPin.Ok -> { lat = r.lat; lng = r.lng }
                                ar.libreta.reparto.domain.ResultadoPin.EnlaceCorto -> { error = AppViewModel.MENSAJE_ENLACE_CORTO; return@Button }
                                ar.libreta.reparto.domain.ResultadoPin.Invalido -> { error = AppViewModel.MENSAJE_PIN_INVALIDO; return@Button }
                            }
                        }
                        val duplicado = vm.clienteConTelefono(telefono)
                        if (duplicado != null && duplicado.id != inicial.id) {
                            error = "Ya existe ${duplicado.nombre} con ese teléfono."
                            return@Button
                        }
                        vm.guardarCliente(
                            inicial.copy(
                                nombre = nombre.trim(),
                                telefono = telefono.trim(),
                                direccion = direccion.trim(),
                                zona = zona.trim(),
                                bidonesEnDomicilio = bidones,
                                cantidadHabitual = habitual,
                                diasVisita = dias,
                                lat = lat,
                                lng = lng,
                                notas = notas.trim(),
                            ),
                        )
                        alCerrar()
                    }) { Text("Guardar") }
                    OutlinedButton(onClick = alCerrar) { Text("Cancelar") }
                    if (inicial.id != 0L) {
                        TextButton(onClick = { alBorrar(inicial) }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogoImportarContactos(vm: AppViewModel, alCerrar: () -> Unit) {
    val context = LocalContext.current
    val clientes by vm.clientes.collectAsStateWithLifecycle()
    var contactos by remember { mutableStateOf<List<ContactoTelefono>?>(null) }
    var busqueda by remember { mutableStateOf("") }
    val elegidos = remember { mutableStateListOf<ContactoTelefono>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        contactos = withContext(Dispatchers.IO) { runCatching { Contactos.leerTodos(context) }.getOrDefault(emptyList()) }
    }
    val yaCargados = remember(clientes) { clientes.mapNotNull { Telefono.normalizarAR(it.telefono) }.toSet() }
    val visibles = remember(contactos, busqueda) {
        val q = busqueda.trim().lowercase()
        val todos = contactos.orEmpty()
        if (q.isEmpty()) todos else todos.filter { it.nombre.lowercase().contains(q) || it.telefono.contains(q) }
    }

    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Importar de contactos", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Marcá los contactos que son clientes. Los que ya tenés (mismo número) no se repiten. Después completá dirección, zona y días.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { busqueda = it },
                    label = { Text("Buscar contacto") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.weight(1f)) {
                    when {
                        contactos == null -> Text("Leyendo contactos…")
                        contactos!!.isEmpty() -> Text("No encontré contactos con número.")
                        else -> LazyColumn {
                            items(visibles) { ct ->
                                val yaEsta = ct.normalizado != null && ct.normalizado in yaCargados
                                val marcado = ct in elegidos
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !yaEsta) { if (marcado) elegidos.remove(ct) else elegidos.add(ct) }
                                        .padding(vertical = 4.dp),
                                ) {
                                    Checkbox(
                                        checked = marcado || yaEsta,
                                        enabled = !yaEsta,
                                        onCheckedChange = { if (it) elegidos.add(ct) else elegidos.remove(ct) },
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(ct.nombre)
                                        Text(
                                            ct.telefono + if (yaEsta) " · ya es cliente" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = elegidos.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                vm.importarContactos(elegidos.toList())
                                alCerrar()
                            }
                        },
                    ) { Text("Importar ${elegidos.size}") }
                    OutlinedButton(onClick = alCerrar) { Text("Cancelar") }
                }
            }
        }
    }
}
