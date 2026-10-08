package ar.libreta.reparto.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/** Montos en centavos, formato argentino: $ 12.345,50 */
object Dinero {
    private val simbolos = DecimalFormatSymbols(Locale("es", "AR")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    fun formatear(centavos: Long): String {
        val signo = if (centavos < 0) "-" else ""
        val a = abs(centavos)
        val pesos = a / 100
        val cent = a % 100
        val entero = DecimalFormat("#,##0", simbolos).format(pesos)
        return if (cent == 0L) "$signo$ $entero" else "$signo$ $entero,${cent.toString().padStart(2, '0')}"
    }

    /** Texto editable sin símbolo: "3000" o "3000,50". */
    fun aTexto(centavos: Long): String {
        val pesos = centavos / 100
        val cent = abs(centavos % 100)
        return if (cent == 0L) pesos.toString() else "$pesos,${cent.toString().padStart(2, '0')}"
    }

    /**
     * Interpreta lo que escribe el usuario: "3000", "3.000", "3000,5", "3.000,50", "$ 3.000".
     * Devuelve centavos o null si no se entiende.
     */
    fun parsear(texto: String): Long? {
        var t = texto.trim().replace("$", "").replace(" ", "")
        if (t.isEmpty()) return null
        if (t.contains(',')) {
            t = t.replace(".", "").replace(',', '.')
        } else if (Regex("""^\d{1,3}(\.\d{3})+$""").matches(t)) {
            t = t.replace(".", "")
        }
        val valor = t.toBigDecimalOrNull() ?: return null
        if (valor.signum() < 0) return null
        return valor.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }
}
