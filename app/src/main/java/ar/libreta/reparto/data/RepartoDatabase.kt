package ar.libreta.reparto.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Cliente::class, Movimiento::class, Visita::class],
    version = 1,
    exportSchema = false,
)
abstract class RepartoDatabase : RoomDatabase() {
    abstract fun dao(): RepartoDao

    companion object {
        @Volatile
        private var instancia: RepartoDatabase? = null

        fun obtener(context: Context): RepartoDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    RepartoDatabase::class.java,
                    "libreta-reparto.db",
                ).build().also { instancia = it }
            }
    }
}
