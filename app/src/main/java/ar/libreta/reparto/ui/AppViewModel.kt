package ar.libreta.reparto.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ar.libreta.reparto.classifier.ClasificadorOpenAICompatible
import ar.libreta.reparto.classifier.Clasificacion
import ar.libreta.reparto.data.Ajustes
import ar.libreta.reparto.data.AjustesStore
import ar.libreta.reparto.data.Cliente
import ar.libreta.reparto.data.FormaPago
import ar.libreta.reparto.data.Movimiento
import ar.libreta.reparto.data.RepartoDatabase
import ar.libreta.reparto.data.Repositorio
import ar.libreta.reparto.data.Respaldo
import ar.libreta.reparto.data.Visita
import ar.libreta.reparto.data.efecto
import ar.libreta.reparto.domain.Dias
import ar.libreta.reparto.domain.Libreta
import ar.libreta.reparto.domain.ParadaCliente
import ar.libreta.reparto.domain.Punto
import ar.libreta.reparto.domain.ResultadoPin
import ar.libreta.reparto.domain.Ruta
import ar.libreta.reparto.domain.Telefono
import ar.libreta.reparto.util.ContactoTelefono
import ar.libreta.reparto.domain.Ubicacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class EstadoHoy(
    val fecha: LocalDate = LocalDate.now(),
    val paradas: List<ParadaCliente> = emptyList(),
    val visitas: Map<Long, Visita> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ajustesStore = AjustesStore(app)
    private val repo = Repositorio(RepartoDatabase.obtener(app), ajustesStore)

    val ajustes: StateFlow<Ajustes> = ajustesStore.ajustes
    val dolphinClave: StateFlow<String> = ajustesStore.dolphinClave

    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes = _mensajes.receiveAsFlow()

    val clientes: StateFlow<List<Cliente>> =
        repo.clientes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val movimientos: StateFlow<List<Movimiento>> =
        repo.movimientos.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Saldo por cliente, calculado siempre a partir de los movimientos. */
    val saldos: StateFlow<Map<Long, Long>> = movimientos
        .map { lista -> lista.groupBy { it.clienteId }.mapValues { (_, m) -> m.sumOf { it.efecto } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val fecha = MutableStateFlow(LocalDate.now())

    /** Se llama al volver a la app: si cambió el día, la hoja de ruta arranca limpia. */
    fun refrescarFecha() {
        val hoy = LocalDate.now()
        if (hoy != fecha.value) fecha.value = hoy
    }

    val hoy: StateFlow<EstadoHoy> = fecha.flatMapLatest { f ->
        combine(clientes, repo.visitasDelDia(Dias.claveDia(f)), ajustes) { cs, vs, a ->
            val delDia = cs.filter { Dias.visitaEl(it.diasVisita, f.dayOfWeek) }
            val inicio = when {
                a.recargaActiva && a.plantaLat != null && a.plantaLng != null -> Punto(a.plantaLat, a.plantaLng)
                a.salidaLat != null && a.salidaLng != null -> Punto(a.salidaLat, a.salidaLng)
                else -> null
            }
            EstadoHoy(f, Ruta.ordenar(delDia, inicio), vs.associateBy { it.clienteId })
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoHoy())

    fun avisar(texto: String) {
        _mensajes.trySend(texto)
    }

    fun movimientosDe(clienteId: Long): List<Movimiento> = movimientos.value.filter { it.clienteId == clienteId }

    fun entregasAbiertas(clienteId: Long): Int = Libreta.entregasAbiertas(movimientosDe(clienteId))

    // ---- Clientes ----
    fun guardarCliente(c: Cliente) = viewModelScope.launch {
        repo.guardarCliente(c)
        avisar("Cliente guardado.")
    }

    fun borrarCliente(c: Cliente) = viewModelScope.launch {
        repo.borrarCliente(c)
        avisar("Cliente borrado.")
    }

    /** Cliente existente con el mismo teléfono normalizado, si lo hay. */
    fun clienteConTelefono(telefono: String): Cliente? {
        val n = Telefono.normalizarAR(telefono) ?: return null
        return clientes.value.firstOrNull { Telefono.normalizarAR(it.telefono) == n }
    }

    /**
     * Crea clientes a partir de contactos elegidos. Saltea los que ya existen (mismo teléfono normalizado)
     * y los repetidos dentro de la selección. Después el repartidor completa dirección, zona, días, etc.
     */
    fun importarContactos(contactos: List<ContactoTelefono>) = viewModelScope.launch {
        val existentes = clientes.value.mapNotNull { Telefono.normalizarAR(it.telefono) }.toMutableSet()
        var creados = 0
        var salteados = 0
        for (ct in contactos) {
            val n = ct.normalizado
            if (n != null && !existentes.add(n)) {
                salteados++
                continue
            }
            repo.guardarCliente(Cliente(nombre = ct.nombre, telefono = ct.telefono, cantidadHabitual = 1))
            creados++
        }
        avisar(
            buildString {
                append(if (creados == 1) "Importaste 1 cliente." else "Importaste $creados clientes.")
                if (salteados > 0) append(" Salteé $salteados que ya estaban.")
                if (creados > 0) append(" Completá dirección, zona y días de visita.")
            },
        )
    }

    /** Interpreta el enlace o coordenadas pegadas y guarda el pin. Devuelve mensaje de error o null. */
    suspend fun guardarPinCliente(clienteId: Long, texto: String): String? =
        when (val r = Ubicacion.interpretar(texto)) {
            is ResultadoPin.Ok -> {
                repo.guardarPin(clienteId, r.lat, r.lng)
                avisar("Pin guardado: ${Ubicacion.formatear(r.lat, r.lng)}")
                null
            }
            ResultadoPin.EnlaceCorto -> MENSAJE_ENLACE_CORTO
            ResultadoPin.Invalido -> MENSAJE_PIN_INVALIDO
        }

    fun borrarPinCliente(clienteId: Long) = viewModelScope.launch {
        repo.guardarPin(clienteId, null, null)
        avisar("Pin borrado.")
    }

    // ---- Ruta del día ----
    suspend fun registrarEntrega(clienteId: Long, llenos: Int, vacios: Int, forma: FormaPago, monto: Long): String? {
        val err = repo.registrarEntrega(clienteId, Dias.claveDia(fecha.value), llenos, vacios, forma, monto)
        if (err == null) avisar("Entrega anotada.")
        return err
    }

    fun registrarNoEstaba(clienteId: Long) = viewModelScope.launch {
        repo.registrarNoEstaba(clienteId, Dias.claveDia(fecha.value))?.let { avisar(it) }
    }

    fun deshacerVisita(visitaId: Long) = viewModelScope.launch {
        repo.deshacerVisita(visitaId)
        avisar("Listo, se deshizo la parada.")
    }

    fun guardarSalida(lat: Double, lng: Double) {
        ajustesStore.actualizar { it.copy(salidaLat = lat, salidaLng = lng, salidaGuardada = System.currentTimeMillis()) }
        avisar("Salida guardada: ${Ubicacion.formatear(lat, lng)}")
    }

    fun borrarSalida() = ajustesStore.actualizar { it.copy(salidaLat = null, salidaLng = null, salidaGuardada = null) }

    fun activarRecarga(activa: Boolean) = ajustesStore.actualizar { it.copy(recargaActiva = activa) }

    // ---- Fiado ----
    suspend fun registrarCobro(clienteId: Long, monto: Long, qr: Boolean): String? {
        val err = repo.registrarCobro(clienteId, monto, qr, if (qr) "Cobro por QR" else "Cobro en efectivo")
        if (err == null) avisar("Cobro anotado.")
        return err
    }

    suspend fun anotarACuenta(clienteId: Long, cantidad: Int, monto: Long): String? {
        val err = repo.anotarACuenta(clienteId, cantidad, monto, "Entrega a cuenta")
        if (err == null) avisar("Entrega a cuenta anotada.")
        return err
    }

    fun anularMovimiento(id: Long) = viewModelScope.launch {
        repo.anularMovimiento(id)
        avisar("Movimiento anulado.")
    }

    // ---- Ajustes ----
    fun actualizarAjustes(cambio: (Ajustes) -> Ajustes) = ajustesStore.actualizar(cambio)

    fun guardarClaveDolphin(clave: String) = ajustesStore.guardarClaveDolphin(clave)

    suspend fun guardarPinPlanta(texto: String): String? =
        when (val r = Ubicacion.interpretar(texto)) {
            is ResultadoPin.Ok -> {
                ajustesStore.actualizar { it.copy(plantaLat = r.lat, plantaLng = r.lng) }
                avisar("Pin de la planta guardado.")
                null
            }
            ResultadoPin.EnlaceCorto -> MENSAJE_ENLACE_CORTO
            ResultadoPin.Invalido -> MENSAJE_PIN_INVALIDO
        }

    // ---- Exportar / importar ----
    fun exportar(uri: Uri) = viewModelScope.launch {
        try {
            val ahora = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val r = repo.respaldo(ahora)
            val texto = Respaldo.json.encodeToString(Respaldo.serializer(), r)
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(texto.toByteArray(Charsets.UTF_8))
                } ?: error("No se pudo abrir el archivo.")
            }
            avisar("Exportaste ${r.clientes.size} clientes y ${r.movimientos.size} movimientos.")
        } catch (e: Exception) {
            avisar("No se pudo exportar: ${e.message}")
        }
    }

    fun importar(uri: Uri) = viewModelScope.launch {
        try {
            val texto = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("No se pudo abrir el archivo.")
            }
            val r = Respaldo.desdeJson(texto)
            repo.restaurar(r)
            refrescarFecha()
            avisar("Importaste ${r.clientes.size} clientes y ${r.movimientos.size} movimientos.")
        } catch (e: Exception) {
            avisar("No se pudo importar: ${e.message}")
        }
    }

    // ---- Dolphin (opcional, apagado por defecto) ----
    /** Solo clasifica y devuelve el resultado para mostrar. No toca la base de datos ni WhatsApp. */
    suspend fun clasificarConDolphin(texto: String): Result<Clasificacion> {
        val a = ajustes.value
        if (!a.dolphinActivo) return Result.failure(IllegalStateException("Dolphin está apagado."))
        val clasificador = ClasificadorOpenAICompatible(a.dolphinUrl, a.dolphinModelo, dolphinClave.value)
        return runCatching { clasificador.clasificar(texto) }
    }

    companion object {
        const val MENSAJE_ENLACE_CORTO =
            "Ese es un enlace corto de Google Maps y sin internet no se puede abrir. " +
                "Abrilo en Maps, tocá el pin y copiá las coordenadas (por ejemplo -34.6037, -58.3816) o el enlace completo."
        const val MENSAJE_PIN_INVALIDO =
            "No encontré coordenadas. Pegá un enlace de Google Maps completo o las coordenadas (por ejemplo -34.6037, -58.3816)."
    }
}
