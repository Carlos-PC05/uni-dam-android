# Continuar el aprendizaje

## Estado actual

El formulario permite añadir y editar movimientos. El resumen y el listado observan el mismo `StateFlow`; el listado permite eliminar movimientos y muestra un mensaje cuando queda vacío. La edición se completó en el commit `742859b`.

## Siguiente paso: filtrar por categoría

Aprenderás a derivar una lista visible sin modificar la lista original del repositorio. El filtro solo cambia qué filas muestra la pantalla.

1. Añade un selector de categoría, incluida una opción para ver todas.
2. Conserva la categoría elegida como estado de la pantalla.
3. Muestra solo los movimientos que coinciden; al elegir «Todas», muestra la lista completa.

**Comprobación:** añade 25 € en Ocio. Al filtrar por Ocio deben aparecer los dos movimientos de esa categoría; al elegir «Todas», deben aparecer los cinco movimientos.

## Fin de esta etapa

Después del filtro, comprueba en el simulador el flujo completo: añadir, editar, filtrar y eliminar; la lista y el resumen deben reflejar cada cambio. La persistencia después de cerrar la aplicación queda fuera de esta etapa.
