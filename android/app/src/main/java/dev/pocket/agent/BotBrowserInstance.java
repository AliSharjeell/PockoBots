package dev.pocket.agent;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Isolated browser instance for a single bot.
 * Features:
 * - Multi-profile cookie and session isolation per bot (via AndroidX WebKit).
 * - Background execution in app memory without OS accessibility or ADB.
 * - DOM distillation and Set-of-Marks indexing (browser-use / OpenBUA compatible).
 * - Off-screen vision screenshots in memory.
 * - In-memory synthetic touch and DOM-level actions.
 * - Human-in-the-loop handoff for logins, CAPTCHAs, and 2FA.
 */
public class BotBrowserInstance {
    private final String botId;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final WebView webView;

    private volatile String currentUrl = "about:blank";
    private volatile String currentTitle = "New Tab";
    private volatile int loadProgress = 100;
    private volatile boolean isLoading = false;
    private volatile String lastError = "";

    // Human intervention state
    private volatile boolean waitingForHuman = false;
    private volatile String humanRequestReason = "";
    private volatile CompletableFuture<String> humanHandbackFuture = null;

    // DOM callback
    private volatile CompletableFuture<String> pendingDomFuture = null;

    public interface BrowserListener {
        void onPageChanged(String url, String title, boolean loading, int progress);
        void onHumanInterventionRequested(String botId, String reason);
        void onHumanInterventionResolved(String botId);
    }

    private volatile BrowserListener listener;

    public void setListener(BrowserListener listener) {
        this.listener = listener;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public BotBrowserInstance(Context context, String botId) {
        this.context = context;
        this.botId = botId;
        this.webView = new WebView(context);

        // 1. Isolate cookies, localStorage, IndexedDB and cache per bot
        String profileName = "bot_" + botId.replaceAll("[^a-zA-Z0-9_]", "_");
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                WebViewCompat.setProfile(this.webView, profileName);
            } catch (Exception e) {
                android.util.Log.w("BotBrowser", "Multi-profile setup failed for " + botId + ": " + e.getMessage());
            }
        }

        // 2. Configure modern Chromium WebSettings
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(false);
        settings.setGeolocationEnabled(true);

        // Standard modern Android Chrome mobile user agent
        String customUa = "Mozilla/5.0 (Linux; Android 14; Mobile; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
        settings.setUserAgentString(customUa);

        // 3. Attach JavaScript Gateway Interface
        webView.addJavascriptInterface(new BotJsBridge(), "PocketBotBridge");

