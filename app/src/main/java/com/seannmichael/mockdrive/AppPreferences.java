package com.seannmichael.mockdrive;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

public final class AppPreferences {
    private static final String FILE = "commercial_settings";
    private static final String API_KEY = "generated_api_key";
    private static final String LICENSE_KEY = "license_key";
    private static final String UNITS = "units";
    private static final String THEME = "theme";
    private static final String API_ENABLED = "api_enabled";
    private static final String API_AUTOSTART = "api_autostart";
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private AppPreferences() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String generateApiKey(Context c) {
        SecureRandom random = new SecureRandom();
        StringBuilder out = new StringBuilder("md_live_");
        for (int i = 0; i < 40; i++) out.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        String key = out.toString();
        prefs(c).edit().putString(API_KEY, key).apply();
        TripStore.setToken(c, key);
        return key;
    }

    public static String apiKey(Context c) { return prefs(c).getString(API_KEY, TripStore.token(c)); }
    public static void revokeApiKey(Context c) {
        prefs(c).edit().remove(API_KEY).apply();
        TripStore.setToken(c, "revoked-" + System.currentTimeMillis());
    }

    public static void saveLicenseKey(Context c, String key) { prefs(c).edit().putString(LICENSE_KEY, key).apply(); }
    public static String licenseKey(Context c) { return prefs(c).getString(LICENSE_KEY, ""); }
    public static void clearLicenseKey(Context c) { prefs(c).edit().remove(LICENSE_KEY).apply(); }

    /** Whether the remote control API is currently meant to be running. */
    public static boolean apiEnabled(Context c) { return prefs(c).getBoolean(API_ENABLED, false); }
    public static void setApiEnabled(Context c, boolean enabled) { prefs(c).edit().putBoolean(API_ENABLED, enabled).apply(); }

    /** Whether the remote API should be restored automatically after a reboot. Off by default. */
    public static boolean apiAutoStart(Context c) { return prefs(c).getBoolean(API_AUTOSTART, false); }
    public static void setApiAutoStart(Context c, boolean autoStart) { prefs(c).edit().putBoolean(API_AUTOSTART, autoStart).apply(); }

    public static void setUnits(Context c, String units) { prefs(c).edit().putString(UNITS, units).apply(); }
    public static String units(Context c) { return prefs(c).getString(UNITS, "Miles"); }
    public static void setTheme(Context c, String theme) { prefs(c).edit().putString(THEME, theme).apply(); }
    public static String theme(Context c) { return prefs(c).getString(THEME, "System default"); }
}