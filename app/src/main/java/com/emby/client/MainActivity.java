package com.emby.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "EmbyClient";
    private static final String PREFS_NAME = "EmbyClientPrefs";
    private static final String KEY_SERVER_URL = "server_url";

    private ImageView logoImage;
    private TextView titleText;
    private EditText serverUrlEdit;
    private EditText usernameEdit;
    private EditText passwordEdit;
    private Button loginButton;
    private ProgressBar progressBar;
    private LinearLayout formContainer;

    private Handler mainHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "MainActivity.onCreate()");

        try {
            setContentView(R.layout.activity_main);
            Log.i(TAG, "Layout inflated");

            mainHandler = new Handler(Looper.getMainLooper());

            initViews();
            loadSavedServerUrl();
            setupListeners();

            Log.i(TAG, "MainActivity ready - Android SDK " + android.os.Build.VERSION.SDK_INT);
            Log.i(TAG, "App: com.emby.client v1.0.2");

        } catch (Exception e) {
            Log.e(TAG, "FATAL: Failed to create activity: " + e.getClass().getName(), e);
            Toast.makeText(this, "Failed to start app: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initViews() {
        logoImage = (ImageView) findViewById(R.id.logoImage);
        titleText = (TextView) findViewById(R.id.titleText);
        serverUrlEdit = (EditText) findViewById(R.id.serverUrl);
        usernameEdit = (EditText) findViewById(R.id.username);
        passwordEdit = (EditText) findViewById(R.id.password);
        loginButton = (Button) findViewById(R.id.loginButton);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        formContainer = (LinearLayout) findViewById(R.id.formContainer);
    }

    private void loadSavedServerUrl() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String savedServerUrl = prefs.getString(KEY_SERVER_URL, "");
            if (savedServerUrl != null && !savedServerUrl.equals("")) {
                serverUrlEdit.setText(savedServerUrl);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not load saved server URL: " + e.getMessage());
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
            CharSequence serverSeq = serverUrlEdit.getText();
            CharSequence userSeq = usernameEdit.getText();
            CharSequence passSeq = passwordEdit.getText();

            serverUrl = serverSeq != null ? serverSeq.toString().trim() : "";
            username = userSeq != null ? userSeq.toString().trim() : "";
            password = passSeq != null ? passSeq.toString() : "";
        } catch (Exception e) {
            Log.e(TAG, "Error reading fields: " + e.getMessage(), e);
            Toast.makeText(this, "Error reading input", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.i(TAG, "Server URL: " + serverUrl);
        Log.i(TAG, "Username: " + username);

        if (serverUrl == null || serverUrl.equals("")) {
            serverUrlEdit.setError("Server URL is required");
            return;
        }
        if (username == null || username.equals("")) {
            usernameEdit.setError("Username is required");
            return;
        }
        if (password == null || password.equals("")) {
            passwordEdit.setError("Password is required");
            return;
        }

        showLoading(true);
        loginButton.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "Creating EmbyApiClient...");
                    final EmbyApiClient apiClient = new EmbyApiClient(serverUrl);

                    Log.i(TAG, "Calling authenticate to: " + serverUrl);
                    final boolean success = apiClient.authenticate(username, password);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            loginButton.setEnabled(true);

                            if (success) {
                                Log.i(TAG, "Login SUCCESS!");
                                Log.i(TAG, "User ID: " + apiClient.getUserId());
                                saveServerUrl(serverUrl);
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

    private void saveServerUrl(String url) {
        try {
            SharedPreferences.Editor prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            prefs.putString(KEY_SERVER_URL, url);
            prefs.commit();
        } catch (Exception e) {
            Log.w(TAG, "Could not save server URL: " + e.getMessage());
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