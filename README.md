# VozRuta Android

**Asistente de transporte inclusivo con un piloto demostrable en Huánuco–Ambo, Perú.** Usa Kotlin, Jetpack Compose, Material 3, ubicación de Android, Google Maps y una escena WebGL local. Requiere Android 8.0 (API 26) o superior.

## Ejecutar en Android Studio

1. Abre la carpeta **VozRuta** en Android Studio y deja terminar la sincronización de Gradle. El proyecto usa el SDK 36, Android Gradle Plugin 9.1.1 y el wrapper de Gradle 9.3.1. Usa JDK 17 o el JDK integrado de Android Studio.
2. Selecciona la configuración **app** y un dispositivo Android o un emulador con **Google APIs**. En este equipo se usó `Jarvis_QA_API35`.
3. Pulsa **Run ▶**. Para compilar desde PowerShell, ejecuta `.\gradlew.bat :app:assembleDebug` en la raíz del proyecto.

El piloto de demostración se puede recorrer sin cuentas de Google Cloud. El reconocimiento y la síntesis de voz dependen de los servicios y las voces instalados en el dispositivo. En modo avión siguen disponibles los comandos escritos, la selección por toque, los datos locales y la simulación.

## Probar la app

1. En **Accesibilidad**, elige guía auditiva o silla de ruedas. Los avisos hablados se pueden activar y desactivar.
2. Pulsa **Hablar con VozRuta** o **Escribir comando** y prueba «Ir a UNHEVAL», «Ir a Amarilis», «Ir al Hospital» o «Paradero cercano». **Ver todos** muestra los nueve destinos de referencia. También puedes cambiar el punto de subida.
3. En **Viaje**, escucha el itinerario, consulta un precio de ejemplo y pulsa **Solicitar abordaje inclusivo**. Confirma la solicitud local para verla en **Central**. Si habilitas el servidor de pruebas, la petición se envía a la bandeja conectada.
4. En **Bus 3D**, usa **Simular llegada y rampa**, cambia la cámara o pausa la escena. El perfil cambia la animación y el mensaje de asistencia. El punto del autobús en el mapa sigue la misma simulación.
5. En **Explorar otras ciudades**, escribe direcciones con ciudad y país, o usa el GPS como origen. La app abre Google Maps si se ejecuta sin el servidor. Con el servidor y Google Routes habilitados, muestra los itinerarios, horarios, distancias y tarifas que proporcione la fuente dentro de VozRuta.

Cuando se permite el GPS, la app solicita una posición puntual en primer plano. El «paradero cercano» busca referencias del piloto en un radio de 3 km. Con Google Routes habilitado, también puede mostrar indicaciones peatonales paso a paso. El resultado actualmente carece de un inventario de accesibilidad verificado en campo.

## Activar el mapa integrado

Agrega esta línea al archivo **`local.properties` de la raíz** (conserva la línea `sdk.dir` si Android Studio ya la creó):

```properties
MAPS_API_KEY=pega_aquí_tu_clave
```

