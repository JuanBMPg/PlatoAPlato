package com.platoaplato.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.util.Base64;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;

import androidx.core.content.FileProvider;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;
    private static final int REQ_VOICE = 1002;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private String voiceId;
    private final ExecutorService net = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Firebase: App Check antes de cualquier llamada a Gemini
        FirebaseApp.initializeApp(this);
        AppCheckSetup.install();

        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.bg));
        web = new WebView(this);
        web.setBackgroundColor(getColor(R.color.bg));
        root.addView(web, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        // Respetar la barra de estado y la de navegación (Android 15+ dibuja de borde a borde)
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int[] p = systemInsets(insets);
            v.setPadding(p[0], p[1], p[2], p[3]);
            return insets;
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage = registro guardado en el móvil
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(true);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme())) return false;
                // Enlaces externos (p. ej. para conseguir la clave) se abren en el navegador
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                return params.isCaptureEnabled() ? openCamera() : openGallery(params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");

        // Botón Atrás: API nueva en Android 13+ (la antigua queda para versiones anteriores)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    /** Márgenes de las barras del sistema: {izquierda, arriba, derecha, abajo}. */
    @SuppressWarnings("deprecation")
    private static int[] systemInsets(WindowInsets insets) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets i = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            return new int[]{i.left, i.top, i.right, i.bottom};
        }
        return new int[]{insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()};
    }

    private void handleBack() {
        // Deja que la página cierre ventanas abiertas; si no hay ninguna, sale de la app
        web.evaluateJavascript("window.__onBack ? window.__onBack() : false", value -> {
            if (!"true".equals(value)) finish();
        });
    }

    private boolean openCamera() {
        try {
            File dir = new File(getCacheDir(), "photos");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "foto_" + System.currentTimeMillis() + ".jpg");
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_FILE);
            return true;
        } catch (ActivityNotFoundException e) {
            cameraUri = null;
            return openGallery(false);
        } catch (Exception e) {
            cancelChooser();
            return false;
        }
    }

    private boolean openGallery(boolean multiple) {
        cameraUri = null;
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            if (multiple) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(Intent.createChooser(i, "Elegir foto"), REQ_FILE);
            return true;
        } catch (Exception e) {
            cancelChooser();
            return false;
        }
    }

    private void cancelChooser() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE) { onVoiceResult(resultCode, data); return; }
        if (requestCode != REQ_FILE || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (cameraUri != null) result = new Uri[]{cameraUri};
            else if (data != null && data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                ArrayList<Uri> list = new ArrayList<>();
                for (int k = 0; k < n; k++) list.add(data.getClipData().getItemAt(k).getUri());
                result = list.toArray(new Uri[0]);
            } else if (data != null && data.getData() != null) result = new Uri[]{data.getData()};
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    private void onVoiceResult(int resultCode, Intent data) {
        String id = voiceId; voiceId = null;
        if (id == null) return;
        String text = null;
        if (resultCode == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) text = r.get(0);
        }
        reply("window.__nativeVoice", id, text, text == null ? "cancel" : null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    // En Android 13+ el botón Atrás va por OnBackInvokedCallback (registrado en onCreate) y este
    // método ya no se llama; se mantiene solo para Android 8-12, donde sigue siendo la vía correcta.
    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onDestroy() {
        net.shutdownNow();
        super.onDestroy();
    }

    private void sendToPage(String js) {
        main.post(() -> web.evaluateJavascript(js, null));
    }

    /** Llama a fn(id, valor, código de error) en la página. */
    private void reply(String fn, String id, String value, String code) {
        sendToPage(fn + "(" + JSONObject.quote(id) + "," + (value == null ? "null" : JSONObject.quote(value)) + ","
                + (code == null ? "null" : JSONObject.quote(code)) + ")");
    }

    /** Traduce los errores del SDK a códigos que entiende la página. */
    private static String errorCode(Throwable t) {
        String name = t.getClass().getSimpleName();
        String msg = String.valueOf(t.getMessage()).toLowerCase(Locale.ROOT);
        if (msg.contains("app check") || msg.contains("appcheck") || msg.contains("app attestation")) return "app_check";
        if (msg.contains("high demand") || msg.contains("overloaded") || msg.contains("503")) return "busy";
        if (name.contains("QuotaExceeded") || msg.contains("quota") || msg.contains("429") || msg.contains("resource_exhausted")) return "rate_limited";
        if (msg.contains("not found") || msg.contains("404") || msg.contains("is not supported") || msg.contains("unsupported model")) return "bad_model";
        if (name.contains("PromptBlocked") || name.contains("ResponseStopped") || msg.contains("safety")) return "refused";
        if (t instanceof java.io.IOException || msg.contains("unable to resolve host") || msg.contains("timeout") || msg.contains("failed to connect")) return "network";
        return "api";
    }

    /** Peticiones HTTP hechas desde Java (sin límites de CORS del WebView) y funciones nativas. */
    private class Bridge {
        /** Gemini a través de Firebase AI Logic: la app no lleva ninguna clave.
         *  imagesJson: lista JSON de imágenes JPEG en base64 (puede estar vacía). */
        @JavascriptInterface
        public void aiGenerate(final String id, final String modelName, final String prompt, final String imagesJson) {
            net.execute(() -> {
                try {
                    GenerationConfig.Builder cfg = new GenerationConfig.Builder();
                    cfg.responseMimeType = "application/json";
                    cfg.temperature = 0.2f;
                    GenerativeModel gm = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                            .generativeModel(modelName, cfg.build());
                    GenerativeModelFutures model = GenerativeModelFutures.from(gm);

                    Content.Builder cb = new Content.Builder();
                    JSONArray imgs = new JSONArray(imagesJson == null || imagesJson.isEmpty() ? "[]" : imagesJson);
                    for (int k = 0; k < imgs.length(); k++) {
                        byte[] bytes = Base64.decode(imgs.getString(k), Base64.DEFAULT);
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) cb.addImage(bmp);
                    }
                    cb.addText(prompt);

                    ListenableFuture<GenerateContentResponse> f = model.generateContent(cb.build());
                    Futures.addCallback(f, new FutureCallback<GenerateContentResponse>() {
                        @Override public void onSuccess(GenerateContentResponse r) {
                            String text = r.getText() == null ? "" : r.getText();
                            sendToPage("window.__nativeAI(" + JSONObject.quote(id) + ",true," + JSONObject.quote(text) + ",null)");
                        }
                        @Override public void onFailure(Throwable t) {
                            android.util.Log.w("PlatoAI", "Fallo de Gemini", t);
                            sendToPage("window.__nativeAI(" + JSONObject.quote(id) + ",false," + JSONObject.quote(String.valueOf(t.getMessage())) + "," + JSONObject.quote(errorCode(t)) + ")");
                        }
                    }, net);
                } catch (Throwable t) {
                    android.util.Log.w("PlatoAI", "No se pudo preparar la llamada", t);
                    sendToPage("window.__nativeAI(" + JSONObject.quote(id) + ",false," + JSONObject.quote(String.valueOf(t.getMessage())) + "," + JSONObject.quote(errorCode(t)) + ")");
                }
            });
        }

        @JavascriptInterface
        public void request(final String id, final String url, final String method,
                            final String headersJson, final String body) {
            net.execute(() -> {
                int status = 0;
                String text;
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod(method == null || method.isEmpty() ? "POST" : method);
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(120000);
                    JSONObject h = new JSONObject(headersJson == null || headersJson.isEmpty() ? "{}" : headersJson);
                    Iterator<String> keys = h.keys();
                    while (keys.hasNext()) { String k = keys.next(); c.setRequestProperty(k, h.getString(k)); }
                    if (body != null && !body.isEmpty()) {
                        c.setDoOutput(true);
                        byte[] b = body.getBytes(StandardCharsets.UTF_8);
                        c.setFixedLengthStreamingMode(b.length);
                        try (OutputStream os = c.getOutputStream()) { os.write(b); }
                    }
                    status = c.getResponseCode();
                    InputStream is = status >= 400 ? c.getErrorStream() : c.getInputStream();
                    text = is == null ? "" : readAll(is);
                } catch (Exception e) {
                    text = "NETWORK_ERROR: " + e.getMessage();
                } finally {
                    if (c != null) c.disconnect();
                }
                sendToPage("window.__nativeResponse(" + JSONObject.quote(id) + "," + status + "," + JSONObject.quote(text) + ")");
            });
        }

        /** Escáner de códigos de barras de Google (EAN/UPC de productos envasados). */
        @JavascriptInterface
        public void scanBarcode(final String id) {
            main.post(() -> {
                try {
                    GmsBarcodeScannerOptions opts = new GmsBarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                                    Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                            .enableAutoZoom()
                            .build();
                    GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(MainActivity.this, opts);
                    scanner.startScan()
                            .addOnSuccessListener(b -> reply("window.__nativeScan", id, b.getRawValue(), b.getRawValue() == null ? "empty" : null))
                            .addOnCanceledListener(() -> reply("window.__nativeScan", id, null, "cancel"))
                            .addOnFailureListener(e -> {
                                android.util.Log.w("PlatoScan", "Fallo del escáner", e);
                                reply("window.__nativeScan", id, String.valueOf(e.getMessage()), "error");
                            });
                } catch (Throwable t) {
                    reply("window.__nativeScan", id, String.valueOf(t.getMessage()), "error");
                }
            });
        }

        /** Dictado por voz con el reconocedor del sistema (no necesita permiso de micrófono). */
        @JavascriptInterface
        public void voice(final String id) {
            main.post(() -> {
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES");
                i.putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué has comido?");
                try {
                    voiceId = id;
                    startActivityForResult(i, REQ_VOICE);
                } catch (ActivityNotFoundException e) {
                    voiceId = null;
                    reply("window.__nativeVoice", id, null, "unavailable");
                }
            });
        }

        /** Token de depuración de App Check (solo en la versión de pruebas; vacío en la publicada). */
        @JavascriptInterface
        public String appCheckDebugToken() {
            return AppCheckSetup.debugToken(MainActivity.this);
        }

        @JavascriptInterface
        public String appVersion() {
            return BuildConfig.VERSION_NAME + (BuildConfig.DEBUG ? " (pruebas)" : "");
        }
    }

    private static String readAll(InputStream is) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return bo.toString("UTF-8");
    }
}
