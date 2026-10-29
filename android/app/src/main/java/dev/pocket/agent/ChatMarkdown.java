package dev.pocket.agent;

import android.widget.TextView;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

/** Native Markdown spans; no WebView, scripts, or remote image loading. */
public final class ChatMarkdown {
    private final Markwon renderer;
    public ChatMarkdown(android.content.Context context){renderer=Markwon.builder(context).usePlugin(TablePlugin.create(context)).usePlugin(LinkifyPlugin.create(android.text.util.Linkify.WEB_URLS)).build();}
    public void set(TextView view,String source){view.setLinkTextColor(0xff89b4ff);renderer.setMarkdown(view,source);view.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());}
}
