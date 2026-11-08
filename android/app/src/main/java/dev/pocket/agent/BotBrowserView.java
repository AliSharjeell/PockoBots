package dev.pocket.agent;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

/**
 * Interactive in-app browser view giving the user full live visibility and touch control
 * over a bot's isolated Chromium browser session.
 */
public class BotBrowserView extends LinearLayout {
    private final Context context;
    private final String botId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText urlInput;
    private ProgressBar progressBar;
    private FrameLayout webViewContainer;
    private LinearLayout handoffBanner;
    private TextView handoffText;
    private ImageButton backBtn, forwardBtn, reloadBtn;

    private BotBrowserInstance browser;
    private Runnable onHandbackCallback;

    public BotBrowserView(Context context, String botId) {
        super(context);
        this.context = context;
        this.botId = botId;
        setOrientation(VERTICAL);
        setBackgroundColor(AgentStyle.BACK);

        initViews();
        attachBrowser();
    }

    public void setOnHandbackCallback(Runnable callback) {
        this.onHandbackCallback = callback;
    }

    private int dp(int n) {
        return AgentStyle.dp(context, n);
    }

    private void initViews() {
        // --- 1. Browser Navigation Top Bar ---
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(6), dp(8), dp(6));

        backBtn = createNavButton("←", "Back", () -> {
            if (browser != null && browser.canGoBack()) browser.goBack();
        });
        topBar.addView(backBtn, new LayoutParams(dp(36), dp(36)));

        forwardBtn = createNavButton("→", "Forward", () -> {
            if (browser != null && browser.canGoForward()) browser.goForward();
        });
        topBar.addView(forwardBtn, new LayoutParams(dp(36), dp(36)));

        reloadBtn = createNavButton("↻", "Reload", () -> {
            if (browser != null) browser.reload();
        });
        topBar.addView(reloadBtn, new LayoutParams(dp(36), dp(36)));

        // URL input capsule
        LinearLayout urlCapsule = new LinearLayout(context);
        urlCapsule.setOrientation(HORIZONTAL);
        urlCapsule.setGravity(Gravity.CENTER_VERTICAL);
        urlCapsule.setPadding(dp(10), dp(4), dp(8), dp(4));
        urlCapsule.setBackground(AgentStyle.surface(context, AgentStyle.PANEL, 18, true));

