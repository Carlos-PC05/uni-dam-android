# Registro de movimientos y flujo mensual

1. Con una cuenta nueva, configura en Inicio un saldo inicial de 1.000 € con fecha del primer día del mes anterior.
2. En Movimientos, abre «Registrar movimiento». Guarda un ingreso de 500 € con la fecha del saldo inicial. Guarda un gasto de Comida de 200 € con la misma fecha. La lista del mes muestra ambos con su signo.
3. En Inicio, selecciona el mes del registro. El efectivo debe ser 1.300 €, los ingresos 500 €, los gastos 200 € y el flujo +300 €. Cambia al mes actual. El efectivo sigue en 1.300 € y los tres importes mensuales quedan en cero.
4. Intenta guardar con fecha anterior al saldo inicial, fecha de mañana, importe cero o importe con tres decimales. Cada intento muestra un error y conserva los campos. La fecha inicial y hoy son válidas. Prueba también 0,01 € para comprobar la precisión.
5. Abre el formulario con conexión y desactiva la red antes de guardar. Después del error, recupera la conexión y reintenta con los mismos campos. Debe aparecer un único movimiento. Cierra sesión y accede con otra cuenta para comprobar que sus movimientos son independientes.

El ViewModel valida el formulario y recupera el identificador del intento pendiente al abrir la cuenta. El repositorio crea el documento con una transacción que reutiliza un registro ya confirmado con ese identificador. Firestore comprueba el propietario, el saldo inicial y los campos. Cuando confirma, el ViewModel incorpora el movimiento y conserva seleccionado su mes. Inicio suma todos los movimientos para calcular el efectivo y toma únicamente el mes seleccionado para el resumen.

`./gradlew :app:testDebugUnitTest` comprueba el cálculo en céntimos. `npm test` desde `firebase-tests` ejecuta las reglas contra el emulador con dos usuarios y acceso anónimo.

## Recuperar un intento pendiente

1. Abre el formulario con conexión y corta la red antes de guardar. Cuando aparezca el error, cierra por completo la app y vuelve a abrirla con conexión y la misma cuenta.
2. En Movimientos, pulsa «Continuar guardado» y comprueba que conserva los datos. Pulsa «Reintentar guardado». Debe aparecer un único movimiento y el saldo de Inicio debe incluirlo una sola vez.
3. Para comprobar un rechazo definitivo en un emulador, adelanta dos días el reloj y guarda un movimiento con esa fecha. Cuando Firestore lo rechace, restaura el reloj, corrige la fecha en el formulario y guarda. Los demás campos deben seguir disponibles.
4. Con una cuenta nueva, deja Inicio abierto antes de configurar el saldo inicial. Configúralo desde otro dispositivo con la misma cuenta. En el primero, entra en Movimientos y vuelve a Inicio. Comprueba que aparecen el efectivo y el resumen mensual.
5. Activa el modo oscuro del sistema y abre un mes con un ingreso. Comprueba que su importe mantiene el verde de Vesta y el signo positivo.

El intento se cifra con una clave de Android Keystore y se guarda por cuenta en un archivo privado excluido de las copias de seguridad. Se elimina al confirmar la escritura o el rechazo. Si la confirmación se pierde, el reintento vuelve a consultar el mismo documento.
