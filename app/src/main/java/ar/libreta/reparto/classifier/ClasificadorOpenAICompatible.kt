package ar.libreta.reparto.classifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Implementación que habla con cualquier servidor compatible con la API de OpenAI
 * (POST {baseUrl}/chat/completions). Solo se crea cuando Dolphin está activado en Ajustes.
 */
class ClasificadorOpenAICompatible(
    private val baseUrl: String,
    private val modelo: String,
    private val clave: String,
) : ClasificadorDeTexto {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    override suspend fun clasificar(texto: String): Clasificacion = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) throw ErrorDeClasificador("Falta la URL del servidor.")
        if (modelo.isBlank()) throw ErrorDeClasificador("Falta el modelo.")
        val url = endpoint(baseUrl)

        val cuerpo = buildJsonObject {
            put("model", modelo)
            put("temperature", 0)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", PROMPT_SISTEMA)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", texto)
                })
            })
        }.toString()

        val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (clave.isNotBlank()) setRequestProperty("Authorization", "Bearer $clave")
        }
        try {
            conexion.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            val codigo = conexion.responseCode
            val respuesta = (if (codigo in 200..299) conexion.inputStream else conexion.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (codigo !in 200..299) {
                throw ErrorDeClasificador("El servidor respondió $codigo: ${respuesta.take(300)}")
            }
            interpretarRespuesta(respuesta)
        } catch (e: IOException) {
            throw ErrorDeClasificador("No se pudo conectar: ${e.message}", e)
        } finally {
            conexion.disconnect()
        }
    }

    internal fun interpretarRespuesta(respuesta: String): Clasificacion {
        val contenido = try {
            json.parseToJsonElement(respuesta).jsonObject["choices"]!!.jsonArray[0]
                .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        } catch (e: Exception) {
            throw ErrorDeClasificador("La respuesta no tiene el formato de chat/completions.", e)
        }
        return interpretarContenido(contenido, json)
    }

    companion object {
        fun endpoint(base: String): String {
            val b = base.trim().trimEnd('/')
            return if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
        }

        fun interpretarContenido(contenido: String, json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }): Clasificacion {
            val inicio = contenido.indexOf('{')
            val fin = contenido.lastIndexOf('}')
            if (inicio < 0 || fin <= inicio) throw ErrorDeClasificador("El modelo no devolvió JSON.")
            val objeto = try {
                json.parseToJsonElement(contenido.substring(inicio, fin + 1)) as JsonObject
            } catch (e: Exception) {
                throw ErrorDeClasificador("El JSON devuelto no es válido.", e)
            }
            val crudo = try {
                json.decodeFromJsonElement(Clasificacion.serializer(), objeto)
            } catch (e: Exception) {
                throw ErrorDeClasificador("El JSON no tiene intent, items y payMode válidos.", e)
            }
            return Clasificacion.normalizada(crudo)
        }

        const val PROMPT_SISTEMA = """Sos un clasificador de mensajes para un reparto de bidones de agua en Argentina.
Respondé ÚNICAMENTE con un objeto JSON, sin texto extra, con esta forma exacta:
{"intent": "pedido|pago|consulta|reclamo|otro", "items": [{"producto": "bidón 20 L", "cantidad": 2}], "payMode": "efectivo|qr|a_cuenta|null"}
- intent: qué quiere el cliente.
- items: productos y cantidades mencionados (lista vacía si no hay).
- payMode: forma de pago mencionada, o null si no se menciona. "fiado" o "anotalo" es a_cuenta; "transferencia" o "Mercado Pago" es qr.
No inventes datos."""
    }
}
