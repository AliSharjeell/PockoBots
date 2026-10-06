package dev.pocket.agent;

import android.content.Context;
import android.os.Build;
import android.system.Os;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class RuntimeInstaller {
    interface Progress { void update(String message); }
    static synchronized File install(Context context, Progress progress) throws Exception {
        String abi = null;
        for (String candidate : Build.SUPPORTED_ABIS) {
            try (InputStream ignored = context.getAssets().open("runtime/" + candidate + ".json")) {
                abi = candidate; break;
            } catch (FileNotFoundException ignored) {}
        }
        if (abi == null) throw new IOException("This APK has no runtime for this device. Install the matching arm64 or x86_64 APK.");
        JSONObject metadata;
        try (InputStream in = context.getAssets().open("runtime/" + abi + ".json")) {
            metadata = new JSONObject(new String(Io.bytes(in), java.nio.charset.StandardCharsets.UTF_8));
        }
        String digest = metadata.getString("sha256");
        File root = new File(context.getFilesDir(), "runtimes/" + digest);
        File marker = new File(root, ".installed");
        if (marker.isFile()) return root;
        // Interrupted extraction is safely retried; projects and keys live elsewhere.
        root.mkdirs();
        progress.update("Unpacking the included coding environment…");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream raw = context.getAssets().open("runtime/" + abi + ".zip");
             ZipInputStream zip = new ZipInputStream(new java.security.DigestInputStream(raw, sha))) {
            ZipEntry entry;
            byte[] buffer = new byte[65536];
            int files = 0;
            while ((entry = zip.getNextEntry()) != null) {
                File dest = within(root, entry.getName());
                if (entry.isDirectory()) dest.mkdirs();
                else {
                    dest.getParentFile().mkdirs();
                    try (OutputStream out = new FileOutputStream(dest)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) out.write(buffer, 0, read);
                    }
                }
                if (++files % 1000 == 0) progress.update("Preparing tools… " + files + " files");
            }
            // ZipInputStream may stop before the archive's central directory.
            while (zip.read(buffer) != -1) {}
        }
        // Verify the complete compressed asset separately, including its central directory.
        sha.reset();
        try (InputStream raw = context.getAssets().open("runtime/" + abi + ".zip")) {
            byte[] buffer = new byte[65536]; int read;
            while ((read = raw.read(buffer)) != -1) sha.update(buffer, 0, read);
        }
        StringBuilder actual = new StringBuilder();
        for (byte b : sha.digest()) actual.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        if (!digest.equals(actual.toString())) throw new IOException("Included runtime failed its checksum check");
        JSONObject modes = new JSONObject(Io.text(new File(root, ".pocket-modes.json")));
        java.util.Iterator<String> names = modes.keys();
        while (names.hasNext()) {
            String name = names.next(); Os.chmod(within(root, name).getPath(), modes.getInt(name));
        }
        JSONArray links = new JSONArray(Io.text(new File(root, ".pocket-links.json")));
        for (int i = 0; i < links.length(); i++) {
            JSONObject link = links.getJSONObject(i);
            File path = within(root, link.getString("path"));
            path.getParentFile().mkdirs();
            // Check lexical destination; symlinks are created after all files.
            File target = new File(path.getParentFile(), link.getString("target"));
            if (!target.getCanonicalPath().startsWith(root.getCanonicalPath() + "/"))
                throw new IOException("Runtime link escapes its filesystem");
            if (!Files.isSymbolicLink(path.toPath())) Os.symlink(link.getString("target"), path.getPath());
        }
        Io.write(marker, digest);
        return root;
    }
    private static File within(File root, String name) throws IOException {
        File dest = new File(root, name);
        if (name.startsWith("/") || !dest.getCanonicalPath().startsWith(root.getCanonicalPath() + "/"))
            throw new IOException("Invalid runtime archive path");
        return dest;
    }
}
