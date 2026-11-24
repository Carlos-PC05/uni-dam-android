# practica-moviles_android
Práctica final android para Desarrollo de Aplicaciones Móviles UCAM

## Configuración de Firebase

Antes de compilar, descarga `google-services.json` desde la configuración del
proyecto Firebase para la app Android `com.example.aplicacion` y colócalo en
`app/google-services.json`. Este archivo se mantiene local y no se sube a Git.

## Acceso privado (ticket 01 / GitHub #9)

La aplicación usa Compose → ViewModel → Repository → Firebase, con Hilt para
inyectar dependencias. `ui/auth` contiene formularios y estado, `data/auth` el
acceso remoto, `di` su configuración y `ui/navigation` las rutas tipadas.

El registro solicita nombre, correo y dos contraseñas idénticas. La contraseña
requiere 12–4096 caracteres, al menos una letra, un dígito y un símbolo de
`-_?#@.`; admite espacios y otros caracteres. Authentication gestiona las
credenciales. Para el perfil, Firestore guarda `usuarios/{uid}.nombreCompleto`
y el correo se obtiene de Authentication. No se guardan contraseñas ni hashes en los perfiles.

Después del alta se mantiene la sesión. Inicio solo se abre tras confirmar el
perfil remoto; si falla ese paso, se puede reintentar sin crear otra cuenta.
Al reabrir, un perfil incompleto vuelve al formulario de finalización. Los
formularios conservan sus datos ante errores; las contraseñas solo viven en memoria
y se descartan al abandonar la pantalla o recrearla. No se ofrece modo offline.

### Preparar Firebase remoto

1. Habilitar **Authentication → Email/Password** en el proyecto correspondiente
   al `google-services.json` local.
2. Revisar la política de contraseñas en Authentication: mínimo 12, dígito y
   carácter no alfanumérico, en modo obligatorio. La app además exige una letra
   y un símbolo de la lista acordada. La consola no permite expresar exactamente
   todas esas condiciones combinadas; no se añade un servidor propio al proyecto.
3. Crear Cloud Firestore y revisar las reglas actualmente desplegadas antes de
   sustituirlas, en especial si el proyecto tiene otras colecciones.
4. Tras iniciar sesión en Firebase CLI con una cuenta autorizada, desplegar desde
   la raíz con `firebase-tests/node_modules/.bin/firebase deploy --only firestore:rules --project <ID_DEL_PROYECTO>`.
   Usa el `project_info.project_id` del `google-services.json` local.
5. Verificar los casos remotos descritos más abajo. La compilación y las pruebas
   locales no demuestran que los proveedores, la política o las reglas remotas
   estén configurados. No usar datos financieros reales en estas comprobaciones.

### Comprobaciones reproducibles

- Compilación: `./gradlew :app:assembleDebug`.
- Validación y estados: `./gradlew :app:testDebugUnitTest`.
- Análisis Android: `./gradlew :app:lintDebug`.
- Formulario Compose en un dispositivo: `./gradlew :app:connectedDebugAndroidTest`.
  La prueba de repositorio remoto se omite salvo que se habilite expresamente.
- Reglas con dos usuarios: `npm --prefix firebase-tests ci` y
  `npm --prefix firebase-tests test`. Requiere Node 22+ y Java 21+ en PATH.
  Se utiliza exclusivamente `demo-vesta` en emuladores, sin credenciales reales.
- Repositorio Android contra emuladores: arrancar un AVD y ejecutar desde la raíz
  `firebase-tests/node_modules/.bin/firebase emulators:exec --project demo-vesta --only auth,firestore './gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.firebaseEmulators=true'`.
  Antes, crear los túneles con `adb reverse tcp:9099 tcp:9099` y
  `adb reverse tcp:8080 tcp:8080`. La prueba usa `127.0.0.1` a través de ADB,
  evitando solicitar acceso a la red local en Android 17. Mantener el dispositivo
  desbloqueado y la pantalla activa. HTTP está habilitado solo en debug.

### Demostración manual con Firebase real

Usar dos cuentas ficticias, A y B, y registrar resultados sin incluir contraseñas.

1. Registrar A, comprobar el acceso directo a Inicio y el nombre/correo en Ajustes.
2. Cerrar y reabrir la app; comprobar que mantiene la sesión. Abrir otro dispositivo,
   iniciar sesión con A y comprobar el mismo perfil.
3. Cerrar sesión, pulsar Atrás y entrar con B: no debe aparecer el perfil de A.
4. Desde un cliente autenticado como B, intentar leer/escribir `usuarios/{uidA}`:
   debe fallar. Comprobar también el acceso anónimo. La prueba local automatiza
   estas denegaciones contra las mismas reglas.
5. Probar registro duplicado, credenciales erróneas, contraseñas distintas y pérdida
   de red. No debe aparecer un falso éxito ni perderse el formulario.
6. Simular una cuenta sin documento de perfil, iniciar sesión y completar el nombre.
   Comprobar que el reintento usa el mismo uid.
7. Revisar login y registro con teclado, rotación, texto grande y ventana ancha:
   los formularios deben poder recorrerse y sus acciones seguir siendo accesibles.

Las pruebas de validación, estado y emuladores son la evidencia automatizada.
La verificación contra el Firebase remoto requiere acceso administrativo y dos
cuentas de prueba; no se da por realizada por el mero hecho de tener el JSON local.

