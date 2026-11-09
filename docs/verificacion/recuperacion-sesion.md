# Verificación de recuperación y sesión — issue #10

Fecha: 9 de noviembre de 2026. Rama: `feat/02-recuperacion-sesion`.

## Comportamiento implementado

- El enlace de login abre un diálogo Material 3 con el correo precargado y editable.
  Los datos del diálogo son independientes del correo y la contraseña del login.
- El correo se valida sin pedir contraseña. Durante el envío se impiden envíos
  simultáneos, la edición y el cierre del diálogo. Se espera al SDK, sin liberar
  un reintento por un timeout local que no cancelaría la solicitud de Firebase.
- Un fallo conserva los datos y permite reintentar. La confirmación tras el
  reintento retira el error anterior; editar el correo retira el resultado previo.
- Se muestra el texto acordado: «Te hemos enviado un correo electrónico para que
  restaures tu contraseña». La respuesta no revela si la cuenta existe.
- La nueva contraseña se establece en la página de Firebase enlazada desde el correo.
  Solicitar recuperación no inicia sesión ni modifica el perfil de Firestore.
- Cerrar sesión limpia el estado visible y desmonta los destinos privados.
  Atrás no recupera la navegación anterior y la cuenta B no hereda el perfil de A.
  El cierre es local y no necesita red. No se borran perfiles remotos.
- La cancelación de recordatorios sigue perteneciendo al ticket 14.

La [guía de lectura del código](../../README.md#cómo-seguir-el-código) explica
las responsabilidades de cada archivo y cómo recorrer el flujo para la defensa.

## Evidencia automatizada

- Compilación debug y APK de pruebas: aprobadas.
- 27 pruebas unitarias: 8 de validación, 18 de estado y una de plantilla; cero fallos.
- Android Lint: sin errores. No se amplió el alcance a advertencias previas de
  versiones o recursos de la plantilla.
- Dos pruebas Node de reglas aprobadas con acceso propio, cruzado y anónimo.
- Suite Android completa: nueve pruebas aprobadas en Android 10 / API 29,
  incluyendo la integración real de los SDK con Authentication/Firestore emulados.
  Resultado del runner: `OK (9 tests)`, sin omisiones.
- Ventana amplia: siete pruebas Compose aprobadas en la misma instalación
  emulada, con resolución 1600 × 1200, densidad 160, texto al 130 % y modo
  oscuro del sistema activado. Resultado: `OK (7 tests)`. Capturas de carga,
  error y éxito revisadas en teléfono y ventana amplia, conservadas localmente.
- Revisión independiente de estándares y cumplimiento del ticket: cero hallazgos
  en ambos ejes. Comparación desde `b435209fd71d800bcec33142c80e1f900b5c924a`.

Las pruebas de ViewModel cubren validación sin envío para correo inválido, carga,
éxito, error de red y reintento, envío lento sin duplicados, conservación de ambos
formularios, limpieza de un resultado anterior y protección de una sesión ya abierta.

Las pruebas Compose utilizan el ViewModel real y un proveedor controlado. Comprueban
éxito, error, reintento y carga del diálogo, conservación de contraseña y correo del
login y el recorrido A → cierre → Atrás → B. Para probar Atrás sin finalizar la
actividad de pruebas, un observador instalado antes de la navegación comprueba que
la pulsación llega a la raíz: ningún destino privado anterior debe interceptarla.

La prueba del repositorio usa los SDK Android reales contra `demo-vesta` local.
Solicita recuperación, obtiene el código ficticio del emulador, establece otra
contraseña y verifica que la anterior se rechaza, la nueva permite entrar y el
perfil sigue siendo el mismo. También comprueba que solicitar recuperación para
un correo inexistente no abre sesión y que B no puede leer el perfil de A.

## Repetir las comprobaciones

Usar el JDK compatible con el proyecto (Java 21 o superior), el SDK Android y
`app/google-services.json` local según el README. Las pruebas emuladas usan
exclusivamente `demo-vesta`; no requieren usuarios ni credenciales de producción.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
npm --prefix firebase-tests test
adb reverse tcp:9099 tcp:9099
adb reverse tcp:8080 tcp:8080
firebase-tests/node_modules/.bin/firebase emulators:exec --project demo-vesta --only auth,firestore './gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.firebaseEmulators=true'
```

Para ejecutar solo una frontera durante el desarrollo:

```sh
./gradlew :app:testDebugUnitTest --tests '*AuthViewModelTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.aplicacion.ui.auth.AuthScreenTest
```

En esta sesión, tras generar ambos APK, la suite Android se ejecutó también
mediante el runner directamente para separar la memoria de compilación de la
usada por el emulador:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e firebaseEmulators true com.example.aplicacion.test/androidx.test.runner.AndroidJUnitRunner
```

Este último comando requiere Authentication y Firestore emulados ya disponibles,
los túneles ADB activos y Android completamente arrancado y desbloqueado.
Contar únicamente una salida `OK` con todas las pruebas terminadas. Una ejecución
sin `firebaseEmulators=true` omite la integración Firebase y no demuestra ese flujo.

## Demostración remota pendiente — Carlos

El agente no ha enviado correos reales, cambiado contraseñas reales, desplegado
reglas ni ejecutado la demostración remota. Usar cuentas de prueba A y B y un
buzón accesible; registrar los resultados sin contraseñas, tokens ni enlaces activos.

1. En login, escribir el correo de A y abrir recuperación: debe aparecer precargado.
   Editarlo y cancelar debe conservar el correo y la contraseña de login.
2. Solicitar recuperación para A y comprobar carga, confirmación y recepción real.
3. Abrir el enlace, elegir una contraseña nueva en Firebase y volver a la app.
   La contraseña anterior debe fallar y la nueva debe recuperar el mismo perfil.
4. Cerrar sesión desde Ajustes y pulsar Atrás: no reaparecen datos privados.
5. Entrar con B: no aparecen el perfil ni formularios de A.
6. Entrar con A desde otro dispositivo o instalación y comprobar su mismo perfil.
7. Probar pérdida de conexión durante recuperación: mostrar error y permitir
   reintento conservando datos. Con una sesión abierta, cortar la conexión y
   cerrarla: los datos privados deben retirarse igualmente.
8. Revisar con teclado, rotación, texto grande y ventana amplia.

## Límites de la evidencia

La confirmación de Firebase no demuestra entrega del correo ni existencia de
cuenta. La página web y la entrega de correo real quedan en la demostración manual.
La integración emulada cambia la contraseña con el SDK para verificar el servicio;
no sustituye la interacción con el enlace recibido en un buzón real.

El emulador de Authentication puede tratar correos mal formados como cuentas
inexistentes. Por ello la validación de formato se verifica en la frontera pública
de validación y en el estado, antes del envío, sin atribuir al emulador una validación
que no realiza.

La primera ejecución Android perdió el dispositivo por caída del emulador y no se
contó como éxito. Se corrigieron además las aserciones de Atrás y campos vacíos:
Android puede finalizar la actividad al llegar a la raíz y el placeholder no es
el contenido editable del campo. Las evidencias válidas son las ejecuciones
completas posteriores.

La demostración de otro dispositivo y el cierre definitivo del ticket dependen
de la comprobación manual pendiente de Carlos.
