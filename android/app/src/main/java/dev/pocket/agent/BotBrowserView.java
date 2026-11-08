package dev.pocket.agent;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

/**
 * Modern in-app browser view styled like Safari/Arc/Chrome with default Google search,
 * SSL security badge, URL formatting, and tactile human-in-the-loop handoff.
 */
public class BotBrowserView extends LinearLayout {
    private final Context context;
    private final String botId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText urlInput;
    private ImageView securityIcon;
    private ImageView clearInputBtn;
    private ImageView reloadIcon;
    private ProgressBar progressBar;
    private FrameLayout webViewContainer;
    private LinearLayout handoffBanner;
    private TextView handoffReasonText;
    private ImageView backBtn, forwardBtn, homeBtn, externalBtn;

    private BotBrowserInstance browser;
    private Runnable onHandbackCallback;
    private boolean isEditingUrl = false;

    public BotBrowserView(Context context, String botId) {
        super(context);
        this.context = context;
        this.botId = (botId == null || botId.isEmpty()) ? "global" : botId;
        setOrientation(VERTICAL);
        // The browser is a card on the page, not a slab that runs to the edges of the screen.
        setBackground(AgentStyle.surface(context, AgentStyle.BACK, 22, true));
        setClipToOutline(true);

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
        // --- 1. Top Navigation & Address Bar Bar ---
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(6), dp(8), dp(6));

        // Back button
        backBtn = createToolbarIcon(R.drawable.lucide_arrow_left, "Back", () -> {
            if (browser != null && browser.canGoBack()) browser.goBack();
        });
        topBar.addView(backBtn, new LayoutParams(dp(36), dp(36)));

        // Forward button
        forwardBtn = createToolbarIcon(R.drawable.lucide_arrow_right, "Forward", () -> {
            if (browser != null && browser.canGoForward()) browser.goForward();
        });
        topBar.addView(forwardBtn, new LayoutParams(dp(36), dp(36)));

        // Address & Search Pill
        LinearLayout addressCapsule = new LinearLayout(context);
        addressCapsule.setOrientation(HORIZONTAL);
        addressCapsule.setGravity(Gravity.CENTER_VERTICAL);
        addressCapsule.setPadding(dp(10), 0, dp(8), 0);
        addressCapsule.setBackground(AgentStyle.surface(context, AgentStyle.PANEL, 20, true));

        // Security / Search indicator
        securityIcon = new ImageView(context);
        securityIcon.setImageResource(R.drawable.lucide_lock);
        securityIcon.setColorFilter(AgentStyle.MUTED);
        securityIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        addressCapsule.addView(securityIcon, new LayoutParams(dp(18), dp(18)));

        // Editable URL / Google Search query input
        urlInput = new EditText(context);
        AgentStyle.input(urlInput);
        urlInput.setSingleLine(true);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        urlInput.setTextSize(13);
        urlInput.setTextColor(AgentStyle.TEXT);
        urlInput.setHintTextColor(AgentStyle.MUTED);
        urlInput.setHint("Search Google or enter URL...");
        urlInput.setBackgroundColor(Color.TRANSPARENT);
        urlInput.setPadding(dp(8), dp(8), dp(4), dp(8));
        urlInput.setEllipsize(TextUtils.TruncateAt.END);

        urlInput.setOnFocusChangeListener((v, hasFocus) -> {
            isEditingUrl = hasFocus;
            if (hasFocus) {
                securityIcon.setImageResource(R.drawable.lucide_search);
                clearInputBtn.setVisibility(urlInput.getText().length() > 0 ? VISIBLE : GONE);
                reloadIcon.setVisibility(GONE);
                if (browser != null) {
                    String fullUrl = browser.getCurrentUrl();
                    if (fullUrl.equals(BotBrowserInstance.DEFAULT_HOME_URL)) {
                        urlInput.setText("");
                    } else if (!fullUrl.equals("about:blank")) {
                        urlInput.setText(fullUrl);
                        urlInput.selectAll();
                    }
                }
            } else {
                updateUrlDisplay();
                clearInputBtn.setVisibility(GONE);
                reloadIcon.setVisibility(VISIBLE);
            }
        });

        urlInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (isEditingUrl) {
                    clearInputBtn.setVisibility(s.length() > 0 ? VISIBLE : GONE);
                }
            }
            public void afterTextChanged(Editable s) {}
        });

        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                submitSearchOrUrl();
                return true;
            }
            return false;
        });

        addressCapsule.addView(urlInput, new LayoutParams(0, -1, 1));

        // Clear input icon (only visible when typing)
        clearInputBtn = new ImageView(context);
        clearInputBtn.setImageResource(R.drawable.lucide_x);
        clearInputBtn.setColorFilter(AgentStyle.MUTED);
        clearInputBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        clearInputBtn.setVisibility(GONE);
        clearInputBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        clearInputBtn.setOnClickListener(v -> urlInput.setText(""));
        addressCapsule.addView(clearInputBtn, new LayoutParams(dp(26), dp(26)));

        // Reload / Stop icon inside the capsule
        reloadIcon = new ImageView(context);
        reloadIcon.setImageResource(R.drawable.lucide_rotate_cw);
        reloadIcon.setColorFilter(AgentStyle.MUTED);
        reloadIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        reloadIcon.setPadding(dp(4), dp(4), dp(4), dp(4));
        reloadIcon.setOnClickListener(v -> {
            if (browser != null) browser.reload();
        });
        addressCapsule.addView(reloadIcon, new LayoutParams(dp(26), dp(26)));

        LayoutParams capsuleParams = new LayoutParams(0, dp(40), 1);
        capsuleParams.setMargins(dp(6), 0, dp(6), 0);
        topBar.addView(addressCapsule, capsuleParams);

        // Home button (Google home)
        homeBtn = createToolbarIcon(R.drawable.lucide_house, "Google Home", () -> {
            if (browser != null) browser.navigate(BotBrowserInstance.DEFAULT_HOME_URL);
        });
        topBar.addView(homeBtn, new LayoutParams(dp(36), dp(36)));

        // Open in external Chrome
        externalBtn = createToolbarIcon(R.drawable.lucide_external_link, "Open in Chrome", () -> {
            if (browser == null) return;
            try {
                String target = browser.getCurrentUrl();
                if (target.isEmpty() || target.equals("about:blank")) target = BotBrowserInstance.DEFAULT_HOME_URL;
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
                intent.setPackage("com.android.chrome");
                try {
                    context.startActivity(intent);
                } catch (Exception notChrome) {
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target)));
                }
            } catch (Exception e) {
                Toast.makeText(context, "Could not open external link", Toast.LENGTH_SHORT).show();
            }
        });
        topBar.addView(externalBtn, new LayoutParams(dp(36), dp(36)));

        addView(topBar, new LayoutParams(-1, -2));

        // Subtle divider
        View divider = new View(context);
        divider.setBackgroundColor(AgentStyle.BORDER);
        addView(divider, new LayoutParams(-1, dp(1)));

        // --- 2. Vibrant Google Blue Progress Bar ---
        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(0xFF1A73E8)); // Modern Google Blue
        progressBar.setVisibility(GONE);
        addView(progressBar, new LayoutParams(-1, dp(2)));

        // --- 3. Premium Human Handoff Banner ---
        handoffBanner = new LinearLayout(context);
        handoffBanner.setOrientation(HORIZONTAL);
        handoffBanner.setGravity(Gravity.CENTER_VERTICAL);
        handoffBanner.setPadding(dp(14), dp(10), dp(14), dp(10));
        handoffBanner.setBackground(AgentStyle.surface(context, 0x24FF9500, 16, true));
        LayoutParams bannerParams = new LayoutParams(-1, -2);
        bannerParams.setMargins(dp(8), dp(6), dp(8), dp(6));
        handoffBanner.setLayoutParams(bannerParams);
        handoffBanner.setVisibility(GONE);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(VERTICAL);

        TextView bannerTitle = new TextView(context);
        bannerTitle.setTextColor(0xFFFF9500);
        bannerTitle.setTextSize(12);
        bannerTitle.setTypeface(AgentStyle.font(context), Typeface.BOLD);
        bannerTitle.setText("⚠️ BOT PAUSED · HUMAN ACTION NEEDED");
        textCol.addView(bannerTitle);

        handoffReasonText = new TextView(context);
        handoffReasonText.setTextColor(AgentStyle.TEXT);
        handoffReasonText.setTextSize(12);
        handoffReasonText.setText("Please solve the CAPTCHA or log in below.");
        textCol.addView(handoffReasonText);

        handoffBanner.addView(textCol, new LayoutParams(0, -2, 1));

        Button handbackBtn = new Button(context);
        handbackBtn.setText("Hand back to bot ✓");
        handbackBtn.setTextSize(12);
        handbackBtn.setTypeface(AgentStyle.font(context), Typeface.BOLD);
        AgentStyle.button(handbackBtn, true);
        handbackBtn.setBackgroundTintList(ColorStateList.valueOf(0xFF34C759)); // Apple/Google vibrant green
        handbackBtn.setPadding(dp(14), 0, dp(14), 0);
        handbackBtn.setOnClickListener(v -> {
            if (browser != null) {
                browser.completeHumanIntervention("Completed by user in live browser");
            }
            animateBannerOut();
            Toast.makeText(context, "Control handed back to bot!", Toast.LENGTH_SHORT).show();
            if (onHandbackCallback != null) onHandbackCallback.run();
        });
        handoffBanner.addView(handbackBtn, new LayoutParams(-2, dp(36)));

        addView(handoffBanner);

        // --- 4. WebView Container ---
        webViewContainer = new FrameLayout(context);
        webViewContainer.setBackgroundColor(0xFFFFFFFF);
        addView(webViewContainer, new LayoutParams(-1, 0, 1));
    }

    private ImageView createToolbarIcon(int iconRes, String description, Runnable action) {
        ImageView img = new ImageView(context);
        img.setImageResource(iconRes);
        img.setColorFilter(AgentStyle.TEXT);
        img.setContentDescription(description);
        img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        img.setBackground(AgentStyle.surface(context, AgentStyle.PANEL, 18, true));
        img.setPadding(dp(7), dp(7), dp(7), dp(7));
        img.setOnClickListener(v -> action.run());
        return img;
    }

    private void animateBannerOut() {
        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(200);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            public void onAnimationStart(Animation a) {}
            public void onAnimationRepeat(Animation a) {}
            public void onAnimationEnd(Animation a) {
                handoffBanner.setVisibility(GONE);
            }
        });
        handoffBanner.startAnimation(fadeOut);
    }

    private void submitSearchOrUrl() {
        String input = urlInput.getText().toString().trim();
        urlInput.clearFocus();

        // Dismiss soft keyboard
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

        // Sync initial state
        updateState();

        // Listen for live page updates
        browser.setListener(new BotBrowserInstance.BrowserListener() {
            @Override
            public void onPageChanged(String url, String title, boolean loading, int progress) {
                mainHandler.post(() -> {
                    if (loading) {
                        progressBar.setVisibility(VISIBLE);
                        progressBar.setProgress(Math.max(5, progress));
                    } else {
                        progressBar.setVisibility(GONE);
                    }

                    if (!isEditingUrl) {
                        updateUrlDisplay();
                    }
                    updateNavButtons();
                });
            }

            @Override
            public void onHumanInterventionRequested(String bId, String reason) {
                mainHandler.post(() -> {
                    handoffReasonText.setText(reason != null ? reason : "Complete this step and hand back to bot.");
                    handoffBanner.setVisibility(VISIBLE);
                });
            }

            @Override
            public void onHumanInterventionResolved(String bId) {
                mainHandler.post(() -> handoffBanner.setVisibility(GONE));
            }
        });
    }

    private void updateUrlDisplay() {
        if (browser == null || isEditingUrl) return;
        String url = browser.getCurrentUrl();

        if (url.startsWith("https://")) {
            securityIcon.setImageResource(R.drawable.lucide_lock);
            securityIcon.setColorFilter(0xFF34C759); // Green lock for HTTPS
        } else {
            securityIcon.setImageResource(R.drawable.lucide_search);
            securityIcon.setColorFilter(AgentStyle.MUTED);
        }

        if (url.equals(BotBrowserInstance.DEFAULT_HOME_URL) || url.equals("https://www.google.com/")) {
            urlInput.setText("Google");
        } else if (url.startsWith("https://www.google.com/search?q=")) {
            String query = url.substring("https://www.google.com/search?q=".length());
            int amp = query.indexOf('&');
            if (amp > 0) query = query.substring(0, amp);
            try {
                query = java.net.URLDecoder.decode(query, "UTF-8");
            } catch (Exception ignored) {}
            urlInput.setText(query);
            securityIcon.setImageResource(R.drawable.lucide_search);
            securityIcon.setColorFilter(AgentStyle.MUTED);
        } else if (!url.isEmpty() && !url.equals("about:blank")) {
            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                String path = uri.getPath();
                if (host != null) {
                    urlInput.setText(host + (path != null && !path.equals("/") ? path : ""));
                } else {
                    urlInput.setText(url);
                }
            } catch (Exception e) {
                urlInput.setText(url);
            }
        } else {
            urlInput.setText("");
        }
    }

    private void updateState() {
        if (browser == null) return;
        updateUrlDisplay();

        if (browser.isWaitingForHuman()) {
            handoffReasonText.setText(browser.getHumanRequestReason());
            handoffBanner.setVisibility(VISIBLE);
        } else {
            handoffBanner.setVisibility(GONE);
        }
        updateNavButtons();
    }

    private void updateNavButtons() {
        if (browser == null) return;
        boolean canBack = browser.canGoBack();
        boolean canForward = browser.canGoForward();

        backBtn.setAlpha(canBack ? 1.0f : 0.35f);
        backBtn.setEnabled(canBack);

        forwardBtn.setAlpha(canForward ? 1.0f : 0.35f);
        forwardBtn.setEnabled(canForward);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        BotBrowserManager.get().returnToHiddenStage(botId);
    }
}
