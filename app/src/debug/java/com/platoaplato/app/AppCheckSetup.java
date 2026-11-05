package com.platoaplato.app;

/** Versión de pruebas (APK instalada a mano): no instala ningún proveedor de App Check.
 *  Con el proveedor de depuración y un token sin registrar, cada llamada a Gemini esperaba
 *  ~20 s a que fallara la verificación. Mientras App Check esté en "solo supervisión"
 *  (hasta publicar en Google Play) las peticiones pasan sin token.
 *  Antes del 2-nov-2026 (App Check obligatorio) las pruebas se harán con la versión release
 *  distribuida por Google Play, que usa Play Integrity (ver src/release). */
final class AppCheckSetup {
    private AppCheckSetup() { }

    static void install() {
        // Intencionadamente vacío en la versión de pruebas.
    }
}
