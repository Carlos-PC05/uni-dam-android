# Instrucciones del proyecto

## Objetivo de la práctica

Esta aplicación es una práctica universitaria que el equipo defenderá en una entrevista. La prioridad es que quienes la desarrollan puedan explicar cómo funciona de extremo a extremo, desde los datos en Firebase hasta el estado que muestra la interfaz en Compose.

- Al implementar, sigue la arquitectura MVVM del proyecto y reutiliza sus patrones. Elige la solución más sencilla que cumpla los requisitos y explica las decisiones que el equipo tendrá que defender.
- Al entregar un cambio funcional, señala los archivos clave y describe un recorrido concreto: qué acción realiza el usuario, cómo la procesa el ViewModel, qué lee o escribe el repositorio en Firebase y cómo el resultado vuelve a la interfaz. Incluye los estados de carga y error pertinentes y cómo reproducir el recorrido. Cubre las capas que intervienen en ese cambio.
- Al revisar el código usa las skills de `android-review`

## Ramas

- `main` es la rama principal compartida del proyecto.
- Para el trabajo individual de Carlos, crea las ramas `feature/*` desde `develop` y usa `develop` como destino de integración. El resto del equipo mantiene su propio flujo de trabajo.

## Código y documentación

- Aplica la skill `documentation-pro` al escribir, editar o documentar código. Sigue las convenciones del proyecto y explica en los comentarios la intención y las restricciones que el código por sí solo no expresa.
- Describe los cambios por su propósito técnico. No incluyas referencias a tickets, sesiones de agentes ni otros metadatos del flujo de trabajo en el código, los comentarios, los mensajes de commit ni otros materiales versionados. Guarda ese contexto en `docs/agents/`.

## Agent skills

Los archivos específicos de la suite de Matt Pocock viven en `docs/agents/`, ignorado por Git. Este `AGENTS.md` permanece en la raíz y se versiona con el proyecto.

### Issue tracker

Las specs viven en `docs/agents/specs/` y cada ticket en `docs/agents/tickets/<slug>.md`. Para importar, crear, consultar o actualizar specs y tickets, lee `docs/agents/issue-tracker.md`.

### Triage labels

Para clasificar tickets locales, usa las cinco etiquetas predeterminadas de `docs/agents/triage-labels.md`.

### Domain docs

El proyecto tiene un único contexto de dominio. Antes de explorar o documentar el dominio, lee `docs/agents/domain.md`.
