package com.limelight.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class HttpClipboardSyncTest {
    private String local = "", host = "";
    private Runnable changed;
    private final List<String> sent = new ArrayList<>(), written = new ArrayList<>(), said = new ArrayList<>();
    private boolean deny;

    private final HttpClipboardSync sync = new HttpClipboardSync(new HttpClipboardSync.Transport() {
        @Override public String get() throws Exception {
            if (deny) throw new IOException("Host clipboard read permission denied");
            return host;
        }
        @Override public void send(String text) { sent.add(text); host = text; }
    }, new ClipboardSync.Clipboard() {
        @Override public String read() { return local; }
        @Override public boolean write(String text) { written.add(text); local = text; changed.run(); return true; }
        @Override public void listen(Runnable listener) { changed = listener; }
        @Override public void stopListening() { changed = null; }
    }, Runnable::run, said::add);

    private void idle(long ms) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    @Test
    public void copiesGoBothWaysWithoutEchoes() {
        sync.start(true);
        idle(0);
        local = "from phone";
        changed.run();
        idle(HttpClipboardSync.POLL_MS);
        assertEquals(List.of("from phone"), sent);
        assertTrue("the phone's own copy does not come back", written.isEmpty());

        host = "from pc";
        idle(HttpClipboardSync.POLL_MS);
        assertEquals(List.of("from pc"), written);
        assertEquals("the host's copy does not go back", List.of("from phone"), sent);
        sync.stop();
    }

    @Test
    public void leavingBringsTheHostsCopyThenPollingRests() {
        sync.start(true);
        idle(0);
        host = "last copy";
        sync.focusChanged(false);
        idle(0);
        assertEquals(List.of("last copy"), written);
        host = "later";
        idle(HttpClipboardSync.POLL_MS * 3);
        assertEquals(List.of("last copy"), written);
        sync.stop();
    }

    @Test
    public void aDeniedHostIsSaidOnceAndNotAskedAgain() {
        deny = true;
        sync.start(true);
        idle(HttpClipboardSync.POLL_MS * 3);
        assertEquals(1, said.size());
        sync.stop();
    }
}