Habilita **Maps SDK for Android** y la facturación en Google Cloud. Restringe la clave a ese SDK, al paquete `pe.vozruta.app` y al SHA-1 del certificado con que firmes el APK. Obtén el SHA-1 de depuración con `.\gradlew.bat :app:signingReport`. Reconstruye la app cada vez que cambies las propiedades. [Configuración oficial de Maps SDK](https://developers.google.com/maps/documentation/android-sdk/get-api-key)

Sin clave, el piloto muestra un esquema del trazado con OpenStreetMap. Para una búsqueda global devuelta por el servidor, la app muestra el itinerario como lista y reserva la polilínea de Google para el mapa de Google.

## Activar la central y la búsqueda global

El servidor de pruebas necesita Python 3.10 o posterior. Persiste solicitudes en SQLite. En el emulador Android, `10.0.2.2` corresponde al equipo anfitrión.

**Terminal de PowerShell en la raíz del proyecto**

```powershell
$env:RIDER_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
$env:OPERATOR_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
# Quita el comentario y pega la clave solo si vas a consultar Google Routes.
# $env:ROUTES_API_KEY = 'clave_restringida_para_el_servidor'
python server/service.py
```

Configura esos mismos valores en `local.properties` antes de compilar. Puedes consultar las variables `$env:RIDER_TOKEN` y `$env:OPERATOR_TOKEN` en la terminal donde las generaste. Comparte el token de operador solo en la compilación privada de la central de pruebas.

```properties
SERVICE_URL=http://10.0.2.2:8710
RIDER_TOKEN=valor_del_token_de_pasajero
OPERATOR_TOKEN=valor_del_token_de_central
```

Ejecuta una compilación de depuración y abre **Central**. Envía un abordaje desde **Viaje**. Confirma cada etapa en la bandeja y vuelve a **Viaje** para ver el estado. La app sincroniza cada 2,5 s mientras la pantalla correspondiente está activa. En un teléfono físico usa un servidor accesible por HTTPS o `adb reverse tcp:8710 tcp:8710` y `http://127.0.0.1:8710` con una conexión USB de depuración. En ambos casos, los tokens son credenciales privadas de prueba y se incluyen en la compilación configurada.

La clave `ROUTES_API_KEY` permanece en el servidor. Habilita **Routes API**, restringe esa clave a la API y a la IP pública del servidor donde aplique, y configura las cuotas del proyecto en Google Cloud. [Routes API para transporte público](https://developers.google.com/maps/documentation/routes/transit-route) · [Restricciones de las claves](https://developers.google.com/maps/api-security-best-practices)

## Alcance del piloto

- Los nombres y coordenadas ubican referencias geográficas. Hospital, Yanag y Conchamarca usan referencias del sector que deben revisarse con el equipo local. Un marcador por sí solo no confirma que se pueda subir o cruzar en ese punto.
- La geometría ilustrativa sigue la red vial de OpenStreetMap y se calculó con OSRM. Los horarios, costos, movimientos de la unidad y avisos del bus corresponden a la demostración. La central conectada sí intercambia solicitudes y confirmaciones de prueba. Se necesita integrar a un operador para reservar transporte y recibir GPS de flota reales.
- El servicio de pruebas usa tokens de grupo y retiene las solicitudes en la base SQLite local. Para publicar, se requieren cuentas y permisos por operador y usuario, caducidad y eliminación de datos, un acuerdo con el operador y pruebas de campo con usuarios de TalkBack y personas con distintas necesidades de movilidad.
- El modelo WebGL explica la secuencia de abordaje. El prototipo de vehículo todavía requiere diseño mecánico, evaluación de riesgos, ensayos y homologación antes de transportar a personas.

[Escuelas y mercados de referencia de Huánuco en OpenStreetMap](https://www.openstreetmap.org/way/237415409) · [Mercado Viejo](https://www.openstreetmap.org/way/577600083) · [Mercado Modelo](https://www.openstreetmap.org/way/237321453) · [Mercado de Ambo](https://www.openstreetmap.org/way/464946088) · [Yanag](https://www.openstreetmap.org/node/7060867548) · [Conchamarca](https://www.openstreetmap.org/node/7060202998) · [Mapa vial del MTC](https://portal.mtc.gob.pe/transportes/caminos/normas_carreteras/Mapas%20Distritales/Huanuco/HU_100204%20CONCHAMARCA.pdf) · [Rosavero](https://www.deperu.com/centros-poblados/rosavero-59162)

**Licencias de terceros.** [© OpenStreetMap contributors](https://www.openstreetmap.org/copyright). El motor three.js r160 está incluido bajo licencia MIT en `app/src/main/assets/three-LICENSE`.

[Engineering design and feasibility brief](docs/WFEO-design-and-feasibility.md)
