# AdBlocker para Android

Bloquea anuncios en todas las apps del teléfono (juegos, apps gratuitas, navegadores) sin necesidad de root.

## Cómo funciona

La app crea una **VPN local** (nada sale a servidores externos) que solo captura las consultas DNS:

1. Una app pregunta "¿cuál es la IP de `googlesyndication.com`?".
2. Si el dominio está en la lista de publicidad, la app responde `0.0.0.0` → el anuncio no carga.
3. Si no, la consulta se reenvía al DNS real (Cloudflare 1.1.1.1) y todo funciona normal.

Solo el tráfico DNS pasa por la VPN, así que la velocidad de internet no cambia.
La lista de dominios es la de [StevenBlack/hosts](https://github.com/StevenBlack/hosts) (~80.000 dominios de anuncios y malware), descargada al activar y renovada cada 7 días. Mientras no se descargue, se usa una lista básica incluida.

## Compilar e instalar

1. Instala [Android Studio](https://developer.android.com/studio).
2. *File › Open* y elige la carpeta `AdBlocker`. Android Studio descargará Gradle y las dependencias.
3. Conecta tu teléfono con la depuración USB activada y pulsa **Run ▶**.
   - O genera el APK en *Build › Build App Bundle(s) / APK(s) › Build APK(s)* y pásalo al teléfono.

Requisitos: Android 7.0 (API 24) o superior.

## Uso

1. Abre la app y pulsa **Activar**; acepta el permiso de VPN.
2. **Importante:** desactiva el **DNS privado** (Ajustes › Red e internet › DNS privado › Desactivado). Si está activo, Android lo usa en lugar de nuestra VPN.
3. Opcional: en Ajustes › Red › VPN › AdBlocker activa **VPN siempre activa** para que arranque sola al reiniciar.

## Limitaciones

- **YouTube, Instagram, Facebook y TikTok** sirven los anuncios desde los mismos dominios que el contenido; bloquearlos por DNS rompería la app. Ningún bloqueador DNS (Blokada, AdGuard DNS, etc.) los elimina por completo.
- Las apps que usan su propio DNS cifrado (DoH, p. ej. Chrome con "DNS seguro") pueden saltarse el filtro. En Chrome: Ajustes › Privacidad › Usar DNS seguro › Desactivar.
- Solo puede haber una VPN activa a la vez en Android.
- Google Play no permite publicar bloqueadores de anuncios que interfieran con otras apps; distribúyela como APK o en F-Droid.

## Estructura

```
app/src/main/java/com/example/adblocker/
  MainActivity.kt        Pantalla con botón activar/desactivar y contador
  AdBlockVpnService.kt   VPN local que intercepta y filtra el DNS
  DnsPackets.kt          Lectura de consultas DNS y armado de paquetes IP/UDP
  Blocklist.kt           Descarga, caché y búsqueda en la lista de dominios
```
