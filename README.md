# Libreta Reparto

App Android nativa para un reparto de bidones de agua en Argentina. Pensada para el repartidor monotributista que atiende por WhatsApp y anota fiado: la hoja de ruta del día, los saldos y los clientes quedan guardados en el teléfono, así no perdés el hilo.

> **No está publicada en Play Store.** Se instala como APK de prueba (debug) que genera GitHub Actions.

- Kotlin + Jetpack Compose (Material 3) + Room. minSdk 26 (Android 8.0 o más nuevo).
- Sin login. Sin servidor. Todos los datos quedan en el teléfono.
- La app no usa ningún modelo de lenguaje. Dolphin (ver abajo) es un agregado opcional que viene apagado.

## Pantallas

Una sola pantalla con navegación inferior: **Hoy**, **Saldos**, **Clientes** y **Ajustes**.

### Clientes

El cliente final nunca carga nada: todos los datos los cargás vos, el repartidor.

- **Agregar cliente** (botón flotante, abajo a la derecha, y botón arriba de la lista): formulario completo a mano.
- **Importar de contactos**: pide permiso de contactos y muestra la agenda del teléfono (la misma que usa WhatsApp) con buscador y selección múltiple (nombre + número). Crea un cliente por cada contacto que marcás. Si el número ya es de un cliente (se compara normalizado, por ejemplo `011 15 2345-6789` = `+54 9 11 2345-6789`), lo saltea.
- **Elegir un contacto**: abre el selector de contactos del sistema para traer uno solo. No necesita el permiso de contactos.
- Después de importar, completás **dirección, zona, días de visita, cantidad habitual y bidones en domicilio**. Los clientes sin dirección o sin días aparecen marcados con "Falta completar".
- Cada cliente tiene: nombre, teléfono, dirección, zona, bidones en domicilio, cantidad habitual, días de visita (lunes a domingo) y un pin opcional (latitud y longitud).
- **Pin**: lo cargás vos pegando un enlace de Google Maps o coordenadas. Se aceptan `-34.6037, -58.3816`, enlaces con `@lat,lng`, `?q=`, `ll=`, `query=`, `!3d…!4d…` y `geo:`. Los enlaces cortos (`maps.app.goo.gl`, `goo.gl/maps`) no se pueden abrir sin internet: la app te pide que pegues las coordenadas o el enlace completo.

### Hoy

- Arma la hoja de ruta **solo con los clientes que visitás ese día de la semana**.
- **Guardar ubicación actual como salida**: toma tu ubicación (pide permiso) y la usa como punto de partida.
- **Agregar recarga**: pone la planta de recarga primera en la ruta hasta que tocás **Listo**. La recarga no tiene calendario. Los datos de la planta (nombre, dirección, teléfono y pin) se cargan una vez en Ajustes.
- **Orden de la ruta** (en línea recta, sin tránsito real):
  1. La planta de recarga, si está activa.
  2. Clientes con pin: del más cercano al siguiente (vecino más cercano), arrancando desde la planta (si está activa y tiene pin) o desde la salida guardada. Si no hay punto de partida, arranca por el primer cliente con pin por orden alfabético.
  3. Clientes sin pin al final, agrupados por zona (los sin zona al último) y por nombre.
- En cada parada:
  - **WhatsApp**: abre `wa.me` con un texto fijo (saludo, dirección, cantidad habitual, saldo y bidones en domicilio). **El mensaje no se envía solo**: lo mandás vos desde WhatsApp.
  - **Pegar pin / Cambiar pin**: pegás el enlace de Maps o las coordenadas.
  - **Pedir pin** (opcional, secundario): abre `wa.me` con un texto que le pide al cliente que mande su ubicación. Cuando te la manda, la pegás vos con "Pegar pin".
  - **Mapa**: abre el pin en la app de mapas.
  - **Entregué**: cargás llenos entregados, vacíos retirados, cómo pagó (efectivo, QR o a cuenta) y el monto (cantidad × precio, editable). Los bidones en domicilio se actualizan: antes − vacíos + llenos. Si es a cuenta, se anota el movimiento en la libreta (respetando los topes).
  - **No estaba**.
  - **Cobrar**: si tiene saldo, anotás un cobro en efectivo o QR.
  - **Deshacer**: borra lo marcado hoy (y anula la entrega a cuenta que generó, reponiendo los bidones).
