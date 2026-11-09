# Integración continua

Acuerdo confirmado con Carlos el 9 de noviembre de 2026. El workflow está en
[ci.yml](../.github/workflows/ci.yml).

## Ejecución

- GitHub Actions exclusivamente al abrir, reabrir o actualizar PR hacia `main`,
  incluido el cambio de destino de un PR existente a esa rama.
- `develop` es la rama personal de integración de Carlos y se trata como otra
  rama de trabajo frente a `main`. Sus PR no ejecutan CI.
- Un único trabajo, `Verificaciones`, con límite de 30 minutos. Una nueva
  ejecución cancela la anterior del mismo PR.
- Compilación debug, pruebas unitarias y Android Lint. Los errores de Lint
  bloquean; las advertencias no.
- Todas las pruebas instrumentadas en un emulador Android 10, API 29 x86_64,
  correspondiente al mínimo soportado. No se garantiza cobertura de otras API.
- Pruebas de reglas Firebase e integración Android con Auth/Firestore locales
  cuando la revisión incluye `firebase-tests/package.json`. En `main` todavía
  no existe esa funcionalidad: su ausencia se indica en el resumen y no se
  importan pruebas o código de otras ramas.
- Solo logs y resumen de resultados en GitHub; sin APK ni informes descargables.

Se reutilizan las tareas Gradle existentes con JDK 25, conforme a los criterios
del daemon versionados. La compatibilidad del código Java sigue siendo 11.
Las acciones externas están fijadas por SHA para evitar cambios implícitos.
El script de conexión Android/Firebase tiene una autocomprobación sin SDK:
`python3 -B -m unittest discover -s .github/ci -p 'test_*.py' -v`.
CI también la ejecuta para comprobar ambas rutas y la propagación de errores.

## Firebase y coste

CI copia [.github/ci/google-services.json](../.github/ci/google-services.json)
a la ruta local ignorada `app/google-services.json`. Es una configuración
ficticia para `demo-vesta`, sin credenciales de un proyecto real. La suite
Firebase utiliza Auth y Firestore en localhost; el script Android redirige los
puertos 9099 y 8080 desde el dispositivo y activa el argumento
`firebaseEmulators=true` para no omitir la prueba del repositorio.

El repositorio es público y utiliza un ejecutor estándar `ubuntu-24.04`, cuyo
cómputo es gratuito. No se publican artefactos ni se configura caché remota.
No se usan ejecutores de pago, Firebase remoto ni servicios de distribución.
Si se cambia la visibilidad del repositorio, se debe revisar el presupuesto
antes de ejecutar CI.

Referencias: [coste de GitHub Actions](https://docs.github.com/en/actions/concepts/billing-and-usage)
y [proyectos demo Firebase](https://firebase.google.com/docs/emulator-suite/connect_auth#choose_a_firebase_project).

## Protecciones que deben configurarse en GitHub

El workflow produce el check `Verificaciones` del workflow `CI`. Para que CI
bloquee el merge, hay que seleccionarlo como check obligatorio en las reglas
de `main` tras su primera ejecución; añadir el YAML por sí solo no lo exige.

Separar las reglas en dos rulesets activos dirigidos a `refs/heads/main`:

1. **Integración y CI, sin bypass:** exigir PR y el check `Verificaciones`
   emitido por GitHub Actions. Mantener el bloqueo de borrado y force-push.
   No exigir aprobaciones en este conjunto.
2. **Revisión, con bypass solo mediante PR para Carlos:** exigir una aprobación,
   revisión de CODEOWNER y aprobación del último push. El CODEOWNER actual es
   `@Carlos-PC05`. Carlos puede usar bypass para integrar sus PR sin otro revisor;
   los demás necesitan su aprobación. No incluir el check CI en este conjunto.

El bypass se asigna a los actores admitidos por GitHub: en este repositorio
personal, usar el rol de administrador siempre que Carlos sea su único miembro.
Si otros usuarios reciben ese rol, revisar el alcance del bypass. La aprobación
del último push debe proceder de otra persona; Carlos emplea el bypass cuando
es autor del PR o del último push.

Conservar la regla de calidad de código existente sin introducir nuevos
requisitos. Al dividir el ruleset actual, retirar de él las aprobaciones que
ya estén en el conjunto de revisión: las reglas de varios conjuntos se acumulan.
No dejar un bypass que también permita omitir CI.

La cuenta CLI disponible durante la preparación es `cparra05`, con acceso de
lectura; no es la cuenta CODEOWNER y no puede administrar estas protecciones.
La configuración remota requiere una sesión autorizada como administrador.
No se han cambiado las reglas remotas como parte de estos archivos.

Referencia: [creación de rulesets y bypass por PR](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/creating-rulesets-for-a-repository).

## Verificación local

Sobre la rama creada desde `main` se han comprobado:

- Compilación debug y del APK de pruebas, prueba unitaria y Android Lint.
- Prueba instrumentada real en API 29 con la configuración ficticia.
- Tres pruebas del script CI, incluidas las rutas con/sin Firebase y los fallos
  de Gradle, adb y Firebase, usando comandos simulados.
- Sintaxis Bash, validación del workflow con `actionlint` y `git diff --check`.

La suite Firebase aún no está en esta base: su integración real no se ha
ejecutado aquí. Tampoco se ha ejecutado todavía el workflow en GitHub Actions
ni se han aplicado las protecciones remotas.

## Criterio académico

El temario presenta Gradle, Android y GitHub, pero no desarrolla CI ni una
estrategia de pruebas. La ampliación se limita a ejecutar las tareas existentes
en un workflow y conectar emuladores, sin añadir arquitectura a la aplicación.
