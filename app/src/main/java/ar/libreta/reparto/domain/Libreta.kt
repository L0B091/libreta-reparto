package ar.libreta.reparto.domain

import ar.libreta.reparto.data.Movimiento
import ar.libreta.reparto.data.TipoMovimiento
import ar.libreta.reparto.data.efecto

/**
 * Reglas del fiado.
 *
 * - El saldo es la suma de los movimientos: entrega a cuenta suma, cobro (efectivo o QR) resta.
 * - "Entregas abiertas": se aplican los cobros a las entregas a cuenta más viejas primero (FIFO).
 *   Cada entrega a cuenta que no quedó cubierta del todo por los cobros cuenta como abierta.
 *   Ejemplo: entregas de $3.000, $3.000 y $3.000 con un cobro de $4.000 → la primera queda
 *   cubierta y quedan 2 abiertas.
 */
object Libreta {

    fun saldo(movimientos: List<Movimiento>): Long = movimientos.sumOf { it.efecto }

    fun entregasAbiertas(movimientos: List<Movimiento>): Int {
        val ordenados = movimientos.sortedWith(compareBy<Movimiento> { it.fecha }.thenBy { it.id })
        var disponible = ordenados.filter { it.tipo != TipoMovimiento.ENTREGA_A_CUENTA }.sumOf { it.montoCentavos }
        var abiertas = 0
        for (m in ordenados.filter { it.tipo == TipoMovimiento.ENTREGA_A_CUENTA }) {
            if (disponible >= m.montoCentavos) {
                disponible -= m.montoCentavos
            } else {
                disponible = 0
                abiertas++
            }
        }
        return abiertas
    }

    /**
     * Devuelve un mensaje de error si anotar una nueva entrega a cuenta por [montoCentavos]
     * supera algún tope. Un tope en 0 significa "sin tope". Devuelve null si se puede anotar.
     */
    fun validarEntregaACuenta(
        movimientos: List<Movimiento>,
        montoCentavos: Long,
        topeMontoCentavos: Long,
        topeEntregasAbiertas: Int,
    ): String? {
        if (montoCentavos <= 0) return "El monto tiene que ser mayor a cero."
        val saldoActual = saldo(movimientos)
        val saldoNuevo = saldoActual + montoCentavos
        if (topeMontoCentavos > 0 && saldoNuevo > topeMontoCentavos) {
            return "No se puede anotar a cuenta: el saldo quedaría en ${Dinero.formatear(saldoNuevo)} " +
                "y el tope es ${Dinero.formatear(topeMontoCentavos)}. Cobrá antes o pedí que pague en efectivo o QR."
        }
        val abiertas = entregasAbiertas(movimientos)
        if (topeEntregasAbiertas > 0 && abiertas + 1 > topeEntregasAbiertas) {
            return "No se puede anotar a cuenta: ya tiene $abiertas ${if (abiertas == 1) "entrega abierta" else "entregas abiertas"} " +
                "y el tope es $topeEntregasAbiertas. Cobrá antes o pedí que pague en efectivo o QR."
        }
        return null
    }
}
