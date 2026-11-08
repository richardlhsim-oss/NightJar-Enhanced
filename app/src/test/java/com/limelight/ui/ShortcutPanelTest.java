package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.limelight.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ShortcutPanelTest {
    private final List<String> keys = new ArrayList<>();
    private boolean anyHeld;

    private static MaterialButton find(View view, String label) {
        if (view instanceof MaterialButton && label.contentEquals(((TextView) view).getText())) return (MaterialButton) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                MaterialButton found = find(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    public void modifierStaysDownAndHighlightedUntilTappedAgainAndTheStreamMakesRoom() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            activity.setTheme(R.style.StreamTheme);
            FrameLayout parent = new FrameLayout(activity);
            View stream = new View(activity);
            parent.addView(stream, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            ShortcutPanel panel = new ShortcutPanel(activity, parent, stream, true, new ShortcutPanel.Host() {
                @Override public void key(int keyCode, boolean down) { keys.add(keyCode + (down ? " down" : " up")); }
                @Override public void toggleKeyboard() { }
                @Override public void setVoiceTyping(boolean on) { }
                @Override public void layoutChanged() { }
                @Override public void modifiersHeld(boolean any) { anyHeld = any; }
            });
            panel.open();
            assertEquals(panel.height(), ((FrameLayout.LayoutParams) stream.getLayoutParams()).bottomMargin);

            MaterialButton ctrl = find(parent, activity.getString(R.string.shortcut_key_ctrl));
            android.graphics.drawable.Drawable off = ctrl.getBackground();
            ctrl.performClick();
            assertTrue(anyHeld);
            assertTrue("highlighted", ctrl.getBackground() != off);
            assertEquals(List.of(KeyEvent.KEYCODE_CTRL_LEFT + " down"), keys);

            ctrl.performClick();
            assertFalse(anyHeld);
            assertEquals(List.of(KeyEvent.KEYCODE_CTRL_LEFT + " down", KeyEvent.KEYCODE_CTRL_LEFT + " up"), keys);

            ctrl.performClick();
            panel.close();
            assertFalse("hiding the bar lets go", anyHeld);
            assertEquals(KeyEvent.KEYCODE_CTRL_LEFT + " up", keys.get(keys.size() - 1));
            assertEquals(0, ((FrameLayout.LayoutParams) stream.getLayoutParams()).bottomMargin);
        }
    }
}
