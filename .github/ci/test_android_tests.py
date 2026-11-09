"""Comprueba el arranque de las pruebas Android sin necesitar SDK ni Firebase."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("android-tests.sh")


class AndroidTestsScriptTest(unittest.TestCase):
    """Verifica ambas rutas del script y la propagación de fallos al check CI."""

    def run_script(self, firebase=False, failing_command=""):
        """Ejecuta el script con comandos simulados y devuelve resultado y llamadas."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            commands = root / "bin"
            commands.mkdir()
            stub = (
                '#!/usr/bin/env bash\n'
                'name="${0##*/}"\n'
                'printf "%s %s\\n" "$name" "$*" >> "$CALLS"\n'
                'if [[ "$name" == "$FAILING_COMMAND" ]]; then exit 17; fi\n'
            )
            for path in (root / "gradlew", commands / "adb"):
                path.write_text(stub)
                path.chmod(0o755)
            if firebase:
                package = root / "firebase-tests"
                (package / "node_modules/.bin").mkdir(parents=True)
                (package / "package.json").write_text("{}")
                cli = package / "node_modules/.bin/firebase"
                cli.write_text(stub)
                cli.chmod(0o755)
            calls = root / "calls"
            result = subprocess.run(
                ["bash", str(SCRIPT)], cwd=root, capture_output=True, text=True,
                env={**os.environ, "PATH": f"{commands}:{os.environ['PATH']}",
                     "CALLS": str(calls), "FAILING_COMMAND": failing_command},
                check=False,
            )
            return result, calls.read_text().splitlines()

    def test_android_without_firebase(self):
        """La base main ejecuta las instrumentadas aunque todavía no tenga Firebase."""
        result, calls = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls, ["gradlew --no-daemon :app:connectedDebugAndroidTest"])

    def test_firebase_connects_both_ports_and_enables_repository_test(self):
        """La suite Firebase usa emuladores demo y activa la prueba que sería omitida."""
        result, calls = self.run_script(firebase=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls[:2], ["adb reverse tcp:9099 tcp:9099", "adb reverse tcp:8080 tcp:8080"])
        self.assertIn("--project demo-vesta --only auth,firestore --config firebase.json", calls[2])
        self.assertIn("-Pandroid.testInstrumentationRunnerArguments.firebaseEmulators=true", calls[2])

    def test_failures_block_ci(self):
        """Los fallos de Gradle, adb o Firebase conservan su código de salida."""
        for firebase, command in ((False, "gradlew"), (True, "adb"), (True, "firebase")):
            with self.subTest(command=command):
                result, calls = self.run_script(firebase, command)
                self.assertEqual(result.returncode, 17, result.stderr)
                if command == "adb":
                    self.assertEqual(len(calls), 1)


if __name__ == "__main__":
    unittest.main()
