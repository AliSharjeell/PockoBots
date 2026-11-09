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
    /** The container each browser is shown in, so a new tab can be put in front of it. */
    private final Map<String, ViewGroup> visibleContainers = new ConcurrentHashMap<>();

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
                    mountHidden(botBrowser);
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
                public void onPageChanged(int tabIndex, String url, String title, boolean loading, int progress) {
                    for (BotBrowserInstance.BrowserListener l : globalListeners) {
                        l.onPageChanged(tabIndex, url, title, loading, progress);
                    }
                }

                @Override
                public void onTabsChanged(String bId, int activeIndex, int tabCount) {
                    for (BotBrowserInstance.BrowserListener l : globalListeners) {
                        l.onTabsChanged(bId, activeIndex, tabCount);
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
            mainHandler.post(() -> mountHidden(newInstance));

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
        visibleContainers.remove(botId);
        if (b != null) {
            b.destroy();
        }
    }

    /** Puts a tab of a browser on the hidden stage, so it keeps running while it is in the back. */
    private void mountHidden(BotBrowserInstance browser) {
        if (browser == null || hiddenStage == null) return;
        for (BotBrowserInstance.Tab tab : browser.getTabs()) {
            View wv = tab.webView;
            if (wv.getParent() == null) {
                hiddenStage.addView(wv, new ViewGroup.LayoutParams(1080, 1920));
            }
        }
    }

    /**
     * Called when the user wants to take control or view the browser in a visible container.
     */
    public void attachToVisibleContainer(String botId, ViewGroup visibleContainer) {
        BotBrowserInstance b = instances.get(botId);
        if (b == null || visibleContainer == null) return;
        visibleContainers.put(botId, visibleContainer);

        mainHandler.post(() -> {
            BotBrowserInstance browser = instances.get(botId);
            if (browser == null) return;
            // Only the tab in front is shown. The rest keep running on the hidden stage.
            for (BotBrowserInstance.Tab tab : browser.getTabs()) {
                if (tab.webView.getParent() != null) {
                    ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
                }
            }
            BotBrowserInstance.Tab active = browser.getActiveTab();
            if (active != null) {
                visibleContainer.addView(active.webView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ));
                active.webView.setVisibility(View.VISIBLE);
                active.webView.requestFocus();
            }
            mountHidden(browser);
        });
    }

    /**
     * Moves the WebView back to the hidden stage when user leaves the browser view.
     */
    public void returnToHiddenStage(String botId) {
        BotBrowserInstance b = instances.get(botId);
        if (b == null) return;
        visibleContainers.remove(botId);

        mainHandler.post(() -> {
            BotBrowserInstance browser = instances.get(botId);
            if (browser == null) return;
            mountHidden(browser);
        });
    }

    /**
     * Shows a tab of a browser in front.
     *
     * A tab that was opened while the browser was on screen takes the place of the one that was in
     * front, and the one it replaces goes back to the hidden stage.
     */
    public void showTab(String botId, int index) {
        BotBrowserInstance b = instances.get(botId);
        if (b == null) return;
        ViewGroup container = visibleContainers.get(botId);
        mainHandler.post(() -> {
            BotBrowserInstance browser = instances.get(botId);
            if (browser == null) return;
            if (!browser.switchTab(index)) return;
            if (container == null) {
                mountHidden(browser);
                return;
            }
            for (BotBrowserInstance.Tab tab : browser.getTabs()) {
                if (tab.webView.getParent() != null) {
                    ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
                }
            }
            BotBrowserInstance.Tab active = browser.getActiveTab();
            if (active != null) {
                container.addView(active.webView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ));
                active.webView.setVisibility(View.VISIBLE);
                active.webView.requestFocus();
            }
            mountHidden(browser);
        });
    }

    /**
     * Opens a tab of a browser and shows it.
     */
    public void openTab(String botId, String url) {
        BotBrowserInstance b = instances.get(botId);
        if (b == null) return;
        b.openTab(url);
        showTab(botId, b.getActiveIndex());
    }
}
