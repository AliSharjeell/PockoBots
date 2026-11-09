package dev.pocket.agent;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Isolated browser instance for a single bot.
 * Features:
 * - Multi-profile cookie and session isolation per bot (via AndroidX WebKit).
 * - Tabs, so a bot can look something up without losing the page it was filling in.
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
    /** The logins the owner typed, kept in the app's private storage. */
    private final BotCredentials credentials;

    public static final String DEFAULT_HOME_URL = "https://www.google.com";

    /**
     * The script that watches for a login and asks for a known one back.
     *
     * The WebView has no password store of its own, so a login the owner typed is reported to the
     * app and kept there, and a page that asks is given the login it had last time.
     */
    private static final String CREDENTIAL_SCRIPT =
            "(function(){try{" +
            "if(!window.__pockoCredentials){window.__pockoCredentials=true;" +
            "document.addEventListener('submit',function(event){try{" +
            "var form=event.target;if(!form||!form.querySelector)return;" +
            "var password=form.querySelector('input[type=\"password\"]');if(!password||!password.value)return;" +
            "var user=form.querySelector('input[type=\"email\"],input[type=\"text\"],input:not([type])');" +
            "window.PocketBotBridge.onCredentials(location.hostname,user?user.value:'',password.value);" +
            "}catch(e){}},true);}" +
            "try{var password=document.querySelector('input[type=\"password\"]');" +
            "if(password)window.PocketBotBridge.requestCredentials(location.hostname);}catch(e){}" +
            "}catch(e){}})();";

    /** Writes the browser's cookies to disk, so a session survives a restart. */
    static void flushCookies() {
        try {
            android.webkit.CookieManager.getInstance().flush();
        } catch (Throwable ignored) {
            // A browser that cannot write its cookies now writes them later.
        }
    }

    /** One tab of this browser: a page of its own, with its own state. */
    public static class Tab {
        public final WebView webView;
        public volatile String url = "about:blank";
        public volatile String title = "";
        public volatile int progress = 0;
        public volatile boolean loading = false;
        public volatile String lastError = "";
        public final AtomicLong navigationCount = new AtomicLong(0);
        volatile CompletableFuture<String> pendingDomFuture = null;

        Tab(WebView webView) {
            this.webView = webView;
        }
    }

    private final List<Tab> tabs = new ArrayList<>();
    private volatile int activeIndex = 0;

    // Human intervention state
    private volatile boolean waitingForHuman = false;
    private volatile String humanRequestReason = "";
    private volatile CompletableFuture<String> humanHandbackFuture = null;

    public interface BrowserListener {
        void onPageChanged(int tabIndex, String url, String title, boolean loading, int progress);
        void onTabsChanged(String botId, int activeIndex, int tabCount);
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
        this.credentials = new BotCredentials(context);
        createTab(DEFAULT_HOME_URL);
    }

    /** A tab of its own, with the same isolation and the same settings as the first one. */
    @SuppressLint("SetJavaScriptEnabled")
    private Tab createTab(String startUrl) {
        final Tab tab = new Tab(new WebView(context));
        WebView webView = tab.webView;

        // 1. Isolate cookies, localStorage, IndexedDB and cache per bot
        String profileName = "bot_" + botId.replaceAll("[^a-zA-Z0-9_]", "_");
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                WebViewCompat.setProfile(webView, profileName);
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
        webView.addJavascriptInterface(new BotJsBridge(tab), "PocketBotBridge");

        // 4. WebViewClient for navigation lifecycle
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                tab.loading = true;
                tab.url = url != null ? url : "about:blank";
                tab.lastError = "";
                notifyPageChanged(tab);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                tab.loading = false;
                tab.progress = 100;
                if (url != null) tab.url = url;
                String title = view.getTitle();
                if (title != null && !title.isEmpty()) tab.title = title;
                // The session of this page is written to disk now, so a restart does not lose it.
                flushCookies();
                view.evaluateJavascript(CREDENTIAL_SCRIPT, null);
                notifyPageChanged(tab);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    tab.lastError = error != null ? error.getDescription().toString() : "Page load error";
                    notifyPageChanged(tab);
                }
            }
        });

        // 5. WebChromeClient for title and progress
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                tab.progress = newProgress;
                if (newProgress >= 100) tab.loading = false;
                notifyPageChanged(tab);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (title != null && !title.isEmpty()) {
                    tab.title = title;
                    notifyPageChanged(tab);
                }
            }
        });

        tabs.add(tab);
        if (startUrl != null && !startUrl.isEmpty()) {
            tab.url = startUrl;
            mainHandler.post(() -> {
                tab.navigationCount.incrementAndGet();
                webView.loadUrl(startUrl);
            });
        }
        return tab;
    }

    public String getBotId() {
        return botId;
    }

    /** The tab that is in front. */
    public Tab getActiveTab() {
        List<Tab> all = tabs;
        if (all.isEmpty()) return null;
        int index = Math.max(0, Math.min(activeIndex, all.size() - 1));
        return all.get(index);
    }

    /** Every tab of this browser, in the order they were opened. */
    public List<Tab> getTabs() {
        return new ArrayList<>(tabs);
    }

    public int getTabCount() {
        return tabs.size();
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    public WebView getWebView() {
        Tab active = getActiveTab();
        return active == null ? null : active.webView;
    }

    public String getCurrentUrl() {
        Tab active = getActiveTab();
        return active == null ? "" : active.url;
    }

    public String getCurrentTitle() {
        Tab active = getActiveTab();
        return active == null ? "" : active.title;
    }

    public int getLoadProgress() {
        Tab active = getActiveTab();
        return active == null ? 0 : active.progress;
    }

    public boolean isLoading() {
        Tab active = getActiveTab();
        return active != null && active.loading;
    }

    public String getLastError() {
        Tab active = getActiveTab();
        return active == null ? "" : active.lastError;
    }

    public boolean isWaitingForHuman() {
        return waitingForHuman;
    }

    public String getHumanRequestReason() {
        return humanRequestReason;
    }

    private int indexOf(Tab tab) {
        List<Tab> all = tabs;
        for (int i = 0; i < all.size(); i++) if (all.get(i) == tab) return i;
        return -1;
    }

    private void notifyPageChanged(Tab tab) {
        BrowserListener l = listener;
        if (l == null) return;
        int index = indexOf(tab);
        mainHandler.post(() -> l.onPageChanged(index, tab.url, tab.title, tab.loading, tab.progress));
    }

    private void notifyTabsChanged() {
        BrowserListener l = listener;
        if (l == null) return;
        mainHandler.post(() -> l.onTabsChanged(botId, activeIndex, tabs.size()));
    }

    public static String resolveInputUrl(String input) {
        if (input == null || input.trim().isEmpty()) {
            return DEFAULT_HOME_URL;
        }
        String trimmed = input.trim();

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("about:") || trimmed.startsWith("file:")) {
            return trimmed;
        }

        boolean hasSpace = trimmed.contains(" ");
        boolean isLocalhost = trimmed.startsWith("localhost") || trimmed.startsWith("127.0.0.1");
        boolean hasDotDomain = trimmed.matches("^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+(:[0-9]+)?(/.*)?$");

        if (!hasSpace && (isLocalhost || hasDotDomain)) {
            return "https://" + trimmed;
        }

        try {
            return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8");
        } catch (Exception e) {
            return "https://www.google.com/search?q=" + trimmed.replace(" ", "+");
        }
    }

    public void navigate(String url) {
        final String target = resolveInputUrl(url);
        mainHandler.post(() -> {
            Tab active = getActiveTab();
            if (active == null) return;
            active.navigationCount.incrementAndGet();
            active.webView.loadUrl(target);
        });
    }

    /**
     * How many pages this browser has been asked for.
     *
     * A caller that waits for a load to finish has to know the load began. A WebView that has not
     * been asked for anything reports neither loading nor progress, so a wait on those alone calls
     * the page settled before it has started.
     */
    public long navigationCount() { return navigationCountOf(getActiveTab()); }

    /** How many pages one tab has been asked for. */
    public long navigationCountOf(Tab tab) { return tab == null ? 0 : tab.navigationCount.get(); }

    /**
     * Opens a tab of its own.
     *
     * The page in front is left as it is, so a form that is half filled in survives a lookup.
     */
    public Tab openTab(String url) {
        final String target = resolveInputUrl(url);
        // A tab is made on the main thread. A caller that is already on it gets the tab at once.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Tab tab = createTab(target);
            activeIndex = indexOf(tab);
            notifyTabsChanged();
            return tab;
        }
        Tab[] created = new Tab[1];
        mainHandler.post(() -> {
            Tab tab = createTab(target);
            created[0] = tab;
            activeIndex = indexOf(tab);
            notifyTabsChanged();
        });
        // The tab is made on the main thread, so a caller that needs it waits for the thread to be
        // free and reads it back.
        long deadline = System.currentTimeMillis() + 3000;
        while (created[0] == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return created[0];
    }

    /** Brings a tab to the front. */
    public boolean switchTab(int index) {
        if (index < 0 || index >= tabs.size()) return false;
        activeIndex = index;
        notifyTabsChanged();
        return true;
    }

    /**
     * Closes a tab.
     *
     * The last tab is never closed: a browser without a page has nothing to show.
     */
    public boolean closeTab(int index) {
        if (index < 0 || index >= tabs.size() || tabs.size() <= 1) return false;
        Tab tab = tabs.remove(index);
        if (index < activeIndex) activeIndex--;
        else if (index == activeIndex) activeIndex = Math.min(activeIndex, tabs.size() - 1);
        final Tab closed = tab;
        mainHandler.post(() -> {
            if (closed.webView.getParent() != null) {
                ((ViewGroup) closed.webView.getParent()).removeView(closed.webView);
            }
            closed.webView.destroy();
        });
        notifyTabsChanged();
        return true;
    }

    /** Every tab of this browser, as the bot is told about them. */
    public JSONArray tabSummaries() {
        JSONArray list = new JSONArray();
        List<Tab> all = tabs;
        for (int i = 0; i < all.size(); i++) {
            Tab tab = all.get(i);
            try {
                JSONObject entry = new JSONObject();
                entry.put("index", i);
                entry.put("url", tab.url);
                entry.put("title", tab.title);
                entry.put("active", i == activeIndex);
                entry.put("loading", tab.loading);
                list.put(entry);
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    public void goBack() {
        mainHandler.post(() -> {
            Tab active = getActiveTab();
            if (active != null && active.webView.canGoBack()) active.webView.goBack();
        });
    }

    public void goForward() {
        mainHandler.post(() -> {
            Tab active = getActiveTab();
            if (active != null && active.webView.canGoForward()) active.webView.goForward();
        });
    }

    public void reload() {
        mainHandler.post(() -> {
            Tab active = getActiveTab();
            if (active != null) active.webView.reload();
        });
    }

    public boolean canGoBack() {
        Tab active = getActiveTab();
        return active != null && active.webView.canGoBack();
    }

    public boolean canGoForward() {
        Tab active = getActiveTab();
        return active != null && active.webView.canGoForward();
    }

    // --- JavaScript Bridge Gateway ---
    private class BotJsBridge {
        private final Tab tab;

        BotJsBridge(Tab tab) {
            this.tab = tab;
        }

        @JavascriptInterface
        public void onDomReady(String json) {
            CompletableFuture<String> future = tab.pendingDomFuture;
            if (future != null && !future.isDone()) {
                future.complete(json);
            }
        }

        /** A login the owner typed on a page. It is kept in the app's private storage. */
        @JavascriptInterface
        public void onCredentials(String host, String username, String password) {
            if (host == null || host.isEmpty()) return;
            credentials.remember(host, username, password);
        }

        /** The page asks for the login of the site it is on. */
        @JavascriptInterface
        public void requestCredentials(String host) {
            BotCredentials.Entry entry = credentials.find(host);
            if (entry == null) return;
            String username = org.json.JSONObject.quote(entry.username);
            String password = org.json.JSONObject.quote(entry.password);
            String fill = "(function(){try{" +
                    "var password=document.querySelector('input[type=\"password\"]');if(!password)return;" +
                    "var user=document.querySelector('input[type=\"email\"],input[type=\"text\"],input:not([type])');" +
                    "if(user){var set=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;" +
                    "set.call(user," + username + ");user.dispatchEvent(new Event('input',{bubbles:true}));}" +
                    "var set2=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;" +
                    "set2.call(password," + password + ");password.dispatchEvent(new Event('input',{bubbles:true}));" +
                    "}catch(e){}})();";
            mainHandler.post(() -> tab.webView.evaluateJavascript(fill, null));
        }
    }

    // --- OBSERVE: Extract interactive DOM tree with numeric IDs ---
    public CompletableFuture<String> extractInteractiveDom() {
        final Tab active = getActiveTab();
        if (active == null) {
            CompletableFuture<String> empty = new CompletableFuture<>();
            empty.complete("{\"url\":\"\",\"title\":\"\",\"elements\":[]}");
            return empty;
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        active.pendingDomFuture = future;

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

        mainHandler.post(() -> active.webView.evaluateJavascript(domIndexerJs, null));

        // Fallback timeout after 6 seconds if bridge doesn't respond
        mainHandler.postDelayed(() -> {
            if (!future.isDone()) {
                future.complete("{\"url\":\"" + active.url + "\",\"title\":\"" + active.title + "\",\"elements\":[]}");
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

        mainHandler.post(() -> {
            Tab active = getActiveTab();
            if (active == null) {
                future.complete(false);
                return;
            }
            active.webView.evaluateJavascript(js, result -> {
                boolean success = result != null && result.contains("true");
                future.complete(success);
            });
        });
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
            "    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value') ?" +
            "                   Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set : null;" +
            "    const val = %s ? '%s' : (el.value || '') + '%s';" +
            "    if (setter) { setter.call(el, val); } else { el.value = val; }" +
            "    el.dispatchEvent(new Event('input', { bubbles: true }));" +
            "    el.dispatchEvent(new Event('change', { bubbles: true }));" +
            "    return 'true';" +
            "  }" +
            "  return 'false';" +
            "})();", id, clear ? "true" : "false", safeText, safeText);

        mainHandler.post(() -> {
            Tab active = getActiveTab();
            if (active == null) {
                future.complete(false);
                return;
            }
            active.webView.evaluateJavascript(js, result -> {
                boolean success = result != null && result.contains("true");
                future.complete(success);
            });
        });
        return future;
    }

    // --- ACT: Tap coordinates directly on WebView memory buffer ---
    public void tapCoordinates(float x, float y) {
        mainHandler.post(() -> {
            Tab active = getActiveTab();
            if (active == null) return;
            long downTime = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
            MotionEvent up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0);
            active.webView.dispatchTouchEvent(down);
            active.webView.dispatchTouchEvent(up);
            down.recycle();
            up.recycle();
        });
    }

    // --- ACT: Scroll page or element ---
    public void scroll(int dx, int dy) {
        mainHandler.post(() -> {
            Tab active = getActiveTab();
            if (active == null) return;
            active.webView.evaluateJavascript(String.format("window.scrollBy(%d, %d);", dx, dy), null);
        });
    }

    // --- VISION: Capture off-screen snapshot directly from WebView buffer ---
    public CompletableFuture<String> captureScreenshotBase64(int targetWidth, int targetHeight) {
        CompletableFuture<String> future = new CompletableFuture<>();
        mainHandler.post(() -> {
            try {
                Tab active = getActiveTab();
                if (active == null) {
                    future.completeExceptionally(new IllegalStateException("The browser has no page open."));
                    return;
                }
                int width = active.webView.getWidth() > 0 ? active.webView.getWidth() : targetWidth > 0 ? targetWidth : 1080;
                int height = active.webView.getHeight() > 0 ? active.webView.getHeight() : targetHeight > 0 ? targetHeight : 1920;

                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                active.webView.draw(canvas);

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
            for (Tab tab : new ArrayList<>(tabs)) {
                if (tab.webView.getParent() != null) {
                    ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
                }
                tab.webView.destroy();
            }
            tabs.clear();
        });
    }
}
