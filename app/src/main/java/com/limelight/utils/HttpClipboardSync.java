package com.limelight.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.limelight.LimeLog;
import com.limelight.nvstream.NvConnection;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Automatic text clipboard over the host's clipboard action, for hosts without clipboard sync (0x5508), as
 * Artemis does on Apollo. This device's copies go when made and when focus returns; the host's are polled, as it
 * tells nothing of them. The last text seen either way is not sent back. Main thread only.
 */
public final class HttpClipboardSync {
    public interface Transport {
        String get() throws Exception;
        void send(String text) throws Exception;
    }

    static final int POLL_MS = 2000;
    private static final String PERMISSION_DENIED = "permission denied";

    private final Transport transport;
    private final ClipboardSync.Clipboard clipboard;
    private final Executor executor;
    private final Consumer<String> sayDenied;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::poll;
    private String last;
    private boolean running, focused, polling, denied;

    HttpClipboardSync(Transport transport, ClipboardSync.Clipboard clipboard, Executor executor, Consumer<String> sayDenied) {
        this.transport = transport;
        this.clipboard = clipboard;
        this.executor = executor;
        this.sayDenied = sayDenied;
    }

    public static HttpClipboardSync forStream(Context context, NvConnection conn, Consumer<String> sayDenied) {
        Transport transport = new Transport() {
            @Override public String get() throws Exception { return conn.getClipboard(); }
            @Override public void send(String text) throws Exception { conn.sendClipboard(text); }
        };
        return new HttpClipboardSync(transport, new ClipboardSync.AndroidClipboard(context),
                task -> new Thread(task, "Clipboard sync").start(), sayDenied);
    }

    public void start(boolean hasFocus) {
        running = true;
        clipboard.listen(this::pushLocal);
        focusChanged(hasFocus);
    }

    public void focusChanged(boolean hasFocus) {
        if (!running) return;
        focused = hasFocus;
        handler.removeCallbacks(poll);
        // Android lets an app read only with focus; leaving is the last chance to bring the host's copy along.
        if (hasFocus) pushLocal();
        poll();
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(poll);
        clipboard.stopListening();
    }

    private void pushLocal() {
        if (!running || !focused) return;
        String text;
        try {
            text = clipboard.read();
        } catch (SecurityException e) {
            return;
        }
        if (text == null || text.isEmpty() || text.equals(last)) return;
        last = text;
        executor.execute(() -> {
            try {
                transport.send(text);
            } catch (Exception e) {
                failed(e);
            }
        });
    }

    private void poll() {
        if (!running || denied || polling) return;
        polling = true;
        executor.execute(() -> {
            String text = null;
            try {
                text = transport.get();
            } catch (Exception e) {
                // A host with an empty clipboard or a busy moment refuses too: only a denial stops this.
                failed(e);
            }
            String found = text;
            handler.post(() -> {
                polling = false;
                if (!running) return;
                if (found != null && !found.isEmpty() && !found.equals(last)) {
                    last = found;
                    clipboard.write(found);
                }
                if (focused && !denied) handler.postDelayed(poll, POLL_MS);
            });
        });
    }

    private void failed(Exception e) {
        LimeLog.warning("Clipboard sync: " + e);
        if (!(e instanceof IOException) || e.getMessage() == null || !e.getMessage().contains(PERMISSION_DENIED)) return;
        handler.post(() -> {
            if (denied) return;
            denied = true;
            sayDenied.accept(e.getMessage());
        });
    }
}
