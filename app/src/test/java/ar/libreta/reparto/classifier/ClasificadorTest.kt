package ar.libreta.reparto.classifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClasificadorTest {
    @Test fun normalizaRespuesta() {
        val c = ClasificadorOpenAICompatible.interpretarContenido(
            "Acá va: {\"intent\":\"PEDIDO\",\"items\":[{\"producto\":\"bidón 20 L\",\"cantidad\":2}],\"payMode\":\"a cuenta\"}",
        )
        assertEquals("pedido", c.intent)
        assertEquals(2, c.items.first().cantidad)
        assertEquals("a_cuenta", c.payMode)
    }
    @Test fun valoresDesconocidos() {
        val c = ClasificadorOpenAICompatible.interpretarContenido("{\"intent\":\"comprar\",\"payMode\":\"bitcoin\"}")
        assertEquals("otro", c.intent)
        assertNull(c.payMode)
    }
    @Test fun endpoint() {
        assertEquals("https://x/v1/chat/completions", ClasificadorOpenAICompatible.endpoint("https://x/v1/"))
    }
}
