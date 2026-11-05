# 🍽️ Plato a Plato

> ⚠️ **Proyecto en desarrollo.** Esto no es una versión final: puede haber errores, cambios importantes entre versiones y funciones a medio terminar. Todavía no está publicada en Google Play.

App Android para **hacer una foto a la comida, estimar sus calorías con IA y llevar un registro diario**. Toda la información se guarda en el propio móvil.

![Estado](https://img.shields.io/badge/estado-en%20desarrollo-orange)
![Versión](https://img.shields.io/badge/versi%C3%B3n-1.1%20(beta)-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

---

## ✨ Funciones

- 📷 **Foto o imagen de la galería** → la IA identifica los alimentos, estima las raciones y calcula kcal, proteínas, hidratos y grasas.
- ✏️ **Corrección del resultado**: puedes indicar qué ha fallado y la IA recalcula; también ajustar las kcal de cada alimento o quitarlo.
- ➕ **Añadir a mano** sin foto.
- 🍳 Comidas agrupadas por momento del día: desayuno, almuerzo, comida, merienda, cena y picoteo.
- 🎯 **Objetivo diario** de kilocalorías con anillo de progreso y reparto de macronutrientes.
- 📊 **Historial de los últimos 7 días** con gráfico y días dentro del objetivo.
- 💾 **Copia de seguridad** en texto (copiar y restaurar).
- 🌗 Tema claro y oscuro automático.

## 🤖 Servicios de IA

La app puede funcionar de tres formas:

| Modo | ¿Necesita clave? | Notas |
|---|---|---|
| **Análisis incluido** (Gemini vía Firebase AI Logic) | No | Opción por defecto. Límite de 30 análisis al día por dispositivo. Elige automáticamente un modelo Gemini Flash disponible. |
| **Google Gemini** con tu clave | Sí, [gratis en AI Studio](https://aistudio.google.com/apikey) | Detecta qué modelos admite tu clave y escoge el mejor Flash. |
| **Claude** (Anthropic) con tu clave | Sí, [de pago por uso](https://console.anthropic.com/settings/keys) | Por defecto `claude-haiku-4-5`; el modelo se puede cambiar en Ajustes. |

Para usar una clave propia: **Ajustes (⚙) → Servicio → pega la clave → Probar clave → Guardar.** La clave se guarda solo en el móvil.

## 🏗️ Arquitectura

App híbrida muy ligera: una única `Activity` nativa que carga una interfaz web local.

```
PlatoAPlato/
├── app/
│   ├── build.gradle                 # Configuración del módulo y dependencias (Firebase)
│   └── src/
│       ├── main/
│       │   ├── assets/index.html    # Toda la interfaz y la lógica (HTML + CSS + JS)
│       │   ├── java/com/platoaplato/app/MainActivity.java
│       │   │                        # WebView, cámara/galería, puente JS ↔ Java,
│       │   │                        # llamadas HTTP y Firebase AI
│       │   └── res/                 # Icono, colores, temas
│       ├── debug/…/AppCheckSetup.java    # Sin App Check (pruebas con APK manual)
│       └── release/…/AppCheckSetup.java  # App Check con Play Integrity
├── build.gradle / settings.gradle   # Configuración de Gradle
└── instalar_y_abrir.bat             # Instala Android Studio (winget) y abre el proyecto
```

- **Interfaz**: HTML/CSS/JS sin frameworks, en `index.html`.
- **Puente nativo** (`window.AndroidBridge`): `aiGenerate` (Gemini por Firebase) y `request` (HTTP desde Java, sin restricciones CORS del WebView).
- **Datos**: `localStorage` del WebView (registro, objetivo y ajustes).
- **Firebase**: AI Logic (Gemini sin clave), App Check (Play Integrity) y Crashlytics.

**Requisitos técnicos:** Android 8.0+ (minSdk 26, targetSdk 35) · Java 17 · Android Gradle Plugin 8.10 · Gradle 8.14.

## 🚀 Compilar el proyecto

### 1. Configurar Firebase

El archivo `app/google-services.json` **no se incluye en el repositorio**. Sin él el proyecto no compila:

1. Crea un proyecto en la [consola de Firebase](https://console.firebase.google.com/).
2. Añade una app Android con el paquete `com.platoaplato.app`.
3. Activa **Firebase AI Logic** (Gemini Developer API) y, opcionalmente, App Check y Crashlytics.
4. Descarga `google-services.json` y colócalo en `app/`.

### 2. Generar la APK con Android Studio

> En Windows puedes ejecutar `instalar_y_abrir.bat`: instala Android Studio con winget si no lo tienes y abre el proyecto.

1. Android Studio → **File › Open** → elige la carpeta `PlatoAPlato`.
2. Espera a que termine la sincronización de Gradle (la primera vez tarda unos minutos).
3. **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
4. La APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
5. Pásala al móvil, ábrela y permite *instalar apps de origen desconocido*.

Con el móvil conectado por USB y la depuración activada basta con pulsar ▶ **Run**.

O desde la terminal:

```bash
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

## 🗺️ Hoja de ruta

- [x] Análisis de fotos con Gemini y Claude
- [x] Análisis incluido sin clave (Firebase AI Logic)
- [x] Registro diario, objetivo e historial semanal
- [x] Copia de seguridad en texto
- [ ] Clave de firma propia para la versión release
- [ ] Publicación en Google Play y App Check obligatorio
- [ ] Más estadísticas e historial a largo plazo
- [ ] Pruebas automatizadas

## ⚠️ Aviso

Las calorías y nutrientes son **estimaciones generadas por IA** y pueden ser inexactas. Esta app no sustituye el consejo de un profesional de la salud o la nutrición.

## 🤝 Contribuir

Al estar en desarrollo activo, las sugerencias y los informes de errores son bienvenidos a través de los [Issues](../../issues).
