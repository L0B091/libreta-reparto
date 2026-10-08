package ar.libreta.reparto.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RepartoDao {
    // Clientes
    @Query("SELECT * FROM clientes ORDER BY nombre COLLATE NOCASE")
    fun clientes(): Flow<List<Cliente>>

    @Query("SELECT * FROM clientes ORDER BY id")
    suspend fun clientesLista(): List<Cliente>

    @Query("SELECT * FROM clientes WHERE id = :id")
    suspend fun cliente(id: Long): Cliente?

    @Insert
    suspend fun insertarCliente(cliente: Cliente): Long

    @Update
    suspend fun actualizarCliente(cliente: Cliente)

    @Delete
    suspend fun borrarCliente(cliente: Cliente)

    // Movimientos
    @Query("SELECT * FROM movimientos ORDER BY fecha DESC, id DESC")
    fun movimientos(): Flow<List<Movimiento>>

    @Query("SELECT * FROM movimientos ORDER BY id")
    suspend fun movimientosLista(): List<Movimiento>

    @Query("SELECT * FROM movimientos WHERE clienteId = :clienteId ORDER BY fecha, id")
    suspend fun movimientosDe(clienteId: Long): List<Movimiento>

    @Insert
    suspend fun insertarMovimiento(movimiento: Movimiento): Long

    @Query("DELETE FROM movimientos WHERE id = :id")
    suspend fun borrarMovimiento(id: Long)

    // Visitas del día
    @Query("SELECT * FROM visitas WHERE dia = :dia")
    fun visitasDelDia(dia: String): Flow<List<Visita>>

    @Query("SELECT * FROM visitas ORDER BY id")
    suspend fun visitasLista(): List<Visita>

    @Query("SELECT * FROM visitas WHERE clienteId = :clienteId AND dia = :dia LIMIT 1")
    suspend fun visita(clienteId: Long, dia: String): Visita?

    @Query("SELECT * FROM visitas WHERE id = :id")
    suspend fun visitaPorId(id: Long): Visita?

    @Insert
    suspend fun insertarVisita(visita: Visita): Long

    @Query("DELETE FROM visitas WHERE id = :id")
    suspend fun borrarVisita(id: Long)

    @Query("UPDATE visitas SET movimientoId = NULL WHERE movimientoId = :movimientoId")
    suspend fun desvincularMovimiento(movimientoId: Long)

    // Importación
    @Insert
    suspend fun insertarClientes(clientes: List<Cliente>)

    @Insert
    suspend fun insertarMovimientos(movimientos: List<Movimiento>)

    @Insert
    suspend fun insertarVisitas(visitas: List<Visita>)

    @Query("DELETE FROM visitas")
    suspend fun borrarTodasLasVisitas()

    @Query("DELETE FROM movimientos")
    suspend fun borrarTodosLosMovimientos()

    @Query("DELETE FROM clientes")
    suspend fun borrarTodosLosClientes()
}
