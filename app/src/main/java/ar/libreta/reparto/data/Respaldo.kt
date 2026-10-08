package ar.libreta.reparto.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Copia completa de los datos para exportar/importar en JSON.
 * La clave de Dolphin NO se incluye nunca.
 */
@Serializable
data class Respaldo(
    val formato: String = FORMATO,
    val version: Int = 1,
    val exportadoEn: String = "",
    val ajustes: Ajustes = Ajustes(),
    val clientes: List<Cliente> = emptyList(),
    val movimientos: List<Movimiento> = emptyList(),
    val visitas: List<Visita> = emptyList(),
) {
    companion object {
        const val FORMATO = "libreta-reparto"

        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun desdeJson(texto: String): Respaldo {
            val r = json.decodeFromString(serializer(), texto)
            require(r.formato == FORMATO) { "El archivo no es un respaldo de Libreta Reparto." }
            val ids = r.clientes.map { it.id }.toSet()
            require(ids.size == r.clientes.size) { "Hay clientes con id repetido." }
            require(r.movimientos.all { it.clienteId in ids }) { "Hay movimientos de clientes que no existen." }
            require(r.visitas.all { it.clienteId in ids }) { "Hay visitas de clientes que no existen." }
            return r
        }
    }
}
