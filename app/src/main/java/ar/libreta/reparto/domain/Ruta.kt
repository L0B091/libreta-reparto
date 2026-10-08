package ar.libreta.reparto.domain

import ar.libreta.reparto.data.Cliente
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Punto(val lat: Double, val lng: Double)

/** Una parada de clientes en la hoja de ruta, con la distancia en línea recta desde la parada anterior. */
data class ParadaCliente(val cliente: Cliente, val distanciaKm: Double?)

/**
 * Orden de la hoja de ruta (sin tránsito real, en línea recta):
 * 1. La planta de recarga, si está activa (se muestra aparte, primera).
 * 2. Clientes con pin: vecino más cercano, arrancando desde la planta (si está activa y tiene pin)
 *    o desde la salida guardada. Si no hay punto de partida, arranca por el primer cliente con pin (por nombre).
 * 3. Clientes sin pin al final, ordenados por zona (las vacías al último) y después por nombre.
 */
object Ruta {
    private const val RADIO_TIERRA_KM = 6371.0

    fun distanciaKm(a: Punto, b: Punto): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * RADIO_TIERRA_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun ordenar(clientes: List<Cliente>, inicio: Punto?): List<ParadaCliente> {
        val conPin = clientes.filter { it.lat != null && it.lng != null }.toMutableList()
        val sinPin = clientes.filter { it.lat == null || it.lng == null }

        val resultado = mutableListOf<ParadaCliente>()
        var actual: Punto? = inicio
        if (actual == null && conPin.isNotEmpty()) {
            val primero = conPin.minWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.nombre })
            conPin.remove(primero)
            resultado += ParadaCliente(primero, null)
            actual = Punto(primero.lat!!, primero.lng!!)
        }
        while (conPin.isNotEmpty()) {
            val desde = actual!!
            val siguiente = conPin.minWith(
                compareBy<Cliente> { distanciaKm(desde, Punto(it.lat!!, it.lng!!)) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.nombre },
            )
            val p = Punto(siguiente.lat!!, siguiente.lng!!)
            resultado += ParadaCliente(siguiente, distanciaKm(desde, p))
            conPin.remove(siguiente)
            actual = p
        }
        resultado += sinPin
            .sortedWith(
                compareBy<Cliente> { it.zona.isBlank() }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.zona.trim() }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.nombre },
            )
            .map { ParadaCliente(it, null) }
        return resultado
    }
}
