package ar.libreta.reparto.domain

import java.net.URLDecoder

/** Resultado de interpretar un enlace de Google Maps o coordenadas pegadas. */
sealed interface ResultadoPin {
    data class Ok(val lat: Double, val lng: Double) : ResultadoPin
    data object EnlaceCorto : ResultadoPin
    data object Invalido : ResultadoPin
}

/**
 * Lee coordenadas de:
 * - "-34.6037, -58.3816" (texto plano)
 * - enlaces con "!3d-34.6!4d-58.3", "@-34.6,-58.3,17z", "?q=-34.6,-58.3", "ll=", "query=", "destination=", "geo:"
 * Los enlaces cortos (maps.app.goo.gl, goo.gl/maps) no se pueden resolver sin internet:
 * en ese caso se pide pegar las coordenadas o el enlace completo.
 */
object Ubicacion {
    private const val NUM = """(-?\d{1,3}(?:\.\d+)?)"""
    private const val DEC = """(-?\d{1,3}\.\d+)"""

    private val patrones = listOf(
        Regex("""!3d$NUM!4d$NUM"""),
        Regex("""[?&](?:q|query|ll|destination|daddr|center|sll)=(?:loc:)?$NUM\s*,\s*$NUM"""),
        Regex("""geo:$NUM\s*,\s*$NUM"""),
        Regex("""@$DEC\s*,\s*$DEC"""),
        Regex("""(?<![\d.])$DEC\s*,\s*$DEC(?![\d.])"""),
    )

    fun interpretar(entrada: String): ResultadoPin {
        val crudo = entrada.trim()
        if (crudo.isEmpty()) return ResultadoPin.Invalido
        val texto = try {
            URLDecoder.decode(crudo, "UTF-8")
        } catch (e: IllegalArgumentException) {
            crudo
        }
        for (p in patrones) {
            for (m in p.findAll(texto)) {
                val lat = m.groupValues[1].toDoubleOrNull() ?: continue
                val lng = m.groupValues[2].toDoubleOrNull() ?: continue
                if (lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)) {
                    return ResultadoPin.Ok(lat, lng)
                }
            }
        }
        val lower = texto.lowercase()
        if (lower.contains("goo.gl") || lower.contains("maps.app")) return ResultadoPin.EnlaceCorto
        return ResultadoPin.Invalido
    }

    fun formatear(lat: Double, lng: Double): String =
        String.format(java.util.Locale.US, "%.6f, %.6f", lat, lng)
}
