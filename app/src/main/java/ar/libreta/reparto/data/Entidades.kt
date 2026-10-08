package ar.libreta.reparto.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Cliente del reparto.
 * [diasVisita] es una máscara de bits: lunes = bit 0 ... domingo = bit 6.
 */
@Serializable
@Entity(tableName = "clientes")
data class Cliente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val telefono: String = "",
    val direccion: String = "",
    val zona: String = "",
    val bidonesEnDomicilio: Int = 0,
    val cantidadHabitual: Int = 1,
    val diasVisita: Int = 0,
    val lat: Double? = null,
    val lng: Double? = null,
    val notas: String = "",
)

val Cliente.tienePin: Boolean get() = lat != null && lng != null

/** Tipos de movimiento del fiado. La entrega a cuenta suma; los cobros restan. */
@Serializable
enum class TipoMovimiento { ENTREGA_A_CUENTA, COBRO_EFECTIVO, COBRO_QR }

/**
 * Movimiento de la libreta de fiado. El saldo de un cliente es SIEMPRE la suma
 * de sus movimientos: nunca se guarda ni se edita a mano.
 */
@Serializable
@Entity(
    tableName = "movimientos",
    foreignKeys = [
        ForeignKey(
            entity = Cliente::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("clienteId")],
)
data class Movimiento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: Long,
    val tipo: TipoMovimiento,
    val montoCentavos: Long,
    val cantidad: Int = 0,
    val fecha: Long,
    val nota: String = "",
)

/** Efecto sobre el saldo: positivo si suma deuda, negativo si la baja. */
val Movimiento.efecto: Long
    get() = if (tipo == TipoMovimiento.ENTREGA_A_CUENTA) montoCentavos else -montoCentavos

@Serializable
enum class EstadoVisita { ENTREGUE, NO_ESTABA }

@Serializable
enum class FormaPago { EFECTIVO, QR, A_CUENTA }

/**
 * Estado de una parada en un día puntual ("Entregué" o "No estaba").
 * Al cambiar el día, la hoja de ruta arranca limpia porque se filtra por [dia] (yyyy-MM-dd).
 */
@Serializable
@Entity(
    tableName = "visitas",
    foreignKeys = [
        ForeignKey(
            entity = Cliente::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["clienteId", "dia"], unique = true)],
)
data class Visita(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: Long,
    val dia: String,
    val estado: EstadoVisita,
    val llenos: Int = 0,
    val vacios: Int = 0,
    val formaPago: FormaPago? = null,
    val montoCentavos: Long = 0,
    val movimientoId: Long? = null,
    val bidonesAntes: Int = 0,
    val hora: Long,
)
