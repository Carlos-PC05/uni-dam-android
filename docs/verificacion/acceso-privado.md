# Verificación del acceso privado — issue #9

Fecha: 8 de noviembre de 2026. Rama de implementación: `feat/09-acceso-privado`.

## Configuración remota

Proyecto: `practica-moviles-android-67eba`.

- Proveedor de correo y contraseña habilitado en Authentication.
- Firestore Native disponible en `eur3`.
- Reglas de `firestore.rules` desplegadas y contrastadas con la versión remota.
  Solo el propietario puede leer o escribir su perfil; las demás rutas quedan denegadas.
- Política remota obligatoria: 12–4096 caracteres, número y carácter no alfanumérico.
  No se fuerza un cambio de contraseña a las cuentas existentes al iniciar sesión.
- La aplicación añade coincidencia exacta de confirmación, una letra y un símbolo
  de `-_?#@.`. Firebase no permite expresar exactamente esa combinación de reglas
  de composición sin distinguir mayúsculas de minúsculas.

## Evidencia automatizada

- 20 pruebas unitarias aprobadas (19 del acceso y una de plantilla).
- Dos pruebas de reglas aprobadas con usuarios distintos y acceso anónimo en emuladores.
- Cinco pruebas Android aprobadas: contexto, repositorio con SDK reales y tres de formularios.
- Cuatro pruebas Compose aprobadas en ventana amplia, incluyendo navegación por las
  cuatro secciones y retirada del perfil al cerrar sesión.
- APK debug generado y lint sin errores. Permanecen advertencias informativas de
  versiones y recursos de la plantilla; no se amplió el alcance para actualizarlos.
- Revisión de estándares y especificación realizada; se corrigió una carrera que
  permitía reenviar Authentication mientras una operación lenta seguía activa.

## Comprobación contra Firebase real

Se ejecutaron 19 comprobaciones mediante las API públicas de Authentication y
Firestore, usando credenciales de usuarios de prueba, sin privilegios administrativos
para las lecturas y escrituras cuya autorización se verificaba:

1. Rechazar contraseña de menos de 12 caracteres.
2. Rechazar contraseña sin número.
3. Rechazar contraseña sin símbolo.
4. Crear dos cuentas distintas con sus respectivas sesiones.
5. Distinguir una cuenta sin documento de perfil.
6. Guardar el perfil de A con la sesión de A.
7. Guardar el perfil de B con la sesión de B.
8. Recuperar el nombre de A desde el servidor.
9. Denegar que B lea el perfil de A.
10. Denegar que B modifique el perfil de A.
11. Denegar lectura anónima del perfil de A.
12. Denegar escritura anónima del perfil de A.
13. Rechazar un campo de contraseña enviado al perfil.
14. Rechazar un nombre vacío.
15. Rechazar el registro de un correo duplicado.
16. Rechazar credenciales incorrectas sin conceder sesión.
17. Autenticar de nuevo A y recuperar el mismo uid.
18. Recuperar el perfil persistido mediante una petición de un cliente independiente.
19. Reintentar el guardado utilizando el mismo documento de perfil.

Las dos cuentas ficticias y sus documentos se eliminaron al terminar. No se leyeron
perfiles de terceros ni se conservaron tokens o contraseñas en este registro.

## Alcance de la evidencia

La prueba real demuestra persistencia y aislamiento en el servicio remoto. La
recuperación se comprobó con una sesión y petición independientes, no mediante
una demostración simultánea en dos teléfonos físicos. La interfaz y el repositorio
Android se comprobaron además contra emuladores y en tamaños de teléfono y ventana
amplia. Las instrucciones para repetir las pruebas están en el [README](../../README.md).
