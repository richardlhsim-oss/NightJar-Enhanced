package com.limelight.ui;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.limelight.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The bar a swipe from the left edge opens: two rows of toggled modifiers, keys and shortcuts in the
 * stream menu's glass. The stream shrinks to the space above it, so the two never overlap and together
 * stay within the window. Another swipe, or a vertical one on the bar, hides it.
 */
public final class ShortcutPanel {
    public interface Host {
        void key(int keyCode, boolean down);
        void toggleKeyboard();
        void setVoiceTyping(boolean on);
        void layoutChanged();
        void modifiersHeld(boolean any);
    }

    private static final int TILE_MIN_WIDTH_DP = 44, TILE_HEIGHT_DP = 32, ICON_DP = 16, GAP_DP = 2, ROWS = 2, SWIPE_DP = 24;
    private static final int EXCLUSION_WIDTH_DP = 48, EXCLUSION_HEIGHT_DP = 200;

    private final Context context;
    private final Host host;
    private final ViewGroup parent;
    private final View stream;
    private final LinearLayout panel;
    private final LinearLayout rows;
    private final int gap;
    private final int barHeight;
    private final List<Integer> held = new ArrayList<>();
    private final List<TileGrid.Tile> modifierButtons = new ArrayList<>();
    private boolean open, voiceOn;
    private int keyboardInset;

    public ShortcutPanel(Activity activity, ViewGroup parent, View stream, boolean voiceTyping, Host host) {
        this.host = host;
        this.parent = parent;
        this.stream = stream;
        // The themed context of the stream menu's panel, so colors and glass match it.
        context = new ActionSheet(activity, "", true).getContext();
        float density = context.getResources().getDisplayMetrics().density;
        gap = Math.round(GAP_DP * density);
        barHeight = ROWS * (Math.round(TILE_HEIGHT_DP * density) + 2 * gap) + 2 * gap;
        float swipe = SWIPE_DP * density;

        panel = new LinearLayout(context) {
            private float downX, downY;

            @Override public boolean onInterceptTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX();
                    downY = event.getRawY();
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float dy = Math.abs(event.getRawY() - downY);
                    if (dy > swipe && dy > Math.abs(event.getRawX() - downX)) {
                        close();
                        return true;
                    }
                }
                return false;
            }

