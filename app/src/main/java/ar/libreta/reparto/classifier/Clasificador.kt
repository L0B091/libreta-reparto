package ar.libreta.reparto.classifier

import kotlinx.serialization.Serializable

/**
 * Dolphin: clasificador OPCIONAL de textos pegados (por ejemplo, un mensaje de WhatsApp).
 * Está APAGADO por defecto. Cuando se activa en Ajustes, solo devuelve una clasificación para mostrar.
 *
 * Límites estrictos:
 * - No confirma pedidos.
 * - No mueve saldos ni crea movimientos.
 * - No escribe ni envía nada por WhatsApp.
 * Este paquete no tiene acceso a la base de datos.
 */
interface ClasificadorDeTexto {
    suspend fun clasificar(texto: String): Clasificacion
}

@Serializable
data class ItemPedido(
    val producto: String = "",
    val cantidad: Int = 0,
)

/** Resultado normalizado: intent ∈ {pedido, pago, consulta, reclamo, otro}; payMode ∈ {efectivo, qr, a_cuenta, null}. */
@Serializable
data class Clasificacion(
    val intent: String = "otro",
    val items: List<ItemPedido> = emptyList(),
    val payMode: String? = null,
) {
    companion object {
        val INTENTS = setOf("pedido", "pago", "consulta", "reclamo", "otro")
        val PAY_MODES = setOf("efectivo", "qr", "a_cuenta")

        fun normalizada(c: Clasificacion): Clasificacion = Clasificacion(
            intent = c.intent.trim().lowercase().takeIf { it in INTENTS } ?: "otro",
            items = c.items.filter { it.cantidad > 0 || it.producto.isNotBlank() }
                .map { ItemPedido(it.producto.trim(), it.cantidad.coerceAtLeast(0)) },
            payMode = c.payMode?.trim()?.lowercase()?.replace(" ", "_")?.takeIf { it in PAY_MODES },
        )
    }
}

class ErrorDeClasificador(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)
