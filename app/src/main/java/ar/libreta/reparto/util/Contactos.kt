package ar.libreta.reparto.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import ar.libreta.reparto.domain.Telefono

/** Contacto de la agenda del teléfono (la misma que usa WhatsApp). */
data class ContactoTelefono(val nombre: String, val telefono: String) {
    val normalizado: String? get() = Telefono.normalizarAR(telefono)
}

object Contactos {
    /**
     * Lee todos los contactos con número. Requiere READ_CONTACTS.
     * Saca repetidos por número normalizado (un mismo contacto suele tener el número cargado dos veces).
     */
    fun leerTodos(context: Context): List<ContactoTelefono> {
        val proyeccion = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val lista = mutableListOf<ContactoTelefono>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            proyeccion,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val iNombre = cursor.getColumnIndex(proyeccion[0])
            val iNumero = cursor.getColumnIndex(proyeccion[1])
            while (cursor.moveToNext()) {
                val nombre = cursor.getString(iNombre)?.trim().orEmpty()
                val numero = cursor.getString(iNumero)?.trim().orEmpty()
                if (numero.isNotEmpty()) lista += ContactoTelefono(nombre.ifBlank { numero }, numero)
            }
        }
        return lista.distinctBy { it.normalizado ?: it.telefono.filter(Char::isDigit) }
    }

    /**
     * Lee el contacto elegido con el selector del sistema (ACTION_PICK sobre teléfonos).
     * El sistema da acceso temporal a esa fila, así que no hace falta READ_CONTACTS.
     */
    fun leerElegido(context: Context, uri: Uri): ContactoTelefono? {
        val proyeccion = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        return context.contentResolver.query(uri, proyeccion, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val nombre = c.getString(c.getColumnIndex(proyeccion[0]))?.trim().orEmpty()
            val numero = c.getString(c.getColumnIndex(proyeccion[1]))?.trim().orEmpty()
            if (numero.isEmpty()) null else ContactoTelefono(nombre.ifBlank { numero }, numero)
        }
    }
}
