package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;
import android.view.MotionEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class DesktopGesturesTest {
    private final List<String> log = new ArrayList<>();
    private DesktopGestures gestures;
    private long time;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        gestures = new DesktopGestures(context, new StreamView(context), () -> { }, () -> log.add("right"));
        gestures.viewport().resize(800, 400);
        gestures.setPointer(new DesktopGestures.Pointer() {
            @Override public void moveTo(float x, float y) { log.add("move " + Math.round(x) + "," + Math.round(y)); }
            @Override public void button(boolean down) { log.add(down ? "down" : "up"); }
            @Override public void click() { log.add("click"); }
            @Override public void doubleClick() { log.add("double"); }
            @Override public void scroll(float v, float h) { log.add("scroll"); }
        });
    }

    private void touch(int action, float... xy) {
        int count = xy.length / 2;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xy[i * 2];
            coords[i].y = xy[i * 2 + 1];
            coords[i].pressure = 1;
            coords[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(0, time, action, count, properties, coords, 0, 0, 1, 1, 0, 0, 0, 0);
        gestures.onTouch(event, true, true);
        event.recycle();
    }

    private void tap(float x, float y) {
        touch(MotionEvent.ACTION_DOWN, x, y);
        touch(MotionEvent.ACTION_UP, x, y);
    }

    private void idle(long ms) {
        time += ms;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private int pointerDown() {
        return MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    }

    @Test
    public void zoomedTapClicksThePixelUnderTheFinger() {
        gestures.setEnabled(true);
        gestures.viewport().zoom(2, 400, 200);
        tap(500, 250);
        assertTrue("the click waits for a second tap", log.isEmpty());
        idle(350);
        assertEquals(List.of("move 450,225", "click"), log);
    }

    @Test
    public void oneFingerPansWithoutTouchingTheHost() {
        gestures.setEnabled(true);
        gestures.viewport().zoom(2, 400, 200);
        float before = gestures.viewport().x();
        touch(MotionEvent.ACTION_DOWN, 400, 200);
        touch(MotionEvent.ACTION_MOVE, 450, 200);
        touch(MotionEvent.ACTION_MOVE, 500, 200);
        touch(MotionEvent.ACTION_UP, 500, 200);
        idle(1000);
        assertEquals("four times the finger's travel past the slop", before + 200, gestures.viewport().x(), .01f);
        assertTrue(log.isEmpty());
    }

    @Test
    public void doubleTapWaitsHalfASecondThenDoubleClicks() {
        gestures.setEnabled(true);
        tap(100, 100);
        idle(100);
        tap(100, 100);
        idle(450);
        assertTrue("not before 0.5 s", log.isEmpty());
        idle(100);
        assertEquals(List.of("move 100,100", "double"), log);
    }

    @Test
    public void doubleTapThenMoveDragsWithoutAClick() {
        gestures.setEnabled(true);
        tap(100, 100);
        idle(100);
        touch(MotionEvent.ACTION_DOWN, 100, 100);
        touch(MotionEvent.ACTION_MOVE, 200, 100);
        touch(MotionEvent.ACTION_UP, 200, 100);
        idle(1000);
        assertEquals(List.of("move 100,100", "down", "move 200,100", "up"), log);
    }

    @Test
    public void widthChangeZoomsAndNothingElseDoes() {
        gestures.setEnabled(true);
        touch(MotionEvent.ACTION_DOWN, 300, 200);
        touch(pointerDown(), 300, 200, 500, 200);
        touch(MotionEvent.ACTION_MOVE, 250, 200, 550, 200);
        touch(MotionEvent.ACTION_MOVE, 200, 200, 600, 200);
        assertEquals(400f / 300f, gestures.viewport().scale(), .01f);
        assertTrue(log.isEmpty());
    }

    @Test
    public void parallelFingersMoveTheCursorBetweenThemThenScroll() {
        gestures.setEnabled(true);
        touch(MotionEvent.ACTION_DOWN, 300, 100);
        touch(pointerDown(), 300, 100, 500, 100);
        touch(MotionEvent.ACTION_MOVE, 300, 150, 500, 150);
        touch(MotionEvent.ACTION_MOVE, 300, 200, 500, 200);
        assertEquals(List.of("move 400,150", "scroll"), log);
        assertEquals(1, gestures.viewport().scale(), 0);
    }

    @Test
    public void withZoomOffPressAndMoveDragsAtOnce() {
        gestures.setEnabled(false);
        touch(MotionEvent.ACTION_DOWN, 100, 100);
        touch(MotionEvent.ACTION_MOVE, 200, 100);
        touch(MotionEvent.ACTION_UP, 200, 100);
        assertEquals(List.of("move 100,100", "down", "move 200,100", "up"), log);
    }

    @Test
    public void withZoomOffTapClicksAtOnceAndPinchScrollsNothing() {
        gestures.setEnabled(false);
        tap(100, 100);
        assertEquals(List.of("move 100,100", "click"), log);
        touch(MotionEvent.ACTION_DOWN, 300, 200);
        touch(pointerDown(), 300, 200, 500, 200);
        touch(MotionEvent.ACTION_MOVE, 200, 200, 600, 200);
        assertEquals(1, gestures.viewport().scale(), 0);
    }
}
