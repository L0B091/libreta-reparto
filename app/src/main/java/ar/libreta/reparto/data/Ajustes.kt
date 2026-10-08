package ar.libreta.reparto.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * Ajustes de la app. Se exportan en el JSON (salvo la clave de Dolphin, que nunca sale del teléfono).
 * Montos en centavos. Un tope en 0 significa "sin tope".
 */
@Serializable
data class Ajustes(
    val precioBidonCentavos: Long = 300_000,
    val topeMontoCentavos: Long = 5_000_000,
    val topeEntregasAbiertas: Int = 3,
    val nombreReparto: String = "",
    val plantaNombre: String = "",
    val plantaDireccion: String = "",
    val plantaTelefono: String = "",
    val plantaLat: Double? = null,
    val plantaLng: Double? = null,
    val salidaLat: Double? = null,
    val salidaLng: Double? = null,
    val salidaGuardada: Long? = null,
    val recargaActiva: Boolean = false,
    val dolphinActivo: Boolean = false,
    val dolphinUrl: String = "",
    val dolphinModelo: String = "",
) {
    val plantaConfigurada: Boolean get() = plantaNombre.isNotBlank()
    val plantaTienePin: Boolean get() = plantaLat != null && plantaLng != null
    val tieneSalida: Boolean get() = salidaLat != null && salidaLng != null
}

/** Guarda los ajustes en SharedPreferences privadas de la app. */
class AjustesStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    private val secreto: SharedPreferences =
        context.getSharedPreferences("dolphin_secreto", Context.MODE_PRIVATE)

    private val _ajustes = MutableStateFlow(leer())
    val ajustes: StateFlow<Ajustes> = _ajustes.asStateFlow()

    private val _dolphinClave = MutableStateFlow(secreto.getString("clave", "") ?: "")
    val dolphinClave: StateFlow<String> = _dolphinClave.asStateFlow()

    fun actualizar(cambio: (Ajustes) -> Ajustes) {
        val nuevo = cambio(_ajustes.value)
        escribir(nuevo)
        _ajustes.value = nuevo
    }

    fun guardarClaveDolphin(clave: String) {
        secreto.edit().putString("clave", clave).apply()
        _dolphinClave.value = clave
    }

    private fun leer(): Ajustes {
        val d = Ajustes()
        return Ajustes(
            precioBidonCentavos = prefs.getLong("precioBidonCentavos", d.precioBidonCentavos),
            topeMontoCentavos = prefs.getLong("topeMontoCentavos", d.topeMontoCentavos),
            topeEntregasAbiertas = prefs.getInt("topeEntregasAbiertas", d.topeEntregasAbiertas),
            nombreReparto = prefs.getString("nombreReparto", "") ?: "",
            plantaNombre = prefs.getString("plantaNombre", "") ?: "",
            plantaDireccion = prefs.getString("plantaDireccion", "") ?: "",
            plantaTelefono = prefs.getString("plantaTelefono", "") ?: "",
            plantaLat = prefs.getString("plantaLat", null)?.toDoubleOrNull(),
            plantaLng = prefs.getString("plantaLng", null)?.toDoubleOrNull(),
            salidaLat = prefs.getString("salidaLat", null)?.toDoubleOrNull(),
            salidaLng = prefs.getString("salidaLng", null)?.toDoubleOrNull(),
            salidaGuardada = if (prefs.contains("salidaGuardada")) prefs.getLong("salidaGuardada", 0) else null,
            recargaActiva = prefs.getBoolean("recargaActiva", false),
            dolphinActivo = prefs.getBoolean("dolphinActivo", false),
            dolphinUrl = prefs.getString("dolphinUrl", "") ?: "",
            dolphinModelo = prefs.getString("dolphinModelo", "") ?: "",
        )
    }

    private fun escribir(a: Ajustes) {
        prefs.edit().apply {
            putLong("precioBidonCentavos", a.precioBidonCentavos)
            putLong("topeMontoCentavos", a.topeMontoCentavos)
            putInt("topeEntregasAbiertas", a.topeEntregasAbiertas)
            putString("nombreReparto", a.nombreReparto)
            putString("plantaNombre", a.plantaNombre)
            putString("plantaDireccion", a.plantaDireccion)
            putString("plantaTelefono", a.plantaTelefono)
            putString("plantaLat", a.plantaLat?.toString())
            putString("plantaLng", a.plantaLng?.toString())
            putString("salidaLat", a.salidaLat?.toString())
            putString("salidaLng", a.salidaLng?.toString())
            if (a.salidaGuardada != null) putLong("salidaGuardada", a.salidaGuardada) else remove("salidaGuardada")
            putBoolean("recargaActiva", a.recargaActiva)
            putBoolean("dolphinActivo", a.dolphinActivo)
            putString("dolphinUrl", a.dolphinUrl)
            putString("dolphinModelo", a.dolphinModelo)
        }.apply()
    }
}
