package dev.pocket.agent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND";
    private WebView web;
    private int port = 8787;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        port = getSharedPreferences("connection", MODE_PRIVATE).getInt("port", 8787);
        try {
            String token = SecretStore.read(this);
            if (!token.isEmpty()) { openAgent(token); return; }
        } catch (Exception ignored) { SecretStore.clear(this); }
        connectionScreen();
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("Backend").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }
    @Override public boolean onOptionsItemSelected(MenuItem item) { connectionScreen(); return true; }

    private TextView text(String value, int size) {
        TextView label = new TextView(this);
        label.setText(value); label.setTextColor(Color.rgb(230, 235, 223)); label.setTextSize(size);
        label.setPadding(0, 16, 0, 16); return label;
    }
    private Button button(String value, LinearLayout layout, View.OnClickListener listener) {
        Button button = new Button(this); button.setText(value); layout.addView(button); button.setOnClickListener(listener); return button;
    }
    private void connectionScreen() {
        if (web != null) { web.destroy(); web = null; }
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(32, 24, 32, 24);
        layout.setBackgroundColor(Color.rgb(17, 19, 21));
        layout.addView(text("Your coding agent, on your phone.", 24));
        layout.addView(text("Install the backend in Termux first. Run bash scripts/pair.sh and paste its token below. Backend must be running to connect.", 14));
        layout.addView(text("Local backend port", 12));
        EditText portField = new EditText(this); portField.setInputType(InputType.TYPE_CLASS_NUMBER); portField.setText(String.valueOf(port)); layout.addView(portField);
        layout.addView(text("Pairing token", 12));
        EditText tokenField = new EditText(this); tokenField.setSingleLine(true); tokenField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenField.setHint("Paste your Termux pairing token"); layout.addView(tokenField);
        try { tokenField.setText(SecretStore.read(this)); } catch (Exception ignored) {}
        button("Connect", layout, v -> {
            try {
                int chosen = Integer.parseInt(portField.getText().toString());
                String token = tokenField.getText().toString().trim();
                if (chosen < 1024 || chosen > 65535 || !token.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Enter a port and the 64-character token from Termux");
                port = chosen; SecretStore.save(this, token);
                getSharedPreferences("connection", MODE_PRIVATE).edit().putInt("port", port).apply(); openAgent(token);
            } catch (Exception e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
        });
        button("Start backend in Termux", layout, v -> startTermux());
        button("Open Termux", layout, v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.termux");
            if (intent != null) startActivity(intent); else Toast.makeText(this, "Install Termux from F-Droid or its official GitHub releases", Toast.LENGTH_LONG).show();
        });
        button("Forget connection", layout, v -> { SecretStore.clear(this); tokenField.setText(""); CookieManager.getInstance().removeAllCookies(null); android.webkit.WebStorage.getInstance().deleteAllData(); });
        layout.addView(text("The backend runs inside Termux, even when this app is closed. To stop it, use pocket-agent-stop in Termux. Android may stop background processes; allow unrestricted battery use for Termux.", 12));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(layout); setContentView(scroll);
    }
    private void startTermux() {
        if (checkSelfPermission(TERMUX_PERMISSION) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{TERMUX_PERMISSION}, 42); return; }
        try {
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"/data/data/com.termux/files/home/.local/bin/pocket-agent-start"});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            startService(intent); Toast.makeText(this, "Start requested. Connect after the backend starts; check Termux if it fails.", Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, "Cannot start backend: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 42 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startTermux();
    }
    private boolean sameOrigin(Uri uri) {
        return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && uri.getPort() == port;
    }
    private void external(Uri uri) {
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception e) { Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show(); }
    }
    private void openAgent(String token) {
        web = new WebView(this); web.setBackgroundColor(Color.rgb(17, 19, 21));
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.getSettings().setSupportMultipleWindows(true); web.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return true;
                if (sameOrigin(request.getUrl())) return false;
                external(request.getUrl()); return true;
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) new AlertDialog.Builder(MainActivity.this).setTitle("Backend unavailable")
                    .setMessage("Start Pocket Agent in Termux, then reconnect. " + error.getDescription())
                    .setPositiveButton("Connection settings", (dialog, which) -> connectionScreen()).setNegativeButton("Retry", (dialog, which) -> view.reload()).show();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("Confirm", (d, w) -> result.confirm())
                    .setNegativeButton("Cancel", (d, w) -> result.cancel()).setOnCancelListener(d -> result.cancel()).show(); return true;
            }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean userGesture, Message resultMsg) {
                if (!userGesture) return false;
                WebView popup = new WebView(MainActivity.this);
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) { external(request.getUrl()); v.destroy(); return true; }
                });
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(popup); resultMsg.sendToTarget(); return true;
            }
        });
        setContentView(web); web.loadUrl("http://127.0.0.1:" + port + "/#token=" + Uri.encode(token));
    }
    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else if (web != null) connectionScreen(); else super.onBackPressed(); }
    @Override public void onDestroy() { if(web != null)web.destroy(); super.onDestroy(); }
}
