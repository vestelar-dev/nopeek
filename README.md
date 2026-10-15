# NoPeek

Nivel de Privacy display por app para el Galaxy S26 Ultra.

One UI decide en qué apps se enciende Privacy display, pero el nivel (normal o máximo) es uno solo para todo el sistema. NoPeek lo cambia según la app que esté en primer plano: las apps marcadas van en máximo y el resto en normal.

## Cómo funciona

- Un servicio de accesibilidad (`LevelService`) recibe los cambios de ventana. Solo cuenta las actividades reales: popups, toasts, el teclado y SystemUI no cambian el nivel.
- Para cada app escribe `Settings.System.privacy_display_level`: `0` normal, `1` máximo. No toca si Privacy display está encendido; eso sigue siendo cosa de One UI.
- No lee el contenido de la pantalla ni usa red.

## Instalación

1. Instalar el APK.
2. En la app, conceder **Modificar ajustes del sistema** y activar el **servicio de accesibilidad**.
3. Marcar las apps que deben ir en máximo.

## Compilar

```bash
./gradlew :app:assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Necesita el SDK de Android (ruta en `local.properties`) y un JDK 21.

`targetSdk` se queda en 28 a propósito: es con el que se comprobó en el dispositivo que se puede escribir la clave oculta `privacy_display_level`.

## probe/

Herramienta de investigación con la que se encontró la clave. Escanea el framework y las apps de Samsung buscando cadenas relacionadas con Privacy display, vigila los cambios en `Settings` mientras se toca el panel rápido y prueba escrituras. Se compila aparte:

```bash
cd probe && ./gradlew :app:assembleRelease
```
