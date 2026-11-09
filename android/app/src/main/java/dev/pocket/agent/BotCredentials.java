package dev.pocket.agent;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * The logins the browser was given, kept in the app's private storage.
 *
 * A cookie session lives in the WebView's own profile and is written to disk by the browser. A
 * password the owner typed does not: the WebView has no password store of its own, so it is kept
 * here instead, per site, and put back into the page the next time it is asked for.
 *
 * Nothing here leaves the phone. The file lives in the app's private files directory, it is not
 * exported, and it is not written to the log.
 */
final class BotCredentials {
    /** One site and the login that belongs to it. */
    static final class Entry {
        final String username;
        final String password;

        Entry(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    private final File file;
    private final Map<String, Entry> entries = new HashMap<>();

    BotCredentials(Context context) {
        this.file = new File(context.getFilesDir(), "browser-credentials.json");
        load();
    }

    private synchronized void load() {
        entries.clear();
        try {
            if (!file.isFile()) return;
            JSONObject stored = new JSONObject(dev.pocket.agent.Io.text(file));
            JSONArray sites = stored.optJSONArray("sites");
            if (sites == null) return;
            for (int i = 0; i < sites.length(); i++) {
                JSONObject site = sites.optJSONObject(i);
                if (site == null) continue;
                String host = site.optString("host");
                if (host.isEmpty()) continue;
                entries.put(host, new Entry(site.optString("username"), site.optString("password")));
            }
        } catch (Exception ignored) {
            // A file that cannot be read is a file without logins, not a crash.
        }
    }

    /** The login of a site, or null when the site is unknown. */
    synchronized Entry find(String host) {
        if (host == null || host.isEmpty()) return null;
        Entry entry = entries.get(host);
        if (entry == null) return null;
        return entry.password.isEmpty() ? null : entry;
    }

    /** Remembers the login of a site. */
    synchronized void remember(String host, String username, String password) {
        if (host == null || host.isEmpty() || password == null || password.isEmpty()) return;
        entries.put(host, new Entry(username == null ? "" : username, password));
        save();
    }

    /** Forgets the login of a site. */
    synchronized void forget(String host) {
        if (host == null || entries.remove(host) != null) save();
    }

    private void save() {
        try {
            JSONArray sites = new JSONArray();
            for (Map.Entry<String, Entry> entry : entries.entrySet()) {
                JSONObject site = new JSONObject();
                site.put("host", entry.getKey());
                site.put("username", entry.getValue().username);
                site.put("password", entry.getValue().password);
                sites.put(site);
            }
            JSONObject stored = new JSONObject().put("sites", sites);
            dev.pocket.agent.Io.write(file, stored.toString());
        } catch (Exception ignored) {
            // A file that cannot be written means the login is remembered for this session only.
        }
    }
}
