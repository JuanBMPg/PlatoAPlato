package com.platoaplato.app;

import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/** Versión publicada: Play Integrity comprueba que la app es la auténtica de Google Play. */
final class AppCheckSetup {
    private AppCheckSetup() { }

    static void install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
    }
}