### Resultado de la verificación del ticket #9

El 8 de noviembre de 2026 se desplegaron las reglas del perfil y la política de
contraseñas, y pasaron 19 comprobaciones contra Firebase real con dos cuentas
ficticias, eliminadas al terminar. Véanse las
[evidencias y límites de la verificación](docs/verificacion/acceso-privado.md).

## Saldo inicial

En Inicio, introduce un importe en euros y una fecha en formato
`DD/MM/AAAA`. El saldo representa el efectivo al comienzo de ese día. El
ViewModel convierte el importe a céntimos enteros, comprueba la fecha local y
mantiene el formulario mientras espera la confirmación de Firestore. Inicio
muestra «Consultando el saldo…» durante la lectura, permite reintentar si falla
y abre el formulario cuando el servidor confirma que el saldo aún no existe.

El repositorio lee del servidor y, al guardar, abre una transacción sobre
`usuarios/{uid}/efectivo/saldoInicial`. El documento contiene `centimos` y
`dia` (días desde el 1 de enero de 1970), por lo que la fecha no cambia al
viajar entre husos horarios. La app rechaza fechas posteriores a su día local.
Las reglas usan UTC+14 como límite, porque el servidor desconoce la zona del
dispositivo. Si el documento ya existe, la transacción devuelve
esa referencia. Solo el propietario puede leerla y crearla. Inicio muestra el
efectivo confirmado en euros.

### Demostración del saldo inicial

1. Despliega las reglas de Firestore en el proyecto que indica el
   `google-services.json` local y usa una cuenta de prueba sin saldo.
2. En Inicio prueba `12,345`, `30/02/2024` y una fecha posterior a hoy.
   Comprueba que el formulario señala los campos inválidos.
3. Escribe `-12,50` y `01/01/2024`. Desconecta la red, guarda y comprueba que
   reaparece la opción de guardado con ambos valores. Recupera la conexión y
   guarda. Comprueba que Inicio muestra `-12,50 €` y la fecha inicial.
4. Reinicia la app y comprueba que aparece el mismo saldo directamente. Con
   cuentas de prueba distintas, repite la configuración con `0` y `12,50`.
5. Ejecuta `./gradlew :app:testDebugUnitTest` para comprobar el formulario y
   `npm --prefix firebase-tests test` para verificar la privacidad y unicidad
   con las reglas locales.

## Consultar y corregir movimientos

Movimientos combina el mes y la categoría seleccionados para filtrar la lista.
Inicio usa esa misma lista de movimientos confirmados, incluida la de otros
meses, para calcular el efectivo. El resumen y las barras usan todos los ingresos
y gastos del mes seleccionado.

Al abrir «Editar movimiento», la ruta pasa el identificador al ViewModel. Este
carga el formulario con el importe, la fecha, la categoría y la nota del registro.
Firestore sustituye los datos bajo el mismo identificador o los elimina tras la
confirmación. Cada edición tiene una revisión propia, que permite reconocer un
reintento y detectar cambios de otro dispositivo aunque los valores coincidan.
Al eliminar, la transacción también guarda una marca con el
identificador, sin datos financieros, que impide que un alta pendiente lo reactive.
La interfaz conserva los valores del formulario durante la espera y ante un error.
Si se pierde la confirmación, el reintento envía los mismos cambios.
El efectivo, el resumen y las barras cambian cuando Firestore confirma la operación.

### Demostración de una corrección

1. Usa una cuenta de prueba y un saldo inicial de 1.000 € con fecha anterior al mes
   pasado. Registra un ingreso de 500 € y un gasto de 200 € en Comida, ambos en el
   mes pasado. En Inicio, selecciona ese mes y comprueba efectivo de 1.300 € y
   flujo de caja de +300 €.
2. En Movimientos, selecciona el mes pasado y la categoría Ingreso. Edita el
   resultado y cambia el importe a 100,50 €, la categoría a Vivienda y la fecha a
   este mes. Tras guardar, el filtro de ingresos del mes pasado queda vacío. En el
   mes actual, selecciona Vivienda para encontrar el movimiento corregido.
3. En Inicio, comprueba que el efectivo es 699,50 €. El flujo es −200 € el mes
   pasado y −100,50 € este mes. Las barras muestran los gastos de cada mes.
4. Abre el movimiento de Vivienda, pulsa «Eliminar movimiento» y cancela. Vuelve a
   pulsar «Eliminar movimiento» y confirma. El efectivo pasa a 800 € y las barras
   del mes actual quedan a cero.
5. Durante una edición, prueba `0`, `12,345` y una fecha anterior al saldo. Luego
   introduce valores válidos, desconecta la red y guarda. Tras el error, vuelve a
   conectar y reintenta con el mismo formulario. Ejecuta `npm --prefix
   firebase-tests test` para comprobar también el aislamiento de dos cuentas.

## Integración continua

Los PR hacia `main` ejecutan compilación, pruebas unitarias, Android Lint y
pruebas instrumentadas en API 29. Cuando el PR incluye la suite Firebase,
también ejecutan sus pruebas de reglas e integración contra emuladores locales.
CI utiliza configuración ficticia y no necesita credenciales Firebase reales.

Consulta [la configuración y las protecciones acordadas](docs/ci.md).
