# 🍽️ Plato a Plato

> ⚠️ **Proyecto en desarrollo.** Esto no es una versión final: puede haber errores, cambios importantes entre versiones y funciones a medio terminar. Todavía no está publicada en Google Play.

App Android para **apuntar lo que comes en segundos**: con una foto (la IA estima calorías y macros), con el buscador de alimentos, con el código de barras del envase o repitiendo tus comidas habituales con un toque. Toda la información se guarda en el propio móvil.

![Estado](https://img.shields.io/badge/estado-en%20desarrollo-orange)
![Versión](https://img.shields.io/badge/versi%C3%B3n-1.2%20(beta)-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

---

## ✨ Funciones

**Apuntar una comida**
- 📷 **Fotos con IA**: hasta 4 fotos de la misma comida (primero, segundo, postre…) o una foto con varios platos. La IA identifica cada alimento, estima la ración y calcula kcal, proteínas, hidratos y grasas.
- 🔍 **Buscador de alimentos sin IA**: más de 230 alimentos y platos habituales. Entiende cantidades escritas como «100 g de arroz cocido», «2 huevos», «un vaso de leche» o «3 lonchas de jamón».
- ▦ **Código de barras**: escanea un producto envasado y toma sus datos de [Open Food Facts](https://world.openfoodfacts.org). Los productos escaneados se guardan para usarlos sin conexión.
- 🎤 **Por voz**: dicta «me he comido dos tostadas con tomate» y se calcula al momento.
- ★ **Favoritos y recientes**: repite tu desayuno de siempre con un solo toque, y deshazlo si te equivocas.
- 📖 **Recetas**: guarda un plato casero con sus ingredientes y raciones, y apunta la parte que te comas.
- ✏️ **Ajuste fino**: cambia gramos o kcal de cada alimento, añade o quita alimentos, o pide a la IA que rehaga el cálculo con una pista («era media ración»).

**Registro**
- 🗂️ Comidas agrupadas por momento del día: desayuno, almuerzo, comida, merienda, cena y picoteo.
- ✏️ **Editar comidas guardadas**: cambiar nombre, cantidades, ración (×½ … ×2), momento del día o fecha; repetirlas hoy o borrarlas (con deshacer).
- 🎯 **Objetivo diario** con anillo de progreso y reparto de macronutrientes.
- 📊 **Últimos 7 días** con gráfico y días dentro del objetivo.
- 💾 **Copia de seguridad** en texto (registro, favoritos, recetas y productos).
- 🌗 Tema claro y oscuro automático.

## 🤖 Servicios de IA

| Modo | ¿Necesita clave? | Notas |
|---|---|---|
| **Análisis incluido** (Gemini vía Firebase AI Logic) | No | Opción por defecto. Límite de 30 análisis al día por dispositivo. Elige automáticamente un modelo Gemini Flash disponible. |
| **Google Gemini** con tu clave | Sí, [gratis en AI Studio](https://aistudio.google.com/apikey) | Detecta qué modelos admite tu clave y escoge el mejor Flash. |
| **Claude** (Anthropic) con tu clave | Sí, [de pago por uso](https://console.anthropic.com/settings/keys) | Por defecto `claude-haiku-4-5`; el modelo se puede cambiar en Ajustes. |

El buscador, el código de barras, los favoritos y las recetas **no gastan análisis de IA**.

Para usar una clave propia: **Ajustes (⚙) → Opciones avanzadas → Servicio → pega la clave → Probar clave → Guardar.** La clave se guarda solo en el móvil.

## 🏗️ Arquitectura

App híbrida muy ligera: una única `Activity` nativa que carga una interfaz web local.

```
PlatoAPlato/
├── app/
│   ├── build.gradle                 # Configuración del módulo y dependencias
│   └── src/
│       ├── main/
│       │   ├── assets/
│       │   │   ├── index.html       # Toda la interfaz y la lógica (HTML + CSS + JS)
│       │   │   └── foods.js         # Tabla de alimentos del buscador (por 100 g)
│       │   ├── java/com/platoaplato/app/MainActivity.java
│       │   │                        # WebView, cámara/galería, escáner, voz,
│       │   │                        # puente JS ↔ Java, HTTP y Firebase AI
│       │   └── res/                 # Icono, colores, temas
│       ├── debug/…/AppCheckSetup.java    # App Check con token de depuración (APK de pruebas)
│       └── release/…/AppCheckSetup.java  # App Check con Play Integrity (Google Play)
├── build.gradle / settings.gradle   # Configuración de Gradle
└── instalar_y_abrir.bat             # Instala Android Studio (winget) y abre el proyecto
```

- **Interfaz**: HTML/CSS/JS sin frameworks, en `index.html`.
- **Puente nativo** (`window.AndroidBridge`): `aiGenerate` (Gemini por Firebase, con varias imágenes), `request` (HTTP desde Java, sin restricciones CORS), `scanBarcode` (escáner de Google Play services, sin permiso de cámara) y `voice` (reconocedor de voz del sistema, sin permiso de micrófono).
- **Datos**: `localStorage` del WebView (registro, objetivo, favoritos, recetas, productos y ajustes).
- **Firebase**: AI Logic (Gemini sin clave), App Check y Crashlytics.

**Requisitos técnicos:** Android 8.0+ (minSdk 26, targetSdk 36, compileSdk 37) · Java 17 · Android Gradle Plugin 9.4 · Gradle 9.8 · Android Studio Quail 4 (2026.1.4) o posterior.

## 🚀 Compilar el proyecto

### 1. Configurar Firebase

El archivo `app/google-services.json` **no se incluye en el repositorio**. Sin él el proyecto no compila:

1. Crea un proyecto en la [consola de Firebase](https://console.firebase.google.com/).
2. Añade una app Android con el paquete `com.platoaplato.app`.
3. Activa **Firebase AI Logic** (Gemini Developer API), **App Check** y, si quieres, Crashlytics.
4. Descarga `google-services.json` y colócalo en `app/`.

### 2. Generar la APK con Android Studio

> En Windows puedes ejecutar `instalar_y_abrir.bat`: instala Android Studio con winget si no lo tienes y abre el proyecto.

1. Android Studio → **File › Open** → elige la carpeta `PlatoAPlato`.
2. Espera a que termine la sincronización de Gradle (la primera vez tarda unos minutos).
3. **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
4. La APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
5. Pásala al móvil, ábrela y permite *instalar apps de origen desconocido*.

O desde la terminal:

```bash
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

### 3. Registrar el token de App Check (solo APK de pruebas)

App Check es obligatorio desde el 2 de noviembre de 2026, así que la APK de pruebas usa un **token de depuración** que hay que registrar una vez por instalación:

1. Abre la app y haz un análisis con foto (fallará la primera vez: es normal).
2. Ve a **Ajustes (⚙) → Versión de pruebas: App Check** y pulsa **Copiar token**.
3. En Firebase Console: **App Check → Apps → ⋮ → Administrar tokens de depuración → Añadir** y pega el token.

La versión publicada en Google Play usa Play Integrity y no necesita este paso.

## 🗺️ Hoja de ruta

**Fase 1: base sólida** ✅
- [x] Análisis sin clave con Firebase AI Logic y App Check
- [x] Límite de uso por usuario (30 análisis/día)
- [x] Mejor estimación (ración, aceite y salsas, referencias de tamaño)
- [x] Rehacer el análisis con una pista
- [x] Registro de errores con Crashlytics
- [x] Deuda técnica: AGP 9.4 + Gradle 9.8, botón Atrás con la API nueva, sin Java fijado a mano
- [x] App Check obligatorio: token de depuración para la APK de pruebas

**Fase 2: registrar más rápido** ✅
- [x] Editar y mover comidas guardadas
- [x] Favoritos y recientes
- [x] Recetas con ingredientes
- [x] Escáner de código de barras (Open Food Facts)
- [x] Buscador de alimentos por nombre y cantidad, sin IA
- [x] Varias fotos por comida
- [x] Entrada por voz

**Próximas fases**
- [ ] Fase 3: personalización y hábitos (objetivo calculado, macros, peso, agua, recordatorios, resúmenes, widget)
- [ ] Fase 4: cuenta y nube (inicio de sesión, sincronización, exportar CSV, Health Connect)
- [ ] Fase 5: publicación en Google Play (firma propia, AAB, privacidad, prueba cerrada, español e inglés)

## ⚠️ Aviso

Las calorías y nutrientes son **estimaciones** (de la IA o de tablas de composición) y pueden ser inexactas. Esta app no sustituye el consejo de un profesional de la salud o la nutrición.

Los datos de productos envasados proceden de [Open Food Facts](https://world.openfoodfacts.org), disponibles bajo la licencia [Open Database License](https://opendatacommons.org/licenses/odbl/1-0/).

## 🤝 Contribuir

Al estar en desarrollo activo, las sugerencias y los informes de errores son bienvenidos a través de los [Issues](../../issues).
