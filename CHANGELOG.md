# Historial de cambios

Cada versión corresponde a una compilación automática publicada en
[Releases](https://github.com/firulais1xd/AdBlocker/releases) con el nombre `build-N`.
La entrada más reciente va arriba. Formato de cada entrada:

- **Motivo:** por qué se hizo el cambio.
- **Cambios:** qué se modificó y en qué archivos.
- **Cómo verificar:** qué debería notar quien instala esa versión.

---

## build-4 — 2026-10-23

**Motivo:** el usuario pidió que cada actualización quede documentada, para tener trazabilidad.

**Cambios:**
- Nuevo `CHANGELOG.md` (este archivo) con el historial de todas las versiones.
- `.github/workflows/build.yml`: las notas de cada release se toman automáticamente de la entrada más reciente de este archivo.
- `.github/workflows/build.yml`: los cambios que solo tocan archivos `.md` ya no generan un APK nuevo.
- Nuevo `CLAUDE.md` con las reglas de trabajo del repositorio, incluida la de documentar cada cambio.
- Sin cambios en el código de la app: el APK funciona igual que build-3.

**Cómo verificar:** la página de este release muestra estas notas.

---

## build-3 — 2026-10-23

**Motivo:** YouTube y MangaDogs seguían mostrando anuncios con build-2.

**Cambios:**
- `Blocklist.kt`: se agregó la lista **HaGeZi Pro** (~208.000 dominios, especializada en anuncios dentro de apps), junto con StevenBlack. En total son ~280.000 dominios.
- `Blocklist.kt`: la lista incluida en la app pasó de 32 a 70 dominios de redes de anuncios móviles (AppLovin, Unity, IronSource, Mintegral, Pangle, Moloco, Liftoff, InMobi, Bigo, Adsterra, etc.).
- `Blocklist.kt`: las listas se actualizan cada 3 días en vez de 7.
- `AdBlockVpnService.kt`: la lista se carga desde la caché al instante y se descarga en segundo plano. Antes se cortaba internet mientras se descargaba.
- `MainActivity.kt` y `activity_main.xml`: aviso en rojo cuando el "DNS privado" de Android está fijado a un servidor, porque en ese caso el bloqueador queda ignorado.

**Cómo verificar:** "Dominios en la lista" debe superar los 200.000 poco después de activar. Recomendación: borrar la caché de MangaDogs, porque los videos de anuncio que ya estaban descargados se reproducen sin conexión.

**Limitación conocida:** los anuncios de YouTube no se pueden bloquear por DNS, porque vienen de los mismos servidores que los videos.

---

## build-2 — 2026-10-22

**Motivo:** MangaDogs mostraba un video de anuncio con el bloqueador activo, y hacía falta saber desde qué dominio llegaba.

**Cambios:**
- `AdBlockVpnService.kt`: registro de los últimos 300 dominios consultados, marcados como bloqueados (🚫) o permitidos (✅).
- `MainActivity.kt` y `activity_main.xml`: botón "Ver registro de dominios" con opciones para copiar y borrar.
- `strings.xml`: textos nuevos para el registro.

**Cómo verificar:** el botón muestra en tiempo real los dominios que consultan las apps.

---

## build-1 — 2026-10-22

**Motivo:** primera versión. Una app que bloquea anuncios en otras apps sin root.

**Cambios:**
- VPN local que solo intercepta el DNS. Los dominios de publicidad reciben `0.0.0.0`, y el resto se reenvía a Cloudflare 1.1.1.1.
- Lista de bloqueo StevenBlack (~78.000 dominios) más una lista básica incluida.
- Pantalla con botón para activar o desactivar, contador de bloqueos y botón para actualizar la lista.
- Compilación automática del APK en GitHub Actions, publicada en Releases.
