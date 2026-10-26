package com.petarsstuff.win95notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Classic Windows 95 styled plain-text editor. Zero third-party dependencies. */
public class MainActivity extends Activity {
    private static final int OPEN_REQUEST = 101;
    private static final int SAVE_AS_REQUEST = 102;
    private static final int SILVER = Color.rgb(192,192,192);
    private static final int DARK = Color.rgb(128,128,128);
    private static final int NAVY = Color.rgb(0,0,128);
    private static final int WHITE = Color.WHITE;

    private static final class PlainTextEditText extends EditText {
        PlainTextEditText(Context context) { super(context); }

        @Override public boolean onTextContextMenuItem(int id) {
            // This also handles the app's Edit > Paste action. It strips rich clipboard styling.
            if (id == android.R.id.paste) id = android.R.id.pasteAsPlainText;
            return super.onTextContextMenuItem(id);
        }
    }

    private EditText editor;
    private TextView title, status;
    private Uri currentUri;
    private String fileName = "Untitled";
    private boolean dirty = false, suppressChange = false, wordWrap = true;
    private String query = "";
    private PopupWindow activeMenu;
    private SharedPreferences prefs;

    private int dp(float x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private GradientDrawable panel(int color, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setStroke(dp(1), stroke); return d;
    }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL); v.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        return v;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("notepad95", MODE_PRIVATE);
        fileName = prefs.getString("name", "Untitled");
        wordWrap = prefs.getBoolean("wrap", true);
        String uri = prefs.getString("uri", "");
        if (!uri.isEmpty()) currentUri = Uri.parse(uri);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(SILVER);
        root.setPadding(dp(3), dp(3), dp(3), dp(3));
        setContentView(root);
        // Preserve room for system bars on current Android versions.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(dp(3) + bars.left, dp(3) + bars.top, dp(3) + bars.right, dp(3) + bars.bottom);
            return insets;
        });

        LinearLayout titleBar = new LinearLayout(this); titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackgroundColor(NAVY); titleBar.setPadding(dp(7), dp(4), dp(5), dp(4));
        TextView icon = text("N", 15, WHITE, false);
        titleBar.addView(icon, new LinearLayout.LayoutParams(dp(25), dp(30)));
        title = text("", 15, WHITE, false);
        titleBar.addView(title, new LinearLayout.LayoutParams(0, dp(30), 1));
        TextView fauxWindow = text("[_] [X]", 13, WHITE, false);
        titleBar.addView(fauxWindow);
        root.addView(titleBar);

        LinearLayout menubar = new LinearLayout(this); menubar.setGravity(Gravity.CENTER_VERTICAL);
        menubar.setPadding(dp(3), dp(2), dp(3), dp(3));
        addMenu(menubar, "File", new String[]{"New", "Open...", "Save", "Save As...", "──────", "Exit"},
                new Runnable[]{this::newDocument, this::openDocument, this::saveDocument, this::saveAsDocument, null, this::finish});
        addMenu(menubar, "Edit", new String[]{"Undo", "──────", "Cut", "Copy", "Paste", "Delete", "──────", "Select All", "Word Wrap"},
                new Runnable[]{() -> { Toast.makeText(this, "Use your keyboard's undo command where available", Toast.LENGTH_SHORT).show(); }, null,
                        () -> editAction(android.R.id.cut), () -> editAction(android.R.id.copy), () -> editAction(android.R.id.paste),
                        this::deleteSelection, null, () -> editor.selectAll(), this::toggleWrap});
        addMenu(menubar, "Search", new String[]{"Find...", "Find Next"}, new Runnable[]{this::findDialog, this::findNext});
        addMenu(menubar, "Help", new String[]{"About Notepad 95"}, new Runnable[]{this::showAbout});
        root.addView(menubar);

        FrameLayout editorFrame = new FrameLayout(this);
        editorFrame.setBackground(panel(WHITE, DARK));
        LinearLayout.LayoutParams eParams = new LinearLayout.LayoutParams(-1, 0, 1);
        eParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        root.addView(editorFrame, eParams);
        editor = new PlainTextEditText(this);
        editor.setTextColor(Color.BLACK); editor.setTextSize(16);
        editor.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL); editor.setBackgroundColor(WHITE);
        editor.setLetterSpacing(0f);
        editor.setLineSpacing(0f, 1f);
        editor.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        editor.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        editor.setGravity(Gravity.TOP | Gravity.START); editor.setPadding(dp(7), dp(7), dp(7), dp(7));
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setSingleLine(false); editor.setHorizontallyScrolling(!wordWrap);
        editor.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        editorFrame.addView(editor, new FrameLayout.LayoutParams(-1, -1));

        status = text("", 13, Color.BLACK, false);
        status.setBackground(panel(SILVER, DARK));
        status.setPadding(dp(7), dp(4), dp(7), dp(4));
        LinearLayout.LayoutParams sParams = new LinearLayout.LayoutParams(-1, dp(31));
        sParams.setMargins(dp(2), dp(2), dp(2), dp(2)); root.addView(status, sParams);
        editor.setText(prefs.getString("draft", ""));
        editor.setSelection(editor.length());
        dirty = prefs.getBoolean("dirty", false);
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!suppressChange) dirty = true;
                updateChrome();
            }
            @Override public void afterTextChanged(Editable e) {
                // Android normally keeps font, size and colour spans from rich clipboard data.
                // Strip any display formatting while preserving active IME composition spans.
                for (CharacterStyle span : e.getSpans(0, e.length(), CharacterStyle.class)) {
                    if ((e.getSpanFlags(span) & Spanned.SPAN_COMPOSING) == 0) e.removeSpan(span);
                }
                for (ParagraphStyle span : e.getSpans(0, e.length(), ParagraphStyle.class)) {
                    if ((e.getSpanFlags(span) & Spanned.SPAN_COMPOSING) == 0) e.removeSpan(span);
                }
            }
        });
        editor.setOnClickListener(v -> updateChrome());
        editor.setOnKeyListener((v, code, event) -> { if (event.getAction() == KeyEvent.ACTION_UP) updateChrome(); return false; });
        updateChrome();
    }

    private void addMenu(LinearLayout bar, String name, String[] items, Runnable[] actions) {
        TextView b = text(name, 14, Color.BLACK, false);
        b.setPadding(dp(11), dp(6), dp(11), dp(6));
        bar.addView(b, new LinearLayout.LayoutParams(-2, dp(40)));
        b.setOnClickListener(v -> popup(b, items, actions));
    }
    private void popup(View anchor, String[] items, Runnable[] actions) {
        if (activeMenu != null) { activeMenu.dismiss(); activeMenu = null; }
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(3), dp(3), dp(3), dp(3)); list.setBackground(panel(SILVER, DARK));
        for (int i = 0; i < items.length; i++) {
            if (actions[i] == null) {
                View divider = new View(this); divider.setBackgroundColor(DARK);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1)); p.setMargins(dp(7), dp(5), dp(7), dp(5));
                list.addView(divider, p); continue;
            }
            final Runnable action = actions[i];
            String label = items[i].equals("Word Wrap") ? (wordWrap ? "[x] Word Wrap" : "[ ] Word Wrap") : items[i];
            TextView item = text(label, 14, Color.BLACK, false);
            item.setPadding(dp(20), dp(9), dp(20), dp(9));
            list.addView(item, new LinearLayout.LayoutParams(-1, dp(42)));
            item.setOnClickListener(v -> { if (activeMenu != null) activeMenu.dismiss(); activeMenu = null; action.run(); });
        }
        activeMenu = new PopupWindow(list, dp(210), -2, true);
        activeMenu.setBackgroundDrawable(panel(SILVER, Color.BLACK));
        activeMenu.setOutsideTouchable(true); activeMenu.setElevation(dp(6));
        activeMenu.showAsDropDown(anchor, 0, -dp(2));
    }

    private void updateChrome() {
        if (title == null || editor == null) return;
        title.setText((dirty ? "*" : "") + fileName + " - Notepad");
        String s = editor.getText().toString(); int pos = Math.min(editor.getSelectionStart(), s.length());
        if (pos < 0) pos = 0;
        int line = 1, col = 1;
        for (int i = 0; i < pos; i++) { if (s.charAt(i) == '\n') { line++; col = 1; } else col++; }
        status.setText("Ln " + line + ", Col " + col + "     |     " + s.length() + " chars     |     UTF-8");
    }
    private void replaceText(String value) {
        suppressChange = true;
        editor.setText(value); editor.setSelection(0);
        suppressChange = false; dirty = false; updateChrome();
    }
    private void confirmDiscard(Runnable action) {
        if (!dirty) { action.run(); return; }
        new AlertDialog.Builder(this).setTitle("Notepad")
                .setMessage("Discard changes to " + fileName + "?")
                .setPositiveButton("Discard", (d, w) -> action.run())
                .setNegativeButton("Cancel", null).show();
    }
    private void newDocument() { confirmDiscard(() -> { currentUri = null; fileName = "Untitled"; replaceText(""); }); }
    private void openDocument() {
        confirmDiscard(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("text/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { startActivityForResult(intent, OPEN_REQUEST); }
            catch (ActivityNotFoundException ex) { toast("No file picker found"); }
        });
    }
    private void saveDocument() { if (currentUri == null) saveAsDocument(); else writeCurrent(); }
    private void saveAsDocument() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/plain"); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, fileName.equals("Untitled") ? "Untitled.txt" : fileName);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, SAVE_AS_REQUEST); }
        catch (ActivityNotFoundException ex) { toast("No file picker found"); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) {}
        if (request == OPEN_REQUEST) {
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null) throw new IllegalStateException("Cannot read file");
                ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
                while ((n = stream.read(buf)) != -1) out.write(buf, 0, n);
                String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
                if (content.startsWith("\uFEFF")) content = content.substring(1);
                currentUri = uri; fileName = displayName(uri); replaceText(content);
            } catch (Exception e) { toast("Open failed: " + e.getMessage()); }
        } else if (request == SAVE_AS_REQUEST) {
            currentUri = uri; fileName = displayName(uri); writeCurrent();
        }
    }
    private String displayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        String seg = uri.getLastPathSegment(); return seg == null ? "Document.txt" : seg;
    }
    private void writeCurrent() {
        if (currentUri == null) { saveAsDocument(); return; }
        try (OutputStream output = getContentResolver().openOutputStream(currentUri, "wt")) {
            if (output == null) throw new IllegalStateException("Cannot write file");
            output.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8)); output.flush();
            dirty = false; updateChrome(); saveDraft(); toast("Saved " + fileName);
        } catch (Exception e) { toast("Save failed: " + e.getMessage()); }
    }
    private void editAction(int id) { editor.requestFocus(); editor.onTextContextMenuItem(id); updateChrome(); }
    private void deleteSelection() {
        int a = Math.max(0, editor.getSelectionStart()), b = Math.max(0, editor.getSelectionEnd());
        if (a != b) editor.getText().delete(Math.min(a,b), Math.max(a,b));
    }
    private void toggleWrap() { wordWrap = !wordWrap; editor.setHorizontallyScrolling(!wordWrap); updateChrome(); }
    private void findDialog() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(query);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Find")
                .setView(input).setPositiveButton("Find Next", (d, w) -> { query = input.getText().toString(); findNext(); })
                .setNegativeButton("Cancel", null).show();
    }
    private void findNext() {
        if (query.isEmpty()) { findDialog(); return; }
        String text = editor.getText().toString(); int from = Math.max(0, editor.getSelectionEnd());
        int at = text.toLowerCase(java.util.Locale.ROOT).indexOf(query.toLowerCase(java.util.Locale.ROOT), from);
        if (at < 0) at = text.toLowerCase(java.util.Locale.ROOT).indexOf(query.toLowerCase(java.util.Locale.ROOT));
        if (at < 0) toast("Cannot find: " + query);
        else { editor.requestFocus(); editor.setSelection(at, at + query.length()); updateChrome(); }
    }
    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("About Notepad 95")
                .setMessage("Notepad 95 for Android\n\nA Windows 95-style plain text editor.\n\nUTF-8 | Open | Save | Find | Word Wrap\n\nUnofficial fan-made app. Not affiliated with Microsoft.")
                .setPositiveButton("OK", null).show();
    }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private void saveDraft() {
        if (editor == null) return;
        prefs.edit().putString("draft", editor.getText().toString()).putString("name", fileName)
                .putString("uri", currentUri == null ? "" : currentUri.toString())
                .putBoolean("dirty", dirty).putBoolean("wrap", wordWrap).apply();
    }
    @Override protected void onPause() { saveDraft(); super.onPause(); }
    @Override public void onBackPressed() {
        if (dirty) confirmDiscard(() -> { dirty = false; finish(); }); else super.onBackPressed();
    }
    @Override public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (event.isCtrlPressed()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_S: saveDocument(); return true;
                case KeyEvent.KEYCODE_O: openDocument(); return true;
                case KeyEvent.KEYCODE_N: newDocument(); return true;
                case KeyEvent.KEYCODE_F: findDialog(); return true;
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }
}
