#!/usr/bin/env bash
# Ejecuta todas las pruebas Android y conecta Firebase local cuando existe su suite.
set -euo pipefail

# main todavía no contiene la funcionalidad de acceso ni sus pruebas Firebase.
if [[ -f firebase-tests/package.json ]]; then
  adb reverse tcp:9099 tcp:9099
  adb reverse tcp:8080 tcp:8080
  firebase-tests/node_modules/.bin/firebase emulators:exec \
    --project demo-vesta --only auth,firestore --config firebase.json \
    './gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.firebaseEmulators=true'
else
  ./gradlew --no-daemon :app:connectedDebugAndroidTest
fi
