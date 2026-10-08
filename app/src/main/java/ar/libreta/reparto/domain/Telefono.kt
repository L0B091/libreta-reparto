package ar.libreta.reparto.domain

/**
 * Normaliza teléfonos argentinos al formato que espera wa.me: 549 + código de área + número, solo dígitos.
 * Ejemplos: "011 15 2345-6789" → "5491123456789"; "+54 9 351 555-1234" → "5493515551234".
 */
object Telefono {
    fun normalizarAR(crudo: String): String? {
        var d = crudo.filter { it.isDigit() }
        if (d.isEmpty()) return null
        if (d.startsWith("00")) d = d.drop(2)
        if (d.startsWith("54")) {
            d = d.drop(2)
            if (d.startsWith("9")) d = d.drop(1)
        }
        if (d.startsWith("0")) d = d.drop(1)
        // Si quedó con el "15" de celular (área + 15 + número = 12 dígitos), se lo sacamos.
        if (d.length == 12) {
            for (i in 2..4) {
                if (d.substring(i, i + 2) == "15") {
                    d = d.removeRange(i, i + 2)
                    break
                }
            }
        }
        if (d.length < 6) return null
        return "549$d"
    }
}
