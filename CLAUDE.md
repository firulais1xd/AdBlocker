# Reglas del repositorio

- **Cada cambio que se suba a GitHub debe documentarse en `CHANGELOG.md`**, en una entrada nueva arriba del todo con el formato `## build-N — AAAA-MM-DD` y las secciones Motivo, Cambios (indicando los archivos) y Cómo verificar.
  - N es el número de la próxima compilación: el número de ejecución de GitHub Actions, que aumenta en 1 con cada push que genera un APK.
  - Los cambios que solo tocan archivos `.md` no generan compilación. Se documentan dentro de la siguiente entrada que sí la genere.
- Las notas de cada release se toman automáticamente de la primera entrada de `CHANGELOG.md`, así que hay que actualizarla **en el mismo commit** que el cambio.
- Los mensajes de commit van en español y describen el cambio.
- Idioma de la app y de la documentación: español.