        urlInput = new EditText(context);
        AgentStyle.input(urlInput);
        urlInput.setSingleLine(true);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setTextSize(13);
        urlInput.setTextColor(AgentStyle.TEXT);
        urlInput.setHintTextColor(AgentStyle.MUTED);
        urlInput.setHint("Enter URL or search...");
        urlInput.setBackgroundColor(Color.TRANSPARENT);
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                submitUrl();
                return true;
            }
            return false;
        });

        urlCapsule.addView(urlInput, new LayoutParams(0, -2, 1));

        Button goBtn = new Button(context);
        goBtn.setText("Go");
        goBtn.setTextSize(11);
        AgentStyle.button(goBtn, true);
        goBtn.setPadding(dp(10), 0, dp(10), 0);
        goBtn.setOnClickListener(v -> submitUrl());
        urlCapsule.addView(goBtn, new LayoutParams(-2, dp(30)));

        LayoutParams capsuleParams = new LayoutParams(0, -2, 1);
        capsuleParams.setMargins(dp(6), 0, dp(4), 0);
        topBar.addView(urlCapsule, capsuleParams);

        addView(topBar, new LayoutParams(-1, -2));

        // --- 2. Progress Bar ---
        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(AgentStyle.BLUE));
        progressBar.setVisibility(GONE);
        addView(progressBar, new LayoutParams(-1, dp(3)));

        // --- 3. Human Handoff Banner (Prominent when bot requests human control) ---
        handoffBanner = new LinearLayout(context);
        handoffBanner.setOrientation(HORIZONTAL);
        handoffBanner.setGravity(Gravity.CENTER_VERTICAL);
        handoffBanner.setPadding(dp(12), dp(8), dp(12), dp(8));
        handoffBanner.setBackgroundColor(0x33FF9500); // Amber tint
        handoffBanner.setVisibility(GONE);

        handoffText = new TextView(context);
        handoffText.setTextColor(AgentStyle.TEXT);
        handoffText.setTextSize(12);
        handoffText.setTypeface(AgentStyle.font(context), Typeface.BOLD);
        handoffText.setText("Bot paused: Solve CAPTCHA or log in below.");
        handoffBanner.addView(handoffText, new LayoutParams(0, -2, 1));

        Button handbackBtn = new Button(context);
        handbackBtn.setText("Hand back to bot ✓");
        handbackBtn.setTextSize(12);
        AgentStyle.button(handbackBtn, true);
        handbackBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF34C759)); // Green accent
        handbackBtn.setOnClickListener(v -> {
            if (browser != null) {
                browser.completeHumanIntervention("Completed by user in live browser");
            }
            handoffBanner.setVisibility(GONE);
            Toast.makeText(context, "Control handed back to bot!", Toast.LENGTH_SHORT).show();
            if (onHandbackCallback != null) onHandbackCallback.run();
        });
        handoffBanner.addView(handbackBtn, new LayoutParams(-2, dp(34)));

        addView(handoffBanner, new LayoutParams(-1, -2));

        // --- 4. WebView Container ---
        webViewContainer = new FrameLayout(context);
        webViewContainer.setBackgroundColor(0xFFFFFFFF); // Clean white webpage backdrop
        addView(webViewContainer, new LayoutParams(-1, 0, 1));
    }

    private ImageButton createNavButton(String glyph, String description, Runnable action) {
        ImageButton btn = new ImageButton(context);
        btn.setContentDescription(description);
        btn.setBackground(AgentStyle.surface(context, AgentStyle.PANEL, 18, true));
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        // Simple text-based glyph button for universal crispness
        TextView label = new TextView(context);
        label.setText(glyph);
        label.setTextColor(AgentStyle.TEXT);
        label.setTextSize(16);
        label.setGravity(Gravity.CENTER);

        FrameLayout wrapper = new FrameLayout(context);
        wrapper.addView(label, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        btn.setOnClickListener(v -> action.run());

        return btn;
    }

    private void submitUrl() {
        String input = urlInput.getText().toString().trim();
        if (input.isEmpty()) return;

        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);

        if (browser != null) {
            browser.navigate(input);
        }
    }

    private void attachBrowser() {
        browser = BotBrowserManager.get().getOrCreate(context, botId);
        if (browser == null) return;

        // Mount to visible container
        BotBrowserManager.get().attachToVisibleContainer(botId, webViewContainer);

        // Update initial state
        updateState();

        // Listen for live updates
        browser.setListener(new BotBrowserInstance.BrowserListener() {
            @Override
            public void onPageChanged(String url, String title, boolean loading, int progress) {
                mainHandler.post(() -> {
                    if (loading) {
                        progressBar.setVisibility(VISIBLE);
                        progressBar.setProgress(progress);
                    } else {
                        progressBar.setVisibility(GONE);
                    }

                    if (!urlInput.hasFocus()) {
                        urlInput.setText(url != null && !url.equals("about:blank") ? url : "");
                    }
                    updateNavButtons();
                });
            }

            @Override
            public void onHumanInterventionRequested(String bId, String reason) {
                mainHandler.post(() -> {
                    handoffText.setText("🤖 Bot request: " + (reason != null ? reason : "Complete step & hand back"));
                    handoffBanner.setVisibility(VISIBLE);
                });
            }

            @Override
            public void onHumanInterventionResolved(String bId) {
                mainHandler.post(() -> handoffBanner.setVisibility(GONE));
            }
        });
    }

    private void updateState() {
        if (browser == null) return;
        String url = browser.getCurrentUrl();
        urlInput.setText(url != null && !url.equals("about:blank") ? url : "");
        if (browser.isWaitingForHuman()) {
            handoffText.setText("🤖 Bot request: " + browser.getHumanRequestReason());
            handoffBanner.setVisibility(VISIBLE);
        } else {
            handoffBanner.setVisibility(GONE);
        }
        updateNavButtons();
    }

    private void updateNavButtons() {
        if (browser == null) return;
        backBtn.setAlpha(browser.canGoBack() ? 1.0f : 0.4f);
        forwardBtn.setAlpha(browser.canGoForward() ? 1.0f : 0.4f);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Return webview safely to hidden stage so Chromium keeps running
        BotBrowserManager.get().returnToHiddenStage(botId);
    }
}
