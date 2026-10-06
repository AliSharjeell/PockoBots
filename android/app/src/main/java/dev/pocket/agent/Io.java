package dev.pocket.agent;
import java.io.*;
import java.nio.charset.StandardCharsets;
final class Io {
    static byte[] bytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[65536]; int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toByteArray();
    }
    static String text(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) { return new String(bytes(in), StandardCharsets.UTF_8); }
    }
    static void write(File file, String value) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) { out.write(value.getBytes(StandardCharsets.UTF_8)); }
    }
}
