package ar.libreta.reparto.domain

import ar.libreta.reparto.data.Cliente
import ar.libreta.reparto.data.Movimiento
import ar.libreta.reparto.data.TipoMovimiento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class TelefonoTest {
    @Test fun celularPorteño() = assertEquals("5491123456789", Telefono.normalizarAR("011 15 2345-6789"))
    @Test fun conCodigoPais() = assertEquals("5493515551234", Telefono.normalizarAR("+54 9 351 555-1234"))
    @Test fun sinNueve() = assertEquals("5493515551234", Telefono.normalizarAR("+54 351 555 1234"))
    @Test fun soloArea() = assertEquals("5491123456789", Telefono.normalizarAR("11 2345 6789"))
    @Test fun interior15() = assertEquals("5492614567890", Telefono.normalizarAR("0261 15 456-7890"))
    @Test fun vacio() = assertNull(Telefono.normalizarAR("sin número"))
}

class UbicacionTest {
    private fun ok(t: String): ResultadoPin.Ok = Ubicacion.interpretar(t) as ResultadoPin.Ok

    @Test fun coordenadasPlanas() {
        val r = ok("-34.6037, -58.3816")
        assertEquals(-34.6037, r.lat, 1e-9); assertEquals(-58.3816, r.lng, 1e-9)
    }
    @Test fun arroba() = assertEquals(-34.6, ok("https://www.google.com/maps/@-34.6,-58.38,17z").lat, 1e-9)
    @Test fun query() = assertEquals(-58.38, ok("https://maps.google.com/?q=-34.6,-58.38").lng, 1e-9)
    @Test fun queryCodificada() = assertEquals(-34.61, ok("https://www.google.com/maps/search/?api=1&query=-34.61%2C-58.39").lat, 1e-9)
    @Test fun ll() = assertEquals(-34.5, ok("https://maps.google.com/maps?ll=-34.5,-58.4&z=15").lat, 1e-9)
    @Test fun pinDeLugarGanaAlCentro() {
        val r = ok("https://www.google.com/maps/place/Algo/@-34.60,-58.40,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d-34.6012!4d-58.3999")
        assertEquals(-34.6012, r.lat, 1e-9); assertEquals(-58.3999, r.lng, 1e-9)
    }
    @Test fun enlaceCorto() = assertEquals(ResultadoPin.EnlaceCorto, Ubicacion.interpretar("https://maps.app.goo.gl/AbCdEf123"))
    @Test fun direccionNoEsPin() = assertEquals(ResultadoPin.Invalido, Ubicacion.interpretar("Calle 12, 45"))
}

class DineroTest {
    @Test fun formato() = assertEquals("$ 12.345,50", Dinero.formatear(1_234_550))
    @Test fun parseos() {
        assertEquals(300_000L, Dinero.parsear("3000"))
        assertEquals(300_000L, Dinero.parsear("3.000"))
        assertEquals(300_050L, Dinero.parsear("3.000,50"))
        assertEquals(350L, Dinero.parsear("3.5"))
        assertNull(Dinero.parsear("abc"))
    }
}

class LibretaTest {
    private fun m(id: Long, tipo: TipoMovimiento, monto: Long) =
        Movimiento(id = id, clienteId = 1, tipo = tipo, montoCentavos = monto, fecha = id)

    @Test fun saldoYAbiertas() {
        val movs = listOf(
            m(1, TipoMovimiento.ENTREGA_A_CUENTA, 3000),
            m(2, TipoMovimiento.ENTREGA_A_CUENTA, 3000),
            m(3, TipoMovimiento.ENTREGA_A_CUENTA, 3000),
            m(4, TipoMovimiento.COBRO_EFECTIVO, 4000),
        )
        assertEquals(5000L, Libreta.saldo(movs))
        assertEquals(2, Libreta.entregasAbiertas(movs))
    }
    @Test fun topeMonto() {
        val movs = listOf(m(1, TipoMovimiento.ENTREGA_A_CUENTA, 9000))
        assertNotNull(Libreta.validarEntregaACuenta(movs, 2000, 10000, 0))
        assertNull(Libreta.validarEntregaACuenta(movs, 1000, 10000, 0))
    }
    @Test fun topeEntregas() {
        val movs = listOf(m(1, TipoMovimiento.ENTREGA_A_CUENTA, 100), m(2, TipoMovimiento.ENTREGA_A_CUENTA, 100))
        assertNotNull(Libreta.validarEntregaACuenta(movs, 100, 0, 2))
        assertNull(Libreta.validarEntregaACuenta(movs + m(3, TipoMovimiento.COBRO_QR, 100), 100, 0, 2))
    }
}

class RutaTest {
    private fun c(id: Long, nombre: String, lat: Double? = null, lng: Double? = null, zona: String = "") =
        Cliente(id = id, nombre = nombre, lat = lat, lng = lng, zona = zona)

    @Test fun vecinoMasCercanoYSinPinPorZona() {
        val salida = Punto(0.0, 0.0)
        val lista = listOf(
            c(1, "Lejos", 0.0, 0.3),
            c(2, "Cerca", 0.0, 0.1),
            c(3, "Medio", 0.0, 0.2),
            c(4, "Zeta sin pin", zona = "B"),
            c(5, "Ana sin pin", zona = "A"),
            c(6, "Sin zona"),
        )
        val orden = Ruta.ordenar(lista, salida).map { it.cliente.nombre }
        assertEquals(listOf("Cerca", "Medio", "Lejos", "Ana sin pin", "Zeta sin pin", "Sin zona"), orden)
    }
    @Test fun distanciaHaversine() {
        val d = Ruta.distanciaKm(Punto(-34.6037, -58.3816), Punto(-34.9205, -57.9536))
        assertTrue(d in 50.0..55.0)
    }
}

class DiasTest {
    @Test fun mascara() {
        val m = Dias.alternar(Dias.alternar(0, 0), 3) // lunes y jueves
        assertTrue(Dias.visitaEl(m, DayOfWeek.THURSDAY))
        assertTrue(!Dias.visitaEl(m, DayOfWeek.FRIDAY))
    }
}

class MensajesTest {
    @Test fun enlace() {
        val cl = Cliente(nombre = "Ana", telefono = "11 2345 6789", direccion = "Mitre 123", cantidadHabitual = 2, bidonesEnDomicilio = 3)
        val url = Mensajes.enlaceWhatsApp(cl.telefono, Mensajes.pedido(cl, 600_000))
        assertTrue(url.startsWith("https://wa.me/5491123456789?text="))
        assertTrue(!url.contains("+"))
        val texto = Mensajes.pedido(cl, 600_000)
        assertTrue(texto.contains("Mitre 123") && texto.contains("2 bidones") && texto.contains("$ 6.000") && texto.contains("3 bidones"))
    }
}
