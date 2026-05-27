package com.emby.client;

import android.app.Application;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EmbyApp extends Application {
    private static final String TAG = "EmbyClient";
    private static final String CRASH_LOG_FILE = "crash_log.txt";
    @Override
    public void onCreate() {
        super.onCreate();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable throwable) {
                logCrash(thread, throwable);
            }
        });

        Log.i(TAG, "========================================");
        Log.i(TAG, "Emby Client v1.0.2 starting");
        Log.i(TAG, "Android: " + android.os.Build.VERSION.RELEASE);
        Log.i(TAG, "SDK: " + android.os.Build.VERSION.SDK_INT);
        Log.i(TAG, "Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
        Log.i(TAG, "========================================");
    }

    private void logCrash(Thread thread, Throwable throwable) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        String timestamp = dateFormat.format(new Date());

        StringBuilder crashReport = new StringBuilder();
        crashReport.append("========================================\n");
        crashReport.append("EMBY CLIENT CRASH REPORT\n");
        crashReport.append("========================================\n");
        crashReport.append("Time: ").append(timestamp).append("\n");
        crashReport.append("----------------------------------------\n");
        crashReport.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
        crashReport.append("Android: ").append(android.os.Build.VERSION.RELEASE).append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        crashReport.append("App Version: 1.0.2\n");
        crashReport.append("----------------------------------------\n");
        crashReport.append("Thread: ").append(thread.getName()).append(" (ID: ").append(thread.getId()).append(")\n");
        crashReport.append("----------------------------------------\n");
        crashReport.append("Exception: ").append(throwable.getClass().getName()).append("\n");
        crashReport.append("Message: ").append(throwable.getMessage()).append("\n");
        crashReport.append("----------------------------------------\n");
        crashReport.append("Stack Trace:\n");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        crashReport.append(sw.toString());

        crashReport.append("========================================\n");
        crashReport.append("End of Report\n");

        PrintWriter fileWriter = null;
        try {
            File logDir = getFilesDir();
            File crashFile = new File(logDir, CRASH_LOG_FILE);

            fileWriter = new PrintWriter(new FileWriter(crashFile, true));
            fileWriter.println(crashReport.toString());
            fileWriter.flush();

            Log.e(TAG, "CRASH: " + throwable.getClass().getName());
            Log.e(TAG, "CRASH LOG SAVED: " + crashFile.getAbsolutePath());

        } catch (IOException e) {
            Log.e(TAG, "Failed to write crash log to file", e);
        } catch (Exception e) {
            Log.e(TAG, "Error in crash handler", e);
        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

}