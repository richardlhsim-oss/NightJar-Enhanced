package com.limelight.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
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
 * The bar a swipe up from the bottom opens: three rows of toggled modifiers, keys and shortcuts in the
 * stream menu's glass. The stream shrinks to the space above it, so the two never overlap and together
 * stay within the window. A vertical swipe on the bar hides it.
 */
public final class ShortcutPanel {
    public interface Host {
        void key(int keyCode, boolean down);
        void toggleKeyboard();
        void setVoiceTyping(boolean on);
        void layoutChanged();
        void modifiersHeld(boolean any);
    }

    private static final int TILE_WIDTH_DP = 60, TILE_HEIGHT_DP = 30, GAP_DP = 2, ROWS = 2, SWIPE_DP = 24;
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
    private final List<MaterialButton> modifierButtons = new ArrayList<>();
    private boolean open, voiceOn;

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

        LinearLayout first = row();
        addModifier(first, R.string.shortcut_key_win, win);
        addModifier(first, R.string.shortcut_key_ctrl, ctrl);
        addModifier(first, R.string.shortcut_key_alt, alt);
        addModifier(first, R.string.shortcut_key_shift, shift);
        tile(first, context.getString(R.string.shortcut_keyboard)).setOnClickListener(v -> host.toggleKeyboard());
        MaterialButton voice = tile(first, context.getString(R.string.shortcut_voice_typing));
        voiceOn = voiceTyping;
        paint(voice, voiceOn);
        voice.setOnClickListener(v -> {
            voiceOn = !voiceOn;
            paint(voice, voiceOn);
            host.setVoiceTyping(voiceOn);
        });
        addChord(first, "Esc", KeyEvent.KEYCODE_ESCAPE);
        addChord(first, "Tab", KeyEvent.KEYCODE_TAB);
        addChord(first, "Enter", KeyEvent.KEYCODE_ENTER);
        addChord(first, "Del", KeyEvent.KEYCODE_FORWARD_DEL);
        addChord(first, "Home", KeyEvent.KEYCODE_MOVE_HOME);
        addChord(first, "End", KeyEvent.KEYCODE_MOVE_END);

        LinearLayout second = row();
        addChord(second, "Alt+Tab", alt, KeyEvent.KEYCODE_TAB);
        addChord(second, "Alt+F4", alt, KeyEvent.KEYCODE_F4);
        addChord(second, "Start", win);
        addChord(second, "Desktop", win, KeyEvent.KEYCODE_D);
        addChord(second, "Explorer", win, KeyEvent.KEYCODE_E);
        addChord(second, "Run", win, KeyEvent.KEYCODE_R);
        addChord(second, "Task view", win, KeyEvent.KEYCODE_TAB);
        addChord(second, "Snip", win, shift, KeyEvent.KEYCODE_S);
        addChord(second, "Task Mgr", ctrl, shift, KeyEvent.KEYCODE_ESCAPE);
        addChord(second, "Lock", win, KeyEvent.KEYCODE_L);
        addChord(second, "Settings", win, KeyEvent.KEYCODE_I);
        addChord(second, "Copy", ctrl, KeyEvent.KEYCODE_C);
        addChord(second, "Paste", ctrl, KeyEvent.KEYCODE_V);
        addChord(second, "Cut", ctrl, KeyEvent.KEYCODE_X);
        addChord(second, "Undo", ctrl, KeyEvent.KEYCODE_Z);
        addChord(second, "Redo", ctrl, KeyEvent.KEYCODE_Y);
        addChord(second, "Select all", ctrl, KeyEvent.KEYCODE_A);
        addChord(second, "Save", ctrl, KeyEvent.KEYCODE_S);
        addChord(second, "Find", ctrl, KeyEvent.KEYCODE_F);
        addChord(second, "New tab", ctrl, KeyEvent.KEYCODE_T);
        addChord(second, "Close tab", ctrl, KeyEvent.KEYCODE_W);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setFillViewport(true);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, barHeight));

        parent.addView(panel, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, barHeight, Gravity.BOTTOM));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android keeps this edge for Back unless excluded; the bottom edge cannot be claimed at all.
            parent.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> parent.setSystemGestureExclusionRects(
                    Collections.singletonList(new android.graphics.Rect(0, Math.max(0, b - t - Math.round(EXCLUSION_HEIGHT_DP * density)),
                            Math.round(EXCLUSION_WIDTH_DP * density), b - t))));
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

    private MaterialButton tile(LinearLayout row, CharSequence label) {
        float density = context.getResources().getDisplayMetrics().density;
        MaterialButton button = new MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextAppearance(context, R.style.TextAppearance_Nightjar_Label);
        button.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0));
        button.setSingleLine(true);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(gap, 0, gap, 0);
        // Taps here must not take focus from the stream: the soft keyboard and dictation stay on it.
        button.setFocusable(false);
        button.setBackground(new GlassControlDrawable(context, GlassControlDrawable.Kind.ROW,
                context.getResources().getDimension(R.dimen.nj_radius_control)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Math.round(TILE_WIDTH_DP * density), Math.round(TILE_HEIGHT_DP * density));
        params.setMargins(gap, gap, gap, gap);
        row.addView(button, params);
        return button;
    }

    // A MaterialButton drops its checked state once it has a custom background, so state lives here.
    private void paint(MaterialButton button, boolean on) {
        if (on) {
            GradientDrawable fill = new GradientDrawable();
            fill.setCornerRadius(context.getResources().getDimension(R.dimen.nj_radius_control));
            fill.setColor(MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0));
            button.setBackground(fill);
            button.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimary, 0));
        } else {
            button.setBackground(new GlassControlDrawable(context, GlassControlDrawable.Kind.ROW,
                    context.getResources().getDimension(R.dimen.nj_radius_control)));
            button.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0));
        }
    }

    // Stays down until tapped again or the bar hides.
    private void addModifier(LinearLayout row, int label, int keyCode) {
        MaterialButton button = tile(row, context.getString(label));
        button.setTag(keyCode);
        button.setOnClickListener(v -> {
            boolean on = !held.contains(keyCode);
            paint(button, on);
            if (on) held.add(keyCode); else held.remove(Integer.valueOf(keyCode));
            host.key(keyCode, on);
            host.modifiersHeld(!held.isEmpty());
        });
        modifierButtons.add(button);
    }

    private void addChord(LinearLayout row, String label, int... keys) {
        tile(row, label).setOnClickListener(v -> press(keys));
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
        for (MaterialButton button : modifierButtons) {
            int key = (Integer) button.getTag();
            if (!held.contains(key)) continue;
            paint(button, false);
            host.key(key, false);
        }
        held.clear();
        host.modifiersHeld(false);
    }

    /** The bar sits on top of the soft keyboard. */
    public void setKeyboardInset(int bottom) {
        if (open) panel.setTranslationY(-bottom);
    }

    private void applyLayout() {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) stream.getLayoutParams();
        params.bottomMargin = height();
        stream.setLayoutParams(params);
        host.layoutChanged();
    }
}
