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

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
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

        // Respetar la barra de estado y la de navegación (Android 15 dibuja de borde a borde)
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, left, right;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                top = i.top; bottom = i.bottom; left = i.left; right = i.right;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage = registro guardado en el móvil
        s.setDatabaseEnabled(true);
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
                return params.isCaptureEnabled() ? openCamera() : openGallery();
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
            return openGallery();
        } catch (Exception e) {
            cancelChooser();
            return false;
        }
    }

    private boolean openGallery() {
        cameraUri = null;
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
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
        if (requestCode != REQ_FILE || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (cameraUri != null) result = new Uri[]{cameraUri};
            else if (data != null && data.getData() != null) result = new Uri[]{data.getData()};
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        handleBack();   // solo se usa en Android 12 o anterior
    }

    @Override
    protected void onDestroy() {
        net.shutdownNow();
        super.onDestroy();
    }

    private void sendToPage(String js) {
        main.post(() -> web.evaluateJavascript(js, null));
    }

    /** Traduce los errores del SDK a códigos que entiende la página. */
    private static String errorCode(Throwable t) {
        String name = t.getClass().getSimpleName();
        String msg = String.valueOf(t.getMessage()).toLowerCase();
        if (msg.contains("app check") || msg.contains("appcheck")) return "app_check";
        if (msg.contains("high demand") || msg.contains("overloaded") || msg.contains("503")) return "busy";
        if (name.contains("QuotaExceeded") || msg.contains("quota") || msg.contains("429") || msg.contains("resource_exhausted")) return "rate_limited";
        if (msg.contains("not found") || msg.contains("404") || msg.contains("is not supported") || msg.contains("unsupported model")) return "bad_model";
        if (msg.contains("app check") || msg.contains("appcheck") || msg.contains("app attestation")) return "app_check";
        if (name.contains("PromptBlocked") || name.contains("ResponseStopped") || msg.contains("safety")) return "refused";
        if (t instanceof java.io.IOException || msg.contains("unable to resolve host") || msg.contains("timeout") || msg.contains("failed to connect")) return "network";
        return "api";
    }

    /** Peticiones HTTP hechas desde Java (sin límites de CORS del WebView). */
    private class Bridge {
        /** Gemini a través de Firebase AI Logic: la app no lleva ninguna clave. */
        @JavascriptInterface
        public void aiGenerate(final String id, final String modelName, final String prompt, final String imageB64) {
            net.execute(() -> {
                try {
                    GenerationConfig.Builder cfg = new GenerationConfig.Builder();
                    cfg.responseMimeType = "application/json";
                    cfg.temperature = 0.2f;
                    GenerativeModel gm = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                            .generativeModel(modelName, cfg.build());
                    GenerativeModelFutures model = GenerativeModelFutures.from(gm);

                    Content.Builder cb = new Content.Builder();
                    if (imageB64 != null && !imageB64.isEmpty()) {
                        byte[] bytes = Base64.decode(imageB64, Base64.DEFAULT);
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
