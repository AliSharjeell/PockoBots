package dev.pocket.agent;

import android.content.*;
import android.graphics.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import com.termux.terminal.*;
import com.termux.view.TerminalRenderer;
import java.nio.charset.StandardCharsets;

final class TerminalCanvas extends View {
    interface Input { void send(String value); void resize(int cols, int rows); }
    private final Input input;
    private final TerminalEmulator emulator;
    private final TerminalRenderer renderer;
    private final float cellWidth;
    private final int cellHeight;
    private int topRow;
    private float touchY;
    private boolean dragged;
    private long offset;
    TerminalCanvas(Context context, Input input) {
        super(context); this.input = input;
        setFocusable(true); setFocusableInTouchMode(true); setBackgroundColor(Color.BLACK);
        int size = Math.round(12 * getResources().getDisplayMetrics().scaledDensity);
        Paint paint = new Paint(); paint.setTypeface(Typeface.MONOSPACE); paint.setTextSize(size);
        cellWidth = paint.measureText("X"); cellHeight = (int)Math.ceil(paint.getFontSpacing());
        renderer = new TerminalRenderer(size, Typeface.MONOSPACE);
        emulator = new TerminalEmulator(new TerminalOutput() {
            @Override public void write(byte[] data, int start, int count) { input.send(new String(data, start, count, StandardCharsets.UTF_8)); }
            @Override public void titleChanged(String oldTitle, String title) {}
            @Override public void onCopyTextToClipboard(String text) { copy(text); }
            @Override public void onPasteTextFromClipboard() { paste(); }
            @Override public void onBell() { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); }
            @Override public void onColorsChanged() { invalidate(); }
        }, 80, 24, Math.round(cellWidth), cellHeight, 5000, null);
        setOnLongClickListener(v -> { copy(emulator.getScreen().getTranscriptText()); return true; });
    }
    void append(String base64, long start) {
        byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        int skip = (int)Math.min(bytes.length, Math.max(0, offset - start));
        if (start > offset && offset > 0) {
            byte[] warning = "\r\n[Some output was missed while disconnected]\r\n".getBytes(StandardCharsets.UTF_8);
            emulator.append(warning, warning.length);
        }
        if (skip < bytes.length) {
            byte[] remaining = java.util.Arrays.copyOfRange(bytes, skip, bytes.length);
            emulator.append(remaining, remaining.length);
        }
        offset = Math.max(offset, start + bytes.length); topRow = 0; invalidate();
    }
    int columns() { return emulator.mColumns; }
    int rows() { return emulator.mRows; }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        int cols = Math.max(10, Math.min(500, (int)(w / cellWidth)));
        int rows = Math.max(2, Math.min(200, h / cellHeight - 1));
        emulator.resize(cols, rows, Math.round(cellWidth), cellHeight);
        input.resize(cols, rows);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); renderer.render(emulator, canvas, topRow, -1, -1, -1, -1);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) { touchY = event.getY(); dragged = false; }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            int delta = (int)((touchY - event.getY()) / cellHeight);
            if (delta != 0) {
                topRow = Math.max(-emulator.getScreen().getActiveTranscriptRows(), Math.min(0, topRow + delta));
                touchY = event.getY(); dragged = true; invalidate();
            }
        }
        if (event.getAction() == MotionEvent.ACTION_UP && !dragged) performClick();
        return true;
    }
    @Override public boolean performClick() {
        super.performClick(); requestFocus();
        ((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        return true;
    }
    @Override public boolean onCheckIsTextEditor() { return true; }
    @Override public InputConnection onCreateInputConnection(EditorInfo out) {
        out.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        out.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NONE;
        return new BaseInputConnection(this, false) {
            @Override public boolean commitText(CharSequence text, int newCursorPosition) { input.send(text.toString()); return true; }
            @Override public boolean setComposingText(CharSequence text, int newCursorPosition) { return super.setComposingText(text, newCursorPosition); }
            @Override public boolean deleteSurroundingText(int before, int after) { for (int i = 0; i < Math.min(before, 100); i++) input.send("\u007f"); return true; }
            @Override public boolean sendKeyEvent(KeyEvent event) { if (event.getAction() == KeyEvent.ACTION_DOWN) return onKeyDown(event.getKeyCode(), event); return true; }
        };
    }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_BACK) return super.onKeyDown(code, event);
        int mods = (event.isCtrlPressed() ? KeyHandler.KEYMOD_CTRL : 0) | (event.isAltPressed() ? KeyHandler.KEYMOD_ALT : 0) | (event.isShiftPressed() ? KeyHandler.KEYMOD_SHIFT : 0);
        String sequence = KeyHandler.getCode(code, mods, emulator.isCursorKeysApplicationMode(), emulator.isKeypadApplicationMode());
        if (sequence != null) { input.send(sequence); return true; }
        int unicode = event.getUnicodeChar(event.isCtrlPressed() ? 0 : event.getMetaState());
        if (unicode != 0) {
            if (event.isCtrlPressed()) unicode &= 31;
            input.send((event.isAltPressed() ? "\u001b" : "") + new String(Character.toChars(unicode))); return true;
        }
        return super.onKeyDown(code, event);
    }
    void paste() {
        ClipboardManager clipboard = getContext().getSystemService(ClipboardManager.class);
        if (clipboard.hasPrimaryClip()) emulator.paste(clipboard.getPrimaryClip().getItemAt(0).coerceToText(getContext()).toString());
    }
    private void copy(String text) { getContext().getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Terminal", text)); }
}
