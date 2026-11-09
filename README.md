# practica-moviles_android
Práctica final android para Desarrollo de Aplicaciones Móviles UCAM

## Configuración de Firebase

Antes de compilar, descarga `google-services.json` desde la configuración del
proyecto Firebase para la app Android `com.example.aplicacion` y colócalo en
`app/google-services.json`. Este archivo se mantiene local y no se sube a Git.

## Integración continua

Los PR hacia `main` ejecutan compilación, pruebas unitarias, Android Lint y
pruebas instrumentadas en API 29. Cuando el PR incluye la suite Firebase,
también ejecutan sus pruebas de reglas e integración contra emuladores locales.
CI utiliza configuración ficticia y no necesita credenciales Firebase reales.

Consulta [la configuración y las protecciones acordadas](docs/ci.md).
