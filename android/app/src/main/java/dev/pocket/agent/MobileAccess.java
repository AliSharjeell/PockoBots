package dev.pocket.agent;

import android.content.Context;
import com.artemis.helper.TokenStore;
import org.json.JSONTokener;
import java.io.File;

/** Consent exists only while the user-started mobile foreground controller is alive. */
public final class MobileAccess {
    private static volatile boolean authorized;
    public static boolean allowed() { return authorized; }
    static void authorize(boolean value) { authorized = value; }
    public static void syncToken(Context context) {
        try { TokenStore.set((String)new JSONTokener(Io.text(new File(context.getFilesDir(),"state/token.json"))).nextValue()); }
        catch(Exception ignored) { TokenStore.set(null); }
    }
}
