package ar.libreta.reparto.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.libreta.reparto.domain.Dinero
import ar.libreta.reparto.domain.Ubicacion
import java.time.LocalDate

@Composable
fun AjustesScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()
    val clave by vm.dolphinClave.collectAsStateWithLifecycle()

    var nombreReparto by remember { mutableStateOf(ajustes.nombreReparto) }
    var precio by remember { mutableStateOf(Dinero.aTexto(ajustes.precioBidonCentavos)) }
    var topeMonto by remember { mutableStateOf(Dinero.aTexto(ajustes.topeMontoCentavos)) }
    var topeEntregas by remember { mutableStateOf(ajustes.topeEntregasAbiertas.toString()) }
    var errorNegocio by remember { mutableStateOf<String?>(null) }

    var plantaNombre by remember { mutableStateOf(ajustes.plantaNombre) }
    var plantaDireccion by remember { mutableStateOf(ajustes.plantaDireccion) }
    var plantaTelefono by remember { mutableStateOf(ajustes.plantaTelefono) }
    var pinPlanta by remember { mutableStateOf(false) }

    var dolphinUrl by remember { mutableStateOf(ajustes.dolphinUrl) }
    var dolphinModelo by remember { mutableStateOf(ajustes.dolphinModelo) }
    var dolphinClave by remember { mutableStateOf(clave) }
    var clasificando by remember { mutableStateOf(false) }
    var confirmarImportar by remember { mutableStateOf(false) }

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportar(uri)
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importar(uri)
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Seccion("Precio y topes del fiado") {
            CampoTexto(nombreReparto, { nombreReparto = it }, "Nombre del reparto (para los mensajes)")
            CampoTexto(precio, { precio = it }, "Precio por bidón", teclado = KeyboardType.Decimal)
            CampoTexto(
                topeMonto, { topeMonto = it }, "Tope por monto", teclado = KeyboardType.Decimal,
                ayuda = "Saldo máximo a cuenta por cliente. 0 = sin tope.",
            )
            CampoTexto(
                topeEntregas, { topeEntregas = it }, "Tope de entregas abiertas", teclado = KeyboardType.Number,
                ayuda = "Entregas a cuenta sin cubrir por cobros (se cubren de la más vieja a la más nueva). 0 = sin tope.",
            )
            TextoError(errorNegocio)
            Button(onClick = {
                val p = Dinero.parsear(precio)
                val t = Dinero.parsear(topeMonto)
                val e = topeEntregas.trim().toIntOrNull()
                if (p == null || t == null || e == null || e < 0) {
                    errorNegocio = "Revisá los números."
                } else {
                    errorNegocio = null
                    vm.actualizarAjustes {
                        it.copy(
                            nombreReparto = nombreReparto.trim(),
                            precioBidonCentavos = p,
                            topeMontoCentavos = t,
                            topeEntregasAbiertas = e,
                        )
                    }
                    vm.avisar("Guardaste precio y topes.")
                }
            }) { Text("Guardar") }
        }

        Seccion("Planta de recarga") {
            Text(
                "Se carga una sola vez. En Hoy, \"Agregar recarga\" la pone primera hasta que tocás \"Listo\".",
                style = MaterialTheme.typography.bodySmall,
            )
            CampoTexto(plantaNombre, { plantaNombre = it }, "Nombre")
            CampoTexto(plantaDireccion, { plantaDireccion = it }, "Dirección")
            CampoTexto(plantaTelefono, { plantaTelefono = it }, "Teléfono", teclado = KeyboardType.Phone)
            Text(
                if (ajustes.plantaLat != null && ajustes.plantaLng != null) "Pin: ${Ubicacion.formatear(ajustes.plantaLat!!, ajustes.plantaLng!!)}"
                else "Sin pin",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.actualizarAjustes {
                        it.copy(
                            plantaNombre = plantaNombre.trim(),
                            plantaDireccion = plantaDireccion.trim(),
                            plantaTelefono = plantaTelefono.trim(),
                        )
                    }
                    vm.avisar("Guardaste la planta.")
                }) { Text("Guardar planta") }
                OutlinedButton(onClick = { pinPlanta = true }) { Text("Cargar pin") }
            }
        }

        Seccion("Salida") {
            Text(
                if (ajustes.salidaLat != null && ajustes.salidaLng != null) {
                    "Guardada: ${Ubicacion.formatear(ajustes.salidaLat!!, ajustes.salidaLng!!)}"
                } else {
                    "Sin salida. Guardala desde Hoy con tu ubicación actual."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (ajustes.tieneSalida) OutlinedButton(onClick = { vm.borrarSalida() }) { Text("Borrar salida") }
        }

        Seccion("Datos") {
            Text(
                "Todo queda guardado en este teléfono. Exportá un JSON seguido para tener copia. La clave de Dolphin no se exporta.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportar.launch("libreta-reparto-${LocalDate.now()}.json") }) { Text("Exportar JSON") }
                OutlinedButton(onClick = { confirmarImportar = true }) { Text("Importar JSON") }
            }
        }

        Seccion("Dolphin (opcional)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Clasificador de texto", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Apagado por defecto. Si lo prendés, solo clasifica un texto pegado y devuelve JSON con intent, items y payMode. " +
                            "No confirma pedidos, no mueve saldos y no escribe por WhatsApp. Usa un servidor compatible con OpenAI que configurás vos.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = ajustes.dolphinActivo, onCheckedChange = { activo -> vm.actualizarAjustes { it.copy(dolphinActivo = activo) } })
            }
            CampoTexto(dolphinUrl, { dolphinUrl = it }, "URL base (ej. https://servidor/v1)", teclado = KeyboardType.Uri)
            CampoTexto(dolphinModelo, { dolphinModelo = it }, "Modelo")
            androidx.compose.material3.OutlinedTextField(
                value = dolphinClave,
                onValueChange = { dolphinClave = it },
                label = { Text("Clave (API key)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.actualizarAjustes { it.copy(dolphinUrl = dolphinUrl.trim(), dolphinModelo = dolphinModelo.trim()) }
                    vm.guardarClaveDolphin(dolphinClave.trim())
                    vm.avisar("Guardaste Dolphin.")
                }) { Text("Guardar") }
                OutlinedButton(
                    enabled = ajustes.dolphinActivo,
                    onClick = { clasificando = true },
                ) { Text("Clasificar texto") }
            }
        }

        HorizontalDivider()
        Text(
            "Libreta Reparto · APK de prueba (debug). No está publicada en Play Store.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (pinPlanta) {
        DialogoPin(
            titulo = "Pin de la planta",
            alGuardar = { vm.guardarPinPlanta(it) },
            alCerrar = { pinPlanta = false },
        )
    }
    if (clasificando) {
        DialogoDolphin(alClasificar = { vm.clasificarConDolphin(it) }, alCerrar = { clasificando = false })
    }
    if (confirmarImportar) {
        Confirmar(
            titulo = "Importar JSON",
            texto = "Importar reemplaza TODOS los clientes, movimientos y ajustes de este teléfono por los del archivo. ¿Seguís?",
            confirmar = "Elegir archivo",
            alConfirmar = { importar.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            alCerrar = { confirmarImportar = false },
        )
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            contenido()
        }
    }
}