- Los estados "Entregué" y "No estaba" son **del día**: al día siguiente la hoja arranca limpia.
- Los teléfonos se normalizan para WhatsApp: si no tienen código de país se les agrega `549` (se sacan el 0 y el 15).

### Saldos

- Total fiado y lista de clientes con saldo (o todos, si querés).
- Detalle por cliente con todos los movimientos, **Registrar cobro** (efectivo o QR) y **Anotar a cuenta** (para entregas fuera de la ruta).
- **El saldo no se edita a mano**: es siempre la suma de movimientos. Entrega a cuenta suma; cobro en efectivo o QR resta. Si cargaste algo por error, podés **Anular** ese movimiento.

### Topes del fiado (Ajustes)

- **Tope por monto**: saldo máximo a cuenta por cliente.
- **Tope de entregas abiertas**: cantidad máxima de entregas a cuenta sin cubrir. Los cobros se aplican a las entregas más viejas primero; cada entrega a cuenta que no quedó cubierta del todo cuenta como abierta. Ejemplo: tres entregas de $ 3.000 y un cobro de $ 4.000 → 2 entregas abiertas.
- Un tope en 0 significa sin tope. Si anotar a cuenta supera cualquiera de los dos, la app **no deja anotar** y te avisa por qué (podés cobrar en efectivo o QR).
- También se configura el **precio por bidón** y el nombre del reparto para los mensajes.

### Exportar e importar

En Ajustes → Datos: **Exportar JSON** guarda clientes, movimientos, visitas y ajustes en un archivo que elegís vos. **Importar JSON** reemplaza todos los datos del teléfono por los del archivo (te pide confirmación). La clave de Dolphin nunca se exporta.

## Dolphin (opcional, apagado)

En Ajustes hay una sección aparte, **Dolphin**, con interruptor (apagado por defecto), URL base, modelo y clave de un servidor compatible con la API de OpenAI (`POST {URL}/chat/completions`). Está en un paquete separado (`classifier/`) con una interfaz (`ClasificadorDeTexto`) y una implementación (`ClasificadorOpenAICompatible`).

Si lo prendés, **solo clasifica un texto pegado** y te muestra un JSON:

```json
{ "intent": "pedido", "items": [{ "producto": "bidón 20 L", "cantidad": 2 }], "payMode": "a_cuenta" }
```

Dolphin **no confirma pedidos, no mueve saldos y no escribe por WhatsApp**. No tiene acceso a la base de datos. La clave queda en las preferencias privadas de la app y se excluye de los backups. Con Dolphin apagado, la app no se conecta a internet.

## Descargar e instalar el APK

1. Entrá a la pestaña **Actions** del repo y abrí la última ejecución exitosa de **Build debug APK**.
2. Abajo, en **Artifacts**, descargá **libreta-reparto-debug-apk** (necesitás estar logueado en GitHub con acceso al repo). Es un `.zip` con `app-debug.apk` adentro.
3. Pasá el `.apk` al teléfono y abrilo. Android te va a pedir que permitas **instalar apps de origen desconocido** para el navegador o el administrador de archivos que usaste: activalo para esa app.
4. Instalá. Es un APK debug, firmado con la clave de depuración: no hay keystore de release ni cuenta de Play Store. Si después instalás un APK firmado distinto, primero desinstalá este (y exportá los datos antes).

Los artifacts de GitHub Actions vencen a los 90 días; para generar uno nuevo, entrá a Actions → Build debug APK → **Run workflow**.

## Compilar vos

```bash
./gradlew testDebugUnitTest   # pruebas de reglas de negocio
./gradlew assembleDebug        # APK en app/build/outputs/apk/debug/
```

Necesitás JDK 17 y el Android SDK (plataforma 34). El workflow está en `.github/workflows/build-apk.yml`.

## Estructura

- `data/`: entidades Room (`Cliente`, `Movimiento`, `Visita`), DAO, ajustes y respaldo JSON.
- `domain/`: reglas puras (libreta y topes, orden de ruta, pins, teléfonos, mensajes, montos).
- `classifier/`: Dolphin (opcional).
- `ui/`: pantallas Compose.
- `util/`: intents (WhatsApp, mapas), ubicación y contactos.
