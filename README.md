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
credenciales: Firestore solo guarda `usuarios/{uid}.nombreCompleto`, y el correo
se obtiene de Authentication. No se guardan contraseñas ni hashes en los perfiles.

Después del alta se mantiene la sesión. Inicio solo se abre tras confirmar el
perfil remoto; si falla ese paso, se puede reintentar sin crear otra cuenta.
Al reabrir, un perfil incompleto vuelve al formulario de finalización. Los
formularios conservan sus datos ante errores; las contraseñas solo viven en memoria
y se descartan al abandonar la pantalla o recrearla. No se ofrece modo offline.
Movimientos e Inversiones están identificados como pendientes.

### Preparar Firebase remoto

1. Habilitar **Authentication → Email/Password** en el proyecto correspondiente
   al `google-services.json` local.
2. Revisar la política de contraseñas en Authentication: mínimo 12, dígito y
   carácter no alfanumérico, en modo obligatorio. La app además exige una letra
   y un símbolo de la lista acordada. La consola no permite expresar exactamente
   todas esas condiciones combinadas; no se añade un servidor propio al proyecto.
3. Crear Cloud Firestore. Revisar las reglas actualmente desplegadas antes de
   sustituirlas: las de este ticket solo permiten el perfil y deniegan otras rutas.
4. Tras iniciar sesión en Firebase CLI con una cuenta autorizada, desplegar desde
   la raíz: `firebase-tests/node_modules/.bin/firebase deploy --only firestore:rules --project practica-moviles-android-67eba`.
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

## Recuperación y cierre de sesión (ticket 02 / GitHub #10)

En login, «¿Has olvidado tu contraseña?» abre un diálogo con el correo precargado.
Se puede editar sin cambiar el correo ni la contraseña escritos en login. El envío
muestra carga y bloquea solicitudes simultáneas; un error conserva los datos para
reintentar. Tras la respuesta de Firebase se muestra la confirmación acordada.
El enlace del correo permite elegir la nueva contraseña en la página de Firebase;
después se vuelve a la app para iniciar sesión.

### Cómo seguir el código

1. `ui/auth/AuthScreen.kt` representa el formulario y `PasswordRecoveryDialog`.
   Reciben estado y callbacks: no consultan Firebase. El diálogo se superpone a
   login para conservar su contraseña en memoria.
2. `ui/navigation/VestaApp.kt` conecta los callbacks al `AuthViewModel`. La
   recuperación no añade un destino al historial de navegación.
3. `ui/auth/AuthViewModel.kt` mantiene `PasswordRecoveryState` dentro de
   `AuthUiState`. `openRecovery` precarga el correo; `updateRecoveryEmail` edita
   solo el diálogo; `recoverPassword` valida y espera la respuesta del repositorio;
   `closeRecovery` retira el diálogo. `busy` impide duplicados hasta que el SDK
   termina: cancelar la espera de una corutina no cancela el envío de Firebase.
4. `data/auth/FirebaseAuthRepository.kt` implementa
   `AuthRepository.sendPasswordResetEmail` con el SDK de Authentication. Traduce
   los fallos a mensajes comprensibles y no crea sesiones ni modifica perfiles.
   `ui/auth/AuthValidation.kt` comparte la validación del correo entre formularios.

El cierre sigue siendo local: `signOut` retira las credenciales y reinicia todo el
estado visible. Al desaparecer el perfil se desmonta la navegación privada; su
clave por UID evita compartirla entre cuentas. No se borran los perfiles remotos.
La cancelación de recordatorios se implementará en el ticket 14.

La confirmación de envío no demuestra que el correo exista ni que se haya entregado.
Se mantiene una respuesta uniforme para cuentas inexistentes, incluso si el
proyecto no tiene protección contra enumeración. No se consulta la existencia de
una cuenta antes de solicitar la recuperación.

Las [pruebas y los pasos de comprobación manual](docs/verificacion/recuperacion-sesion.md)
separan la evidencia con emuladores de la demostración con correo real, que Carlos
realizará manualmente.

## Integración continua

Los PR hacia `main` ejecutan compilación, pruebas unitarias, Android Lint y
pruebas instrumentadas en API 29. Cuando el PR incluye la suite Firebase,
también ejecutan sus pruebas de reglas e integración contra emuladores locales.
CI utiliza configuración ficticia y no necesita credenciales Firebase reales.

Consulta [la configuración y las protecciones acordadas](docs/ci.md).
