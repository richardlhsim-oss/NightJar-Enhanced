package com.limelight;

import android.app.Application;
import android.content.Context;

import com.limelight.utils.CrashCapture;

public class NightjarApplication extends Application {
    private CrashCapture crashCapture;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        crashCapture = new CrashCapture(this);
        crashCapture.installHandler();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        crashCapture.start(this);
        com.limelight.pin.PinGate.install(this);
        com.limelight.console.OpenPlay.install(this);
        applyEnhancedDefaults();
    }

    // Once, so an install from before these defaults gets them and a later choice is kept.
    private void applyEnhancedDefaults() {
        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean("enhanced_audio_defaults", false)) {
            prefs.edit().putBoolean("enhanced_audio_defaults", true)
                    .putBoolean(com.limelight.preferences.PreferenceConfiguration.ENABLE_MIC_PREF_STRING, true)
                    .putBoolean(com.limelight.preferences.PreferenceConfiguration.HOST_AUDIO_PREF_STRING, false).apply();
        }
        // 4.9.0-enhanced.10 started remote streams at 0.5 Mbps and may have stored it; the default is now 1 Mbps.
        if (!prefs.getBoolean("enhanced_remote_1mbps", false)) {
            android.content.SharedPreferences.Editor editor = prefs.edit().putBoolean("enhanced_remote_1mbps", true);
            if (prefs.getInt(com.limelight.preferences.RemoteConnection.BITRATE_PREF, 0) == 500) {
                editor.putInt(com.limelight.preferences.RemoteConnection.BITRATE_PREF, com.limelight.preferences.RemoteConnection.DEFAULT_BITRATE_KBPS);
            }
            editor.apply();
        }
        if (!prefs.getBoolean("enhanced_mic_button_hidden", false)) {
            prefs.edit().putBoolean("enhanced_mic_button_hidden", true)
                    .putBoolean(com.limelight.preferences.PreferenceConfiguration.MIC_BUTTON_PREF_STRING, false).apply();
        }
    }
}