        // 4. WebViewClient for navigation lifecycle
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                isLoading = true;
                currentUrl = url != null ? url : "about:blank";
                lastError = "";
                notifyPageChanged();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isLoading = false;
                loadProgress = 100;
                if (url != null) currentUrl = url;
                String title = view.getTitle();
                if (title != null && !title.isEmpty()) currentTitle = title;
                notifyPageChanged();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    lastError = error != null ? error.getDescription().toString() : "Page load error";
                    notifyPageChanged();
                }
            }
        });

        // 5. WebChromeClient for title and progress
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                loadProgress = newProgress;
                if (newProgress >= 100) isLoading = false;
                notifyPageChanged();
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (title != null && !title.isEmpty()) {
                    currentTitle = title;
                    notifyPageChanged();
                }
            }
        });
    }

    public String getBotId() {
        return botId;
    }

    public WebView getWebView() {
        return webView;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    public int getLoadProgress() {
        return loadProgress;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isWaitingForHuman() {
        return waitingForHuman;
    }

    public String getHumanRequestReason() {
        return humanRequestReason;
    }

    private void notifyPageChanged() {
        BrowserListener l = listener;
        if (l != null) {
            mainHandler.post(() -> l.onPageChanged(currentUrl, currentTitle, isLoading, loadProgress));
        }
    }

    public void navigate(String url) {
        if (url == null || url.trim().isEmpty()) return;
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("about:")) {
            trimmed = "https://" + trimmed;
        }
        final String target = trimmed;
        mainHandler.post(() -> webView.loadUrl(target));
    }

    public void goBack() {
        mainHandler.post(() -> {
            if (webView.canGoBack()) webView.goBack();
        });
    }

    public void goForward() {
        mainHandler.post(() -> {
            if (webView.canGoForward()) webView.goForward();
        });
    }

    public void reload() {
        mainHandler.post(webView::reload);
    }

    public boolean canGoBack() {
        return webView.canGoBack();
    }

    public boolean canGoForward() {
        return webView.canGoForward();
    }

    // --- JavaScript Bridge Gateway ---
    private class BotJsBridge {
        @JavascriptInterface
        public void onDomReady(String json) {
            CompletableFuture<String> future = pendingDomFuture;
            if (future != null && !future.isDone()) {
                future.complete(json);
            }
        }
    }

    // --- OBSERVE: Extract interactive DOM tree with numeric IDs ---
    public CompletableFuture<String> extractInteractiveDom() {
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingDomFuture = future;

        String domIndexerJs =
            "(function() {" +
            "  try {" +
            "    const selectors = 'a, button, input, select, textarea, [role=\"button\"], [role=\"link\"], [role=\"checkbox\"], [role=\"menuitem\"], [onclick], [tabindex=\"0\"]';" +
            "    const elements = Array.from(document.querySelectorAll(selectors));" +
            "    const items = [];" +
            "    let index = 1;" +
            "    const winWidth = window.innerWidth || document.documentElement.clientWidth || 1080;" +
            "    const winHeight = window.innerHeight || document.documentElement.clientHeight || 1920;" +
            "    elements.forEach(el => {" +
            "      const rect = el.getBoundingClientRect();" +
            "      const style = window.getComputedStyle(el);" +
            "      if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return;" +
            "      if (rect.width > 2 && rect.height > 2 && rect.bottom > 0 && rect.top < winHeight + 1000) {" +
            "        el.setAttribute('data-bot-idx', index);" +
            "        const text = (el.innerText || el.value || el.placeholder || el.getAttribute('aria-label') || el.getAttribute('title') || el.alt || '').trim().slice(0, 120);" +
            "        items.push({" +
            "          id: index," +
            "          tag: el.tagName.toLowerCase()," +
            "          type: el.type || ''," +
            "          text: text," +
            "          href: el.href || ''," +
            "          x: Math.round(rect.left + rect.width / 2)," +
            "          y: Math.round(rect.top + rect.height / 2)," +
            "          width: Math.round(rect.width)," +
            "          height: Math.round(rect.height)" +
            "        });" +
            "        index++;" +
            "      }" +
            "    });" +
            "    const result = JSON.stringify({" +
            "      url: location.href," +
            "      title: document.title || ''," +
            "      totalElements: items.length," +
            "      elements: items.slice(0, 300)" +
            "    });" +
            "    window.PocketBotBridge.onDomReady(result);" +
            "  } catch (err) {" +
            "    window.PocketBotBridge.onDomReady(JSON.stringify({ url: location.href, error: String(err), elements: [] }));" +
            "  }" +
            "})();";

        mainHandler.post(() -> webView.evaluateJavascript(domIndexerJs, null));

        // Fallback timeout after 6 seconds if bridge doesn't respond
        mainHandler.postDelayed(() -> {
            if (!future.isDone()) {
                future.complete("{\"url\":\"" + currentUrl + "\",\"title\":\"" + currentTitle + "\",\"elements\":[]}");
            }
        }, 6000);

        return future;
    }

    // --- ACT: Click element by bot index ---
    public CompletableFuture<Boolean> clickElementById(int id) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        String js = String.format(
            "(function() {" +
            "  const el = document.querySelector('[data-bot-idx=\"%d\"]');" +
            "  if (el) {" +
            "    el.scrollIntoView({block: 'center', inline: 'center'});" +
            "    el.focus();" +
            "    el.click();" +
            "    return 'true';" +
            "  }" +
            "  return 'false';" +
            "})();", id);

        mainHandler.post(() -> webView.evaluateJavascript(js, result -> {
            boolean success = result != null && result.contains("true");
            future.complete(success);
        }));
        return future;
    }

    // --- ACT: Input text by bot index ---
    public CompletableFuture<Boolean> inputTextById(int id, String text, boolean clear) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        String safeText = text != null ? text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "") : "";
        String js = String.format(
            "(function() {" +
            "  const el = document.querySelector('[data-bot-idx=\"%d\"]');" +
            "  if (el) {" +
            "    el.scrollIntoView({block: 'center', inline: 'center'});" +
            "    el.focus();" +
            "    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value') ? " +
            "                   Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set : null;" +
            "    const val = %s ? '%s' : (el.value || '') + '%s';" +
            "    if (setter) { setter.call(el, val); } else { el.value = val; }" +
            "    el.dispatchEvent(new Event('input', { bubbles: true }));" +
            "    el.dispatchEvent(new Event('change', { bubbles: true }));" +
            "    return 'true';" +
            "  }" +
            "  return 'false';" +
            "})();", id, clear ? "true" : "false", safeText, safeText);

        mainHandler.post(() -> webView.evaluateJavascript(js, result -> {
            boolean success = result != null && result.contains("true");
            future.complete(success);
        }));
        return future;
    }

    // --- ACT: Tap coordinates directly on WebView memory buffer ---
    public void tapCoordinates(float x, float y) {
        mainHandler.post(() -> {
            long downTime = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
            MotionEvent up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0);
            webView.dispatchTouchEvent(down);
            webView.dispatchTouchEvent(up);
            down.recycle();
            up.recycle();
        });
    }

    // --- ACT: Scroll page or element ---
    public void scroll(int dx, int dy) {
        mainHandler.post(() -> webView.evaluateJavascript(String.format("window.scrollBy(%d, %d);", dx, dy), null));
    }

    // --- VISION: Capture off-screen snapshot directly from WebView buffer ---
    public CompletableFuture<String> captureScreenshotBase64(int targetWidth, int targetHeight) {
        CompletableFuture<String> future = new CompletableFuture<>();
        mainHandler.post(() -> {
            try {
                int width = webView.getWidth() > 0 ? webView.getWidth() : targetWidth > 0 ? targetWidth : 1080;
                int height = webView.getHeight() > 0 ? webView.getHeight() : targetHeight > 0 ? targetHeight : 1920;

                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                webView.draw(canvas);

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream);
                byte[] bytes = stream.toByteArray();
                bitmap.recycle();
                future.complete(Base64.encodeToString(bytes, Base64.NO_WRAP));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    // --- HUMAN HANDOFF ---
    public CompletableFuture<String> requestHumanIntervention(String reason) {
        this.waitingForHuman = true;
        this.humanRequestReason = reason != null ? reason : "Please complete this step in the browser and hand back to the bot.";
        this.humanHandbackFuture = new CompletableFuture<>();

        BrowserListener l = listener;
        if (l != null) {
            mainHandler.post(() -> l.onHumanInterventionRequested(botId, humanRequestReason));
        }

        return this.humanHandbackFuture;
    }

    public void completeHumanIntervention(String userNotes) {
        this.waitingForHuman = false;
        CompletableFuture<String> future = this.humanHandbackFuture;
        this.humanHandbackFuture = null;
        if (future != null && !future.isDone()) {
            future.complete(userNotes != null ? userNotes : "Human handoff completed");
        }

        BrowserListener l = listener;
        if (l != null) {
            mainHandler.post(() -> l.onHumanInterventionResolved(botId));
        }
    }

    public void destroy() {
        mainHandler.post(() -> {
            if (webView.getParent() != null) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.destroy();
        });
    }
}
