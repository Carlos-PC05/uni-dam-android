# Registro de movimientos y flujo mensual

1. Con una cuenta nueva, configura en Inicio un saldo inicial de 1.000 € con fecha del primer día del mes anterior.
2. En Movimientos, abre «Registrar movimiento». Guarda un ingreso de 500 € con la fecha del saldo inicial. Guarda un gasto de Comida de 200 € con la misma fecha. La lista del mes muestra ambos con su signo.
3. En Inicio, selecciona el mes del registro. El efectivo debe ser 1.300 €, los ingresos 500 €, los gastos 200 € y el flujo +300 €. Cambia al mes actual. El efectivo sigue en 1.300 € y los tres importes mensuales quedan en cero.
4. Intenta guardar con fecha anterior al saldo inicial, fecha de mañana, importe cero o importe con tres decimales. Cada intento muestra un error y conserva los campos. La fecha inicial y hoy son válidas. Prueba también 0,01 € para comprobar la precisión.
5. Abre el formulario con conexión y desactiva la red antes de guardar. Después del error, recupera la conexión y reintenta con los mismos campos. Debe aparecer un único movimiento. Cierra sesión y accede con otra cuenta para comprobar que sus movimientos son independientes.

El ViewModel valida el formulario y conserva el identificador del intento mientras vive el grafo privado. El repositorio crea el documento con una transacción que reutiliza un registro ya confirmado con ese identificador. Firestore comprueba el propietario, el saldo inicial y los campos. Cuando confirma, el ViewModel incorpora el movimiento y conserva seleccionado su mes. Inicio suma todos los movimientos para calcular el efectivo y toma únicamente el mes seleccionado para el resumen.

`./gradlew :app:testDebugUnitTest` comprueba el cálculo en céntimos. `npm test` desde `firebase-tests` ejecuta las reglas contra el emulador con dos usuarios y acceso anónimo.