            @Override public boolean onTouchEvent(MotionEvent event) {
                return true;
            }
        };
        panel.setBackground(GlassPanelDrawable.menu(context, false));
        panel.setVisibility(View.GONE);
        ViewCompat.setAccessibilityPaneTitle(panel, context.getString(R.string.shortcut_panel_title));

        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(gap, gap, gap, gap);
        int ctrl = KeyEvent.KEYCODE_CTRL_LEFT, alt = KeyEvent.KEYCODE_ALT_LEFT, shift = KeyEvent.KEYCODE_SHIFT_LEFT, win = KeyEvent.KEYCODE_META_LEFT;

        int d = 0;
        LinearLayout first = row();
        addModifier(first, R.drawable.ic_windows, R.string.shortcut_key_win, win);
        addModifier(first, d, R.string.shortcut_key_ctrl, ctrl);
        addModifier(first, d, R.string.shortcut_key_alt, alt);
        addModifier(first, d, R.string.shortcut_key_shift, shift);
        tile(first, R.drawable.ic_keyboard, context.getString(R.string.shortcut_keyboard)).setOnClickListener(v -> host.toggleKeyboard());
        TileGrid.Tile voice = tile(first, R.drawable.ic_mic, context.getString(R.string.shortcut_voice_typing));
        voiceOn = voiceTyping;
        voice.setOn(voiceOn);
        voice.setOnClickListener(v -> {
            voiceOn = !voiceOn;
            voice.setOn(voiceOn);
            host.setVoiceTyping(voiceOn);
        });
        addChord(first, d, "Esc", KeyEvent.KEYCODE_ESCAPE);
        addChord(first, d, "Tab", KeyEvent.KEYCODE_TAB);
        addChord(first, R.drawable.ic_glass_right, "Enter", KeyEvent.KEYCODE_ENTER);
        addChord(first, R.drawable.ic_backspace, "Del", KeyEvent.KEYCODE_FORWARD_DEL);
        addChord(first, d, "Home", KeyEvent.KEYCODE_MOVE_HOME);
        addChord(first, d, "End", KeyEvent.KEYCODE_MOVE_END);
        addChord(first, R.drawable.ic_more_horiz, "Alt+Tab", alt, KeyEvent.KEYCODE_TAB);
        addChord(first, R.drawable.ic_close, "Alt+F4", alt, KeyEvent.KEYCODE_F4);
        addChord(first, R.drawable.ic_windows, "Start", win);
        addChord(first, R.drawable.ic_monitor, "Desktop", win, KeyEvent.KEYCODE_D);
        addChord(first, R.drawable.ic_folder, "Explorer", win, KeyEvent.KEYCODE_E);
        addChord(first, R.drawable.ic_terminal, "Run", win, KeyEvent.KEYCODE_R);

        LinearLayout second = row();
        addChord(second, R.drawable.ic_copy, "Copy", ctrl, KeyEvent.KEYCODE_C);
        addChord(second, R.drawable.ic_clipboard, "Paste", ctrl, KeyEvent.KEYCODE_V);
        addChord(second, R.drawable.ic_delete, "Cut", ctrl, KeyEvent.KEYCODE_X);
        addChord(second, R.drawable.ic_arrow_back, "Undo", ctrl, KeyEvent.KEYCODE_Z);
        addChord(second, R.drawable.ic_refresh, "Redo", ctrl, KeyEvent.KEYCODE_Y);
        addChord(second, R.drawable.ic_check, "Select all", ctrl, KeyEvent.KEYCODE_A);
        addChord(second, R.drawable.ic_download, "Save", ctrl, KeyEvent.KEYCODE_S);
        addChord(second, R.drawable.ic_glass_search, "Find", ctrl, KeyEvent.KEYCODE_F);
        addChord(second, R.drawable.ic_add, "New tab", ctrl, KeyEvent.KEYCODE_T);
        addChord(second, R.drawable.ic_close, "Close tab", ctrl, KeyEvent.KEYCODE_W);
        addChord(second, R.drawable.ic_glass_grid, "Task view", win, KeyEvent.KEYCODE_TAB);
        addChord(second, R.drawable.ic_picture_in_picture, "Snip", win, shift, KeyEvent.KEYCODE_S);
        addChord(second, R.drawable.ic_pulse, "Task Mgr", ctrl, shift, KeyEvent.KEYCODE_ESCAPE);
        addChord(second, R.drawable.ic_lock, "Lock", win, KeyEvent.KEYCODE_L);
        addChord(second, R.drawable.ic_settings, "Settings", win, KeyEvent.KEYCODE_I);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, barHeight));
        stream.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place());

        parent.addView(panel, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, barHeight, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android keeps this edge for Back unless excluded; the bottom edge cannot be claimed at all.
            int edgeWidth = Math.round(EXCLUSION_WIDTH_DP * density), edgeHeight = Math.round(EXCLUSION_HEIGHT_DP * density);
            parent.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> parent.setSystemGestureExclusionRects(java.util.Arrays.asList(
                    new android.graphics.Rect(0, Math.max(0, b - t - edgeHeight), edgeWidth, b - t),
                    new android.graphics.Rect(r - l - edgeWidth, 0, r - l, edgeHeight))));
        }
        ViewCompat.setOnApplyWindowInsetsListener(panel, (view, insets) -> {
            androidx.core.graphics.Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            view.setPadding(cutout.left, 0, cutout.right, 0);
            return insets;
        });
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        rows.addView(row);
        return row;
    }

    // The stream menu's tile, its selected look included, with the icon beside the label to fit a row.
    private TileGrid.Tile tile(LinearLayout row, int icon, CharSequence label) {
        float density = context.getResources().getDisplayMetrics().density;
        android.content.res.Resources res = context.getResources();
        TileGrid.Tile button = new TileGrid.Tile(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextAppearance(context, R.style.TextAppearance_Nightjar_Tile);
        button.setSingleLine(true);
        if (icon != 0) {
            button.setIconResource(icon);
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            button.setIconSize(Math.round(ICON_DP * density));
            button.setIconPadding(2 * gap);
        }
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(Math.round(TILE_MIN_WIDTH_DP * density));
        button.setMinimumWidth(Math.round(TILE_MIN_WIDTH_DP * density));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPaddingRelative(3 * gap, 0, 3 * gap, 0);
        int[][] states = {new int[] {android.R.attr.state_checked}, new int[] {}};
        android.content.res.ColorStateList ink = new android.content.res.ColorStateList(states, new int[] {
                MaterialColors.getColor(context, R.attr.glassOnSelected, 0),
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0)});
        button.setTextColor(ink);
        button.setIconTint(ink);
        // Taps here must not take focus from the stream: the soft keyboard and dictation stay on it.
        button.setFocusable(false);
        button.setBackground(new GlassControlDrawable(context, GlassControlDrawable.Kind.CONTROL, res.getDimension(R.dimen.stream_tile_corner)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Math.round(TILE_HEIGHT_DP * density));
        params.setMargins(gap, gap, gap, gap);
        row.addView(button, params);
        return button;
    }

    // Stays down until tapped again or the bar hides.
    private void addModifier(LinearLayout row, int icon, int label, int keyCode) {
        TileGrid.Tile button = tile(row, icon, context.getString(label));
        button.setOn(false);
        button.setTag(keyCode);
        button.setOnClickListener(v -> {
            boolean on = !held.contains(keyCode);
            button.setOn(on);
            if (on) held.add(keyCode); else held.remove(Integer.valueOf(keyCode));
            host.key(keyCode, on);
            host.modifiersHeld(!held.isEmpty());
        });
        modifierButtons.add(button);
    }

    private void addChord(LinearLayout row, int icon, String label, int... keys) {
        tile(row, icon, label).setOnClickListener(v -> press(keys));
    }

    private void press(int... keys) {
        List<Integer> sent = new ArrayList<>();
        for (int key : keys) {
            if (held.contains(key)) continue;
            host.key(key, true);
            sent.add(key);
        }
        Collections.reverse(sent);
        for (int key : sent) host.key(key, false);
    }

    public boolean isOpen() {
        return open;
    }

    /** What the bar takes from the bottom of the window, in pixels. */
    public int height() {
        return open ? barHeight : 0;
    }

    public void toggle() {
        if (open) close(); else open();
    }

    public void open() {
        if (open) return;
        open = true;
        panel.setVisibility(View.VISIBLE);
        panel.bringToFront();
        applyLayout();
    }

    /** Hides the bar; what it holds down goes up with it. */
    public void close() {
        if (!open) return;
        open = false;
        panel.setVisibility(View.GONE);
        panel.setTranslationY(0);
        applyLayout();
        for (TileGrid.Tile button : modifierButtons) {
            int key = (Integer) button.getTag();
            if (!held.contains(key)) continue;
            button.setOn(false);
            host.key(key, false);
        }
        held.clear();
        host.modifiersHeld(false);
    }

    /** The bar sits on top of the soft keyboard. */
    public void setKeyboardInset(int bottom) {
        keyboardInset = bottom;
        place();
    }

    // Right under the picture, whose letterbox would otherwise lie between them. The picture is centred in the
    // space above the bar, so this centres the two together.
    private void place() {
        if (!open) return;
        panel.setTranslationY(-Math.max(keyboardInset, parent.getHeight() - barHeight - stream.getBottom()));
    }

    private void applyLayout() {
        // FrameLayout centres a child at (space - height) / 2 + top - bottom, and takes both margins from its
        // height. A quarter above and the rest below gives the space above the bar and centres in it; the bar's
        // whole height below alone would lift the picture half a bar past the top of the screen.
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) stream.getLayoutParams();
        boolean top = (params.gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.TOP;
        params.topMargin = top ? 0 : height() / 4;
        params.bottomMargin = height() - params.topMargin;
        stream.setLayoutParams(params);
        host.layoutChanged();
    }
}
