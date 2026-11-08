package com.limelight.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

import com.limelight.binding.input.touch.TwoFingerTapGesture;

/**
 * Finger gestures over the stream. Two fingers pinch to zoom when their distance changes and scroll
 * otherwise, from the cursor placed between them. In direct mode one finger also acts here: with zoom on
 * it pans, a tap clicks where it points, a double tap double clicks and a double tap then move drags; with
 * zoom off a press and move drags at once. A hold right clicks.
 */
public final class DesktopGestures {
    /** The host's mouse; positions are in stream view pixels with the zoom already undone. */
    public interface Pointer {
        void moveTo(float x, float y);
        void button(boolean down);
        void click();
        void doubleClick();
        void scroll(float vertical, float horizontal);
    }

    private static final int TWO_UNDECIDED = 0, TWO_ZOOM = 1, TWO_SCROLL = 2;
    private static final int ONE_IDLE = 0, ONE_PRESS = 1, ONE_SECOND_PRESS = 2, ONE_PAN = 3, ONE_DRAG = 4;
    private static final float PINCH_SLOP_DP = 24, SCROLL_SLOP_DP = 10, TAP_SLOP_DP = 12, DOUBLE_TAP_SLOP_DP = 40;
    private static final float SCROLL_SPEED = 3, PAN_SPEED = 2;
    private static final int DOUBLE_TAP_MS = 350, DOUBLE_CLICK_WAIT_MS = 500, LONG_PRESS_MS = 650;

    private final DesktopViewport viewport = new DesktopViewport();
    private final StreamView view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable cancelInput;
    private final Runnable rightClick;
    private final TwoFingerTapGesture twoFingerTap = new TwoFingerTapGesture();
    private final float density;
    private Pointer pointer;
    private boolean enabled, consuming, tapAllowed;
    private int twoFingers, oneFinger;
    private float startSpan, startX, startY, lastSpan, lastX, lastY;
    private float downX, downY, oneLastX, oneLastY, tapX, tapY;
    private static final int HOST_DOUBLE_CLICK_MS = 500;
    private long tapTime = Long.MIN_VALUE / 2;
    private boolean longPressed;
    private final Runnable pendingClick = this::firePendingClick;
    private boolean clickPending;
    private final Runnable pendingDoubleClick = this::firePendingDoubleClick;
    private boolean doubleClickPending;
    private final Runnable longPress = this::fireLongPress;

