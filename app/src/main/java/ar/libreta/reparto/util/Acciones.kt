package ar.libreta.reparto.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.util.Locale

object Acciones {
    /** Abre WhatsApp (o el navegador) con el enlace wa.me. El usuario decide si envía. */
    fun abrirEnlace(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No hay una app para abrir el enlace.", Toast.LENGTH_LONG).show()
        }
    }

    fun abrirMapa(context: Context, lat: Double, lng: Double, etiqueta: String) {
        val coords = String.format(Locale.US, "%.6f,%.6f", lat, lng)
        val uri = Uri.parse("geo:$coords?q=$coords(${Uri.encode(etiqueta)})")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            abrirEnlace(context, "https://www.google.com/maps/search/?api=1&query=$coords")
        }
    }

    fun tienePermisoUbicacion(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Pide una ubicación actual al GPS (o a la red). Si tarda más de 25 s, usa la última conocida.
     * [alTerminar] recibe null si no se pudo obtener.
     */
    @SuppressLint("MissingPermission")
    fun ubicacionActual(context: Context, alTerminar: (Location?) -> Unit) {
        if (!tienePermisoUbicacion(context)) {
            alTerminar(null)
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val proveedores = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        fun ultimaConocida(): Location? = listOf(
            LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }

        if (proveedores.isEmpty()) {
            alTerminar(ultimaConocida())
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val cancelar = CancellationSignal()
        var terminado = false
        val timeout = Runnable {
            if (!terminado) {
                terminado = true
                cancelar.cancel()
                alTerminar(ultimaConocida())
            }
        }
        handler.postDelayed(timeout, 25_000)
        LocationManagerCompat.getCurrentLocation(
            lm, proveedores.first(), cancelar, ContextCompat.getMainExecutor(context),
        ) { loc ->
            if (!terminado) {
                terminado = true
                handler.removeCallbacks(timeout)
                alTerminar(loc ?: ultimaConocida())
            }
        }
    }
}
