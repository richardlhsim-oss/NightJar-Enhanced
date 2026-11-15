package com.limelight.console;

import com.limelight.R;

/** The host's Desktop entry is a connection, not a game: it connects and terminates instead of playing and quitting. */
public final class DesktopLabels {
    private DesktopLabels() { }

    public static boolean isDesktop(String appName) {
        return appName != null && appName.trim().equalsIgnoreCase("Desktop");
    }

    public static int start(String appName, int otherwise) {
        return isDesktop(appName) ? R.string.desktop_connect : otherwise;
    }

    public static int end(String appName, int otherwise) {
        return isDesktop(appName) ? R.string.desktop_terminate : otherwise;
    }
}
