package dev.pocket.agent;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton manager coordinating all in-app isolated bot browsers.
 * Maintains the hidden background stage so WebViews stay active and rendering.
 */
public class BotBrowserManager {
    private static volatile BotBrowserManager instance;

    public static BotBrowserManager get() {
        if (instance == null) {
            synchronized (BotBrowserManager.class) {
                if (instance == null) {
                    instance = new BotBrowserManager();
                }
            }
        }
        return instance;
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, BotBrowserInstance> instances = new ConcurrentHashMap<>();
    private final List<BotBrowserInstance.BrowserListener> globalListeners = new CopyOnWriteArrayList<>();

    private volatile ViewGroup hiddenStage;
    private volatile Context appContext;

    private BotBrowserManager() {}

    public void init(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setHiddenStage(ViewGroup stage) {
        this.hiddenStage = stage;
        // Mount any existing detached webviews to the new stage
        if (stage != null) {
            mainHandler.post(() -> {
                for (BotBrowserInstance botBrowser : instances.values()) {
                    View wv = botBrowser.getWebView();
                    if (wv.getParent() == null) {
                        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(1080, 1920);
                        stage.addView(wv, lp);
                    }
                }
            });
        }
    }

    public void addListener(BotBrowserInstance.BrowserListener listener) {
        if (listener != null && !globalListeners.contains(listener)) {
            globalListeners.add(listener);
        }
    }

    public void removeListener(BotBrowserInstance.BrowserListener listener) {
        globalListeners.remove(listener);
    }

    public BotBrowserInstance getOrCreate(Context context, String botId) {
        if (botId == null || botId.isEmpty()) return null;
        if (appContext == null && context != null) {
            appContext = context.getApplicationContext();
        }

        BotBrowserInstance existing = instances.get(botId);
        if (existing != null) return existing;

        synchronized (instances) {
            existing = instances.get(botId);
            if (existing != null) return existing;

            Context targetContext = context != null ? context : appContext;
            if (targetContext == null) return null;

            BotBrowserInstance newInstance = new BotBrowserInstance(targetContext, botId);
            newInstance.setListener(new BotBrowserInstance.BrowserListener() {
                @Override
                public void onPageChanged(String url, String title, boolean loading, int progress) {
                    for (BotBrowserInstance.BrowserListener l : globalListeners) {
                        l.onPageChanged(url, title, loading, progress);
                    }
                }

                @Override
                public void onHumanInterventionRequested(String bId, String reason) {
                    for (BotBrowserInstance.BrowserListener l : globalListeners) {
                        l.onHumanInterventionRequested(bId, reason);
                    }
                }

                @Override
                public void onHumanInterventionResolved(String bId) {
                    for (BotBrowserInstance.BrowserListener l : globalListeners) {
                        l.onHumanInterventionResolved(bId);
                    }
                }
            });

            instances.put(botId, newInstance);

            // Mount to hidden stage if available
            mainHandler.post(() -> {
                if (hiddenStage != null && newInstance.getWebView().getParent() == null) {
                    ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(1080, 1920);
                    hiddenStage.addView(newInstance.getWebView(), lp);
                }
            });

            return newInstance;
        }
    }

    public BotBrowserInstance get(String botId) {
        if (botId == null) return null;
        return instances.get(botId);
    }

    public List<String> getActiveBotIds() {
        return new ArrayList<>(instances.keySet());
    }

    public void remove(String botId) {
        if (botId == null) return;
        BotBrowserInstance b = instances.remove(botId);
        if (b != null) {
            b.destroy();
        }
    }

    /**
     * Called when the user wants to take control or view the browser in a visible container.
     */
    public void attachToVisibleContainer(String botId, ViewGroup visibleContainer) {
        BotBrowserInstance b = instances.get(botId);
        if (b == null || visibleContainer == null) return;

        mainHandler.post(() -> {
            View wv = b.getWebView();
            if (wv.getParent() != null) {
                ((ViewGroup) wv.getParent()).removeView(wv);
            }
            visibleContainer.addView(wv, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));
            wv.setVisibility(View.VISIBLE);
            wv.requestFocus();
        });
    }

    /**
     * Moves the WebView back to the hidden stage when user leaves the browser view.
     */
    public void returnToHiddenStage(String botId) {
        BotBrowserInstance b = instances.get(botId);
        if (b == null) return;

        mainHandler.post(() -> {
            View wv = b.getWebView();
            if (wv.getParent() != null) {
                ((ViewGroup) wv.getParent()).removeView(wv);
            }
            if (hiddenStage != null) {
                ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(1080, 1920);
                hiddenStage.addView(wv, lp);
            }
        });
    }
}
