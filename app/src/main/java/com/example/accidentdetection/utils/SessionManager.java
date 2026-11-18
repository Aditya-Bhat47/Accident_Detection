package com.example.accidentdetection.utils;


import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREFS = "app_prefs";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_NAME = "user_name";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveSession(String token, String name) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_NAME, name)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getUserName() {
        return prefs.getString(KEY_NAME, "User");
    }

    public boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }
    public void logout() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_NAME)
                .apply();
    }
}