    public DesktopGestures(Context context, StreamView view, Runnable cancelInput, Runnable rightClick) {
        this.view = view; this.cancelInput = cancelInput; this.rightClick = rightClick;
        density = context.getResources().getDisplayMetrics().density;
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            viewport.resize(r - l, b - t); apply();
        });
    }
    public void setPointer(Pointer pointer) { this.pointer = pointer; }
    public DesktopViewport viewport() { return viewport; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) {
        cancelInput.run(); cancelTouch(); consuming = false; this.enabled = enabled;
        if (!enabled) reset();
    }
    /** Invalidate a pending tap on focus loss, mode changes, or an overlay taking input. */
    public void cancelTouch() {
        twoFingerTap.reset(); tapAllowed = false;
        cancelOneFinger();
        flushClicks();
    }
    public void reset() { viewport.reset(); apply(); }
    private void apply() { view.setVideoTransform(viewport.scale(), viewport.x(), viewport.y()); }

    public boolean onTouch(MotionEvent event, boolean allowRightClick) {
        return onTouch(event, allowRightClick, false);
    }

    public boolean onTouch(MotionEvent event, boolean allowRightClick, boolean direct) {
        direct = direct && pointer != null;
        if ((!enabled && !direct) || event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            consuming = false;
            tapAllowed = allowRightClick;
        }
        boolean tapped = twoFingerTap.onTouch(event, density);
        if (event.getPointerCount() >= 2) onTwoFingers(event, action, direct);
        else if (direct && !consuming) onOneFinger(event, action);
        boolean handled = consuming || direct;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean wasConsuming = consuming;
            consuming = false;
            if (wasConsuming && tapped && tapAllowed) rightClick.run();
            tapAllowed = false;
        }
        return handled;
    }

    private void onTwoFingers(MotionEvent event, int action, boolean direct) {
        float x = (event.getX(0) + event.getX(1)) / 2;
        float y = (event.getY(0) + event.getY(1)) / 2;
        float span = (float) Math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1));
        if (!consuming) {
            cancelOneFinger();
            flushClicks();
            cancelInput.run();
            consuming = true;
            twoFingers = TWO_UNDECIDED;
            startSpan = span; startX = x; startY = y;
            rebase(span, x, y);
            return;
        }
        if (action != MotionEvent.ACTION_MOVE) {
            rebase(span, x, y);
            return;
        }
        if (twoFingers == TWO_UNDECIDED) {
            float spanMoved = Math.abs(span - startSpan);
            float midMoved = (float) Math.hypot(x - startX, y - startY);
            if (enabled && spanMoved > PINCH_SLOP_DP * density && spanMoved >= midMoved) {
                twoFingers = TWO_ZOOM;
            } else if (midMoved > SCROLL_SLOP_DP * density) {
                twoFingers = TWO_SCROLL;
                // The host scrolls what is under its cursor, so no click is needed to pick the pane.
                if (pointer != null) pointer.moveTo(viewport.inputX(x), viewport.inputY(y));
            } else {
                return;
            }
            twoFingerTap.invalidate();
            rebase(span, x, y);
            return;
        }
        if (twoFingers == TWO_ZOOM) {
            if (lastSpan > 0) viewport.zoom(span / lastSpan, x, y);
            viewport.pan(x - lastX, y - lastY);
            apply();
        } else if (!direct && viewport.scale() > 1 || pointer == null) {
            // A trackpad's one finger moves the cursor, so a zoomed view still pans with two.
            viewport.pan(x - lastX, y - lastY);
            apply();
        } else {
            pointer.scroll((y - lastY) * SCROLL_SPEED, -(x - lastX) * SCROLL_SPEED);
        }
        rebase(span, x, y);
    }

    private void rebase(float span, float x, float y) {
        lastSpan = span; lastX = x; lastY = y;
    }

    private void onOneFinger(MotionEvent event, int action) {
        float x = event.getX(), y = event.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = oneLastX = x;
                downY = oneLastY = y;
                longPressed = false;
                if (enabled && clickPending && viewportDistance(x, y) < DOUBLE_TAP_SLOP_DP * density) {
                    // The first tap's click never went: a lift double clicks, a move drags.
                    handler.removeCallbacks(pendingClick);
                    clickPending = false;
                    oneFinger = ONE_SECOND_PRESS;
                    doubleClickPending = true;
                    handler.postDelayed(pendingDoubleClick, DOUBLE_CLICK_WAIT_MS);
                } else {
                    flushClicks();
                    oneFinger = ONE_PRESS;
                    handler.postDelayed(longPress, LONG_PRESS_MS);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                boolean moved = Math.hypot(x - downX, y - downY) > TAP_SLOP_DP * density;
                if (oneFinger == ONE_PRESS && moved) {
                    handler.removeCallbacks(longPress);
                    if (enabled) oneFinger = ONE_PAN;
                    else startDrag(x, y);
                } else if (oneFinger == ONE_SECOND_PRESS && moved && doubleClickPending) {
                    handler.removeCallbacks(pendingDoubleClick);
                    doubleClickPending = false;
                    startDrag(x, y);
                } else if (oneFinger == ONE_PAN) {
                    viewport.pan((x - oneLastX) * PAN_SPEED, (y - oneLastY) * PAN_SPEED);
                    apply();
                } else if (oneFinger == ONE_DRAG) {
                    pointer.moveTo(viewport.inputX(x), viewport.inputY(y));
                }
                oneLastX = x;
                oneLastY = y;
                break;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPress);
                if (oneFinger == ONE_PRESS) {
                    // A second tap lands on the first one's pixel, or the host sees two clicks, not a double click.
                    boolean again = event.getEventTime() - tapTime < HOST_DOUBLE_CLICK_MS &&
                            viewportDistance(x, y) < DOUBLE_TAP_SLOP_DP * density;
                    if (!again) {
                        tapX = viewport.inputX(x);
                        tapY = viewport.inputY(y);
                    }
                    tapTime = again ? Long.MIN_VALUE / 2 : event.getEventTime();
                    if (longPressed) {
                        pointer.moveTo(tapX, tapY);
                        rightClick.run();
                    } else if (enabled) {
                        // Held back until a second tap can no longer come.
                        clickPending = true;
                        handler.postDelayed(pendingClick, DOUBLE_TAP_MS);
                    } else {
                        pointer.moveTo(tapX, tapY);
                        pointer.click();
                    }
                } else if (oneFinger == ONE_DRAG) {
                    pointer.button(false);
                }
                oneFinger = ONE_IDLE;
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelOneFinger();
                break;
            default:
                break;
        }
    }

    private float viewportDistance(float x, float y) {
        return (float) Math.hypot(viewport.inputX(x) - tapX, viewport.inputY(y) - tapY) * viewport.scale();
    }

    // Presses where the finger landed, so the drag takes what was pointed at, then follows it.
    private void startDrag(float x, float y) {
        pointer.moveTo(viewport.inputX(downX), viewport.inputY(downY));
        pointer.button(true);
        pointer.moveTo(viewport.inputX(x), viewport.inputY(y));
        oneFinger = ONE_DRAG;
    }

    private void cancelOneFinger() {
        handler.removeCallbacks(longPress);
        if (oneFinger == ONE_DRAG && pointer != null) pointer.button(false);
        oneFinger = ONE_IDLE;
    }

    // A tap already finished still reaches the host, before whatever comes next.
    private void flushClicks() {
        if (clickPending) {
            handler.removeCallbacks(pendingClick);
            firePendingClick();
        }
        if (doubleClickPending) {
            handler.removeCallbacks(pendingDoubleClick);
            firePendingDoubleClick();
        }
    }

    private void fireLongPress() {
        if (oneFinger != ONE_PRESS) return;
        longPressed = true;
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    private void firePendingClick() {
        clickPending = false;
        if (pointer == null) return;
        pointer.moveTo(tapX, tapY);
        pointer.click();
    }

    private void firePendingDoubleClick() {
        doubleClickPending = false;
        if (pointer == null) return;
        pointer.moveTo(tapX, tapY);
        pointer.doubleClick();
    }
}
