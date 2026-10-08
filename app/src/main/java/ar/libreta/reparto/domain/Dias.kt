package ar.libreta.reparto.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Dias {
    val nombres = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    val cortos = listOf("Lu", "Ma", "Mi", "Ju", "Vi", "Sá", "Do")

    fun bit(dia: DayOfWeek): Int = 1 shl (dia.value - 1)

    fun visitaEl(mascara: Int, dia: DayOfWeek): Boolean = mascara and bit(dia) != 0

    fun alternar(mascara: Int, indice: Int): Int = mascara xor (1 shl indice)

    fun resumen(mascara: Int): String {
        if (mascara == 0) return "Sin días de visita"
        if (mascara == 0b1111111) return "Todos los días"
        return cortos.filterIndexed { i, _ -> mascara and (1 shl i) != 0 }.joinToString(" ")
    }

    private val locale = Locale("es", "AR")

    fun claveDia(fecha: LocalDate): String = fecha.format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun titulo(fecha: LocalDate): String {
        val dia = fecha.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val mes = fecha.month.getDisplayName(TextStyle.FULL, locale)
        return "${dia.replaceFirstChar { it.uppercase() }} ${fecha.dayOfMonth} de $mes"
    }
}
