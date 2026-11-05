package com.platoaplato.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

/** Versión de pruebas (APK instalada a mano): usa el proveedor de depuración de App Check.
 *
 *  Desde que App Check es obligatorio (2-nov-2026) las peticiones sin token se rechazan, así que
 *  la APK de pruebas genera un "token de depuración". Hay que registrarlo UNA vez por instalación en
 *  Firebase Console › App Check › Apps › (menú ⋮) › Administrar tokens de depuración.
 *  La app lo enseña en Ajustes (solo en esta versión) para poder copiarlo; también sale en Logcat. */
final class AppCheckSetup {
    private AppCheckSetup() { }

    static void install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());
    }

    /** Lee el token que el SDK guarda en el móvil (vacío si todavía no se ha generado). */
    static String debugToken(Context ctx) {
        try {
            String prefs = "com.google.firebase.appcheck.debug.store." + FirebaseApp.getInstance().getPersistenceKey();
            SharedPreferences sp = ctx.getSharedPreferences(prefs, Context.MODE_PRIVATE);
            String t = sp.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", "");
            return t == null ? "" : t;
        } catch (Exception e) {
            return "";
        }
    }
}
