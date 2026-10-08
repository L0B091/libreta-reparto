package ar.libreta.reparto.domain

import ar.libreta.reparto.data.Cliente
import java.net.URLEncoder

/** Textos fijos para WhatsApp. La app solo abre WhatsApp con el texto cargado: nunca envía nada sola. */
object Mensajes {
    private fun bidones(n: Int) = if (n == 1) "bidón" else "bidones"

    fun pedido(cliente: Cliente, saldoCentavos: Long, nombreReparto: String = ""): String {
        val desde = if (nombreReparto.isBlank()) "del reparto de agua" else "de $nombreReparto"
        val saldo = when {
            saldoCentavos > 0 -> "Tu saldo pendiente es de ${Dinero.formatear(saldoCentavos)}."
            saldoCentavos < 0 -> "Tenés ${Dinero.formatear(-saldoCentavos)} a favor."
            else -> "Estás al día, no tenés saldo pendiente."
        }
        val direccion = cliente.direccion.ifBlank { "tu domicilio" }
        return "¡Hola ${cliente.nombre}! Te escribo $desde. " +
            "Hoy paso por $direccion. " +
            "Te llevo ${cliente.cantidadHabitual} ${bidones(cliente.cantidadHabitual)}, como siempre. " +
            "$saldo " +
            "Tenés ${cliente.bidonesEnDomicilio} ${bidones(cliente.bidonesEnDomicilio)} nuestros en tu domicilio. " +
            "Si necesitás otra cantidad, avisame por acá. ¡Gracias!"
    }

    fun pedirPin(cliente: Cliente, nombreReparto: String = ""): String {
        val desde = if (nombreReparto.isBlank()) "del reparto de agua" else "de $nombreReparto"
        return "¡Hola ${cliente.nombre}! Te escribo $desde. " +
            "Para armar mejor el recorrido, ¿me mandás tu ubicación por acá? " +
            "Tocá el clip 📎, elegí \"Ubicación\" y después \"Enviar tu ubicación actual\" cuando estés en tu casa. ¡Gracias!"
    }

    /** Arma https://wa.me/549...?text=... Si no hay teléfono válido, deja elegir el contacto en WhatsApp. */
    fun enlaceWhatsApp(telefono: String, texto: String): String {
        val numero = Telefono.normalizarAR(telefono) ?: ""
        val t = URLEncoder.encode(texto, "UTF-8").replace("+", "%20")
        return "https://wa.me/$numero?text=$t"
    }
}
