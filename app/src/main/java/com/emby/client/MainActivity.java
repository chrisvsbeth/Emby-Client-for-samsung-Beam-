package com.emby.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "EmbyClient";
    private static final String PREFS_NAME = "EmbyClientPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_REMEMBER = "remember_credentials";

    private EditText serverUrlEdit;
    private EditText usernameEdit;
    private EditText passwordEdit;
    private Button loginButton;
    private ProgressBar progressBar;
    private LinearLayout formContainer;
    private CheckBox rememberCheck;

    private Handler mainHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "MainActivity.onCreate()");

        try {
            setContentView(R.layout.activity_main);
            mainHandler = new Handler(Looper.getMainLooper());
            initViews();
            loadSavedCredentials();
            setupListeners();
            Log.i(TAG, "MainActivity ready - Android SDK " + android.os.Build.VERSION.SDK_INT);
        } catch (Exception e) {
            Log.e(TAG, "FATAL: Failed to create activity: " + e.getClass().getName(), e);
            Toast.makeText(this, "Failed to start app: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initViews() {
        serverUrlEdit = (EditText) findViewById(R.id.serverUrl);
        usernameEdit = (EditText) findViewById(R.id.username);
        passwordEdit = (EditText) findViewById(R.id.password);
        loginButton = (Button) findViewById(R.id.loginButton);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        formContainer = (LinearLayout) findViewById(R.id.formContainer);
        rememberCheck = new CheckBox(this);
        rememberCheck.setText("Remember credentials");
        rememberCheck.setTextColor(0xFFB3B3B3);
        rememberCheck.setChecked(true);
        formContainer.addView(rememberCheck, formContainer.indexOfChild(findViewById(R.id.loginButton)));
    }

    private void loadSavedCredentials() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String savedServerUrl = prefs.getString(KEY_SERVER_URL, "");
            String savedUsername = prefs.getString(KEY_USERNAME, "");
            String savedPassword = prefs.getString(KEY_PASSWORD, "");
            boolean remember = prefs.getBoolean(KEY_REMEMBER, true);

            rememberCheck.setChecked(remember);
            if (savedServerUrl != null && !savedServerUrl.equals("")) {
                serverUrlEdit.setText(savedServerUrl);
            }
            if (remember) {
                if (savedUsername != null && !savedUsername.equals("")) {
                    usernameEdit.setText(savedUsername);
                }
                if (savedPassword != null && !savedPassword.equals("")) {
                    passwordEdit.setText(savedPassword);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not load saved credentials: " + e.getMessage());
        }
    }

    private void setupListeners() {
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performLogin();
            }
        });
    }

    private void performLogin() {
        Log.i(TAG, "performLogin() called");

        final String serverUrl;
        final String username;
        final String password;

        try {
            serverUrl = serverUrlEdit.getText() != null ? serverUrlEdit.getText().toString().trim() : "";
            username = usernameEdit.getText() != null ? usernameEdit.getText().toString().trim() : "";
            password = passwordEdit.getText() != null ? passwordEdit.getText().toString() : "";
        } catch (Exception e) {
            Log.e(TAG, "Error reading fields: " + e.getMessage(), e);
            Toast.makeText(this, "Error reading input", Toast.LENGTH_SHORT).show();
            return;
        }

        if (serverUrl.equals("")) {
            serverUrlEdit.setError("Server URL is required");
            return;
        }
        if (username.equals("")) {
            usernameEdit.setError("Username is required");
            return;
        }
        if (password.equals("")) {
            passwordEdit.setError("Password is required");
            return;
        }

        showLoading(true);
        loginButton.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final EmbyApiClient apiClient = new EmbyApiClient(serverUrl);
                    final boolean success = apiClient.authenticate(username, password);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            loginButton.setEnabled(true);

                            if (success) {
                                Log.i(TAG, "Login SUCCESS!");
                                saveCredentials(serverUrl, username, password);
                                navigateToContent(apiClient);
                            } else {
                                String error = apiClient.getLastError();
                                int code = apiClient.getLastResponseCode();
                                String fullError = error;
                                if (code > 0) {
                                    fullError = "HTTP " + code + ": " + error;
                                }
                                Log.w(TAG, "Login FAILED (code=" + code + "): " + error);
                                showError(fullError != null ? fullError : getString(R.string.login_failed));
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Login EXCEPTION: " + e.getClass().getName(), e);
                    final String errorMsg = e.getMessage();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            loginButton.setEnabled(true);
                            showError("Connection error: " + errorMsg);
                        }
                    });
                }
            }
        }).start();
    }

    private void saveCredentials(String url, String user, String pass) {
        try {
            boolean remember = rememberCheck.isChecked();
            SharedPreferences.Editor prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            prefs.putString(KEY_SERVER_URL, url);
            prefs.putBoolean(KEY_REMEMBER, remember);
            if (remember) {
                prefs.putString(KEY_USERNAME, user);
                prefs.putString(KEY_PASSWORD, pass);
            } else {
                prefs.remove(KEY_USERNAME);
                prefs.remove(KEY_PASSWORD);
            }
            prefs.apply();
        } catch (Exception e) {
            Log.w(TAG, "Could not save credentials: " + e.getMessage());
        }
    }

    private void navigateToContent(EmbyApiClient client) {
        try {
            Intent intent = new Intent(MainActivity.this, ContentActivity.class);
            intent.putExtra("server_url", client.getServerUrl());
            intent.putExtra("access_token", client.getAccessToken());
            intent.putExtra("user_id", client.getUserId());
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting ContentActivity: " + e.getMessage(), e);
            Toast.makeText(this, "Error opening content", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (formContainer != null) {
            formContainer.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void showError(String message) {
        Log.e(TAG, "Showing error: " + message);
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Login Error");
            builder.setMessage(message);
            builder.setPositiveButton("OK", null);
            builder.show();
        } catch (Exception e) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
}
