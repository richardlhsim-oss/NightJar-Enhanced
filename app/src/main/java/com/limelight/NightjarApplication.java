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
        if (prefs.getBoolean("enhanced_audio_defaults", false)) return;
        prefs.edit().putBoolean("enhanced_audio_defaults", true)
                .putBoolean(com.limelight.preferences.PreferenceConfiguration.ENABLE_MIC_PREF_STRING, true)
                .putBoolean(com.limelight.preferences.PreferenceConfiguration.MIC_BUTTON_PREF_STRING, true)
                .putBoolean(com.limelight.preferences.PreferenceConfiguration.HOST_AUDIO_PREF_STRING, false).apply();
    }
}
