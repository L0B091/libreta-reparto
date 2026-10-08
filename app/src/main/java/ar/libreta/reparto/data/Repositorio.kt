package ar.libreta.reparto.data

import androidx.room.withTransaction
import ar.libreta.reparto.domain.Libreta

class Repositorio(private val db: RepartoDatabase, val ajustesStore: AjustesStore) {
    private val dao = db.dao()

    val clientes = dao.clientes()
    val movimientos = dao.movimientos()
    fun visitasDelDia(dia: String) = dao.visitasDelDia(dia)

    suspend fun guardarCliente(c: Cliente): Long =
        if (c.id == 0L) dao.insertarCliente(c) else { dao.actualizarCliente(c); c.id }

    suspend fun borrarCliente(c: Cliente) = dao.borrarCliente(c)

    suspend fun guardarPin(clienteId: Long, lat: Double?, lng: Double?) {
        val c = dao.cliente(clienteId) ?: return
        dao.actualizarCliente(c.copy(lat = lat, lng = lng))
    }

    /** Anota una entrega a cuenta fuera de la ruta. Devuelve error si supera un tope. */
    suspend fun anotarACuenta(clienteId: Long, cantidad: Int, montoCentavos: Long, nota: String): String? =
        db.withTransaction {
            val a = ajustesStore.ajustes.value
            val movs = dao.movimientosDe(clienteId)
            Libreta.validarEntregaACuenta(movs, montoCentavos, a.topeMontoCentavos, a.topeEntregasAbiertas)
                ?.let { return@withTransaction it }
            dao.insertarMovimiento(
                Movimiento(
                    clienteId = clienteId,
                    tipo = TipoMovimiento.ENTREGA_A_CUENTA,
                    montoCentavos = montoCentavos,
                    cantidad = cantidad,
                    fecha = System.currentTimeMillis(),
                    nota = nota,
                ),
            )
            null
        }

    suspend fun registrarCobro(clienteId: Long, montoCentavos: Long, qr: Boolean, nota: String): String? {
        if (montoCentavos <= 0) return "El monto tiene que ser mayor a cero."
        dao.insertarMovimiento(
            Movimiento(
                clienteId = clienteId,
                tipo = if (qr) TipoMovimiento.COBRO_QR else TipoMovimiento.COBRO_EFECTIVO,
                montoCentavos = montoCentavos,
                fecha = System.currentTimeMillis(),
                nota = nota,
            ),
        )
        return null
    }

    /** Anula un movimiento cargado por error (queda como si nunca se hubiera anotado). */
    suspend fun anularMovimiento(id: Long) = db.withTransaction {
        dao.desvincularMovimiento(id)
        dao.borrarMovimiento(id)
    }

    /**
     * "Entregué": guarda el estado del día, actualiza bidones en domicilio
     * (antes − vacíos retirados + llenos entregados) y, si fue a cuenta, crea el movimiento.
     */
    suspend fun registrarEntrega(
        clienteId: Long,
        dia: String,
        llenos: Int,
        vacios: Int,
        forma: FormaPago,
        montoCentavos: Long,
    ): String? = db.withTransaction {
        val c = dao.cliente(clienteId) ?: return@withTransaction "El cliente ya no existe."
        if (dao.visita(clienteId, dia) != null) return@withTransaction "Ya marcaste esta parada hoy. Deshacé primero."
        if (llenos < 0 || vacios < 0) return@withTransaction "Las cantidades no pueden ser negativas."
        if (llenos == 0 && vacios == 0) return@withTransaction "Cargá cuántos bidones entregaste o retiraste."
        val ahora = System.currentTimeMillis()
        var movimientoId: Long? = null
        if (forma == FormaPago.A_CUENTA) {
            val a = ajustesStore.ajustes.value
            val movs = dao.movimientosDe(clienteId)
            Libreta.validarEntregaACuenta(movs, montoCentavos, a.topeMontoCentavos, a.topeEntregasAbiertas)
                ?.let { return@withTransaction it }
            movimientoId = dao.insertarMovimiento(
                Movimiento(
                    clienteId = clienteId,
                    tipo = TipoMovimiento.ENTREGA_A_CUENTA,
                    montoCentavos = montoCentavos,
                    cantidad = llenos,
                    fecha = ahora,
                    nota = "Entrega en ruta",
                ),
            )
        }
        dao.insertarVisita(
            Visita(
                clienteId = clienteId,
                dia = dia,
                estado = EstadoVisita.ENTREGUE,
                llenos = llenos,
                vacios = vacios,
                formaPago = forma,
                montoCentavos = montoCentavos,
                movimientoId = movimientoId,
                bidonesAntes = c.bidonesEnDomicilio,
                hora = ahora,
            ),
        )
        val nuevos = (c.bidonesEnDomicilio - vacios + llenos).coerceAtLeast(0)
        dao.actualizarCliente(c.copy(bidonesEnDomicilio = nuevos))
        null
    }

    suspend fun registrarNoEstaba(clienteId: Long, dia: String): String? = db.withTransaction {
        val c = dao.cliente(clienteId) ?: return@withTransaction "El cliente ya no existe."
        if (dao.visita(clienteId, dia) != null) return@withTransaction "Ya marcaste esta parada hoy. Deshacé primero."
        dao.insertarVisita(
            Visita(
                clienteId = clienteId,
                dia = dia,
                estado = EstadoVisita.NO_ESTABA,
                bidonesAntes = c.bidonesEnDomicilio,
                hora = System.currentTimeMillis(),
            ),
        )
        null
    }

    /** Deshace el estado del día: borra la visita, el movimiento a cuenta que generó y repone los bidones. */
    suspend fun deshacerVisita(visitaId: Long) = db.withTransaction {
        val v = dao.visitaPorId(visitaId) ?: return@withTransaction
        v.movimientoId?.let { dao.borrarMovimiento(it) }
        if (v.estado == EstadoVisita.ENTREGUE) {
            dao.cliente(v.clienteId)?.let { c ->
                val revertido = (c.bidonesEnDomicilio - v.llenos + v.vacios).coerceAtLeast(0)
                dao.actualizarCliente(c.copy(bidonesEnDomicilio = revertido))
            }
        }
        dao.borrarVisita(v.id)
    }

    suspend fun respaldo(exportadoEn: String): Respaldo = db.withTransaction {
        Respaldo(
            exportadoEn = exportadoEn,
            ajustes = ajustesStore.ajustes.value,
            clientes = dao.clientesLista(),
            movimientos = dao.movimientosLista(),
            visitas = dao.visitasLista(),
        )
    }

    /** Reemplaza TODOS los datos por los del respaldo. */
    suspend fun restaurar(r: Respaldo) {
        db.withTransaction {
            dao.borrarTodasLasVisitas()
            dao.borrarTodosLosMovimientos()
            dao.borrarTodosLosClientes()
            dao.insertarClientes(r.clientes)
            dao.insertarMovimientos(r.movimientos)
            dao.insertarVisitas(r.visitas)
        }
        ajustesStore.actualizar { r.ajustes }
    }
}
