package dev.pocket.agent;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Direct webapp preview with hot reload, separate from the accessibility agent. */
public final class BotPreviewActivity extends Activity {
    private WebView web;
    static boolean valid(String value){try{Uri uri=Uri.parse(value);return ("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))&&uri.getHost()!=null&&uri.getUserInfo()==null;}catch(Exception ignored){return false;}}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);web=new WebView(this);setContentView(web);
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !valid(request.getUrl().toString());}
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return !valid(url);}
        });
        if(saved==null)open(getIntent());else web.restoreState(saved);
    }
    private void open(Intent intent){String url=intent.getStringExtra("url");if(!valid(url)){finish();return;}if(url.equals(web.getUrl())){if(intent.getBooleanExtra("refresh",false))web.reload();}else web.loadUrl(url);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);open(intent);}
    @Override protected void onSaveInstanceState(Bundle state){web.saveState(state);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
