package com.emby.client;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.UUID;

public class PlayerActivity extends Activity {
    private VideoView videoView;
    private HlsPlaybackEngine hlsEngine;
    private SeekBar seekBar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView remainingTimeText;
    private TextView titleText;
    private TextView statsLabel;
    private Button playPauseBtn;
    private Button rewindBtn;
    private Button forwardBtn;
    private ProgressBar bufferProgress;
    private LinearLayout controlsLayout;
    private FrameLayout rootLayout;
    private View backBtn;
    private View resumeDialogLayout;
    private Button resumeBtn;
    private Button startOverBtn;
    private TextView resumeTimeLabel;
    private boolean isTracking = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean controlsVisible = true;
    private static final int FADE_DELAY = 3000;
    private static final String TAG = "Player";
    private static final String PREFS_NAME = "EmbyPlaybackPrefs";

    private String itemId;
    private String videoName;
    private String itemImageTag;
    private String videoUrl;
    private int savedPosition = 0;
    private long apiDuration = 0;
    private boolean resumeDialogShown = false;
    private boolean userResumed = false;
    private String serverUrl;
    private String accessToken;
    private String playSessionId;
    private EmbyApiClient apiClient;
    private boolean useHlsEngine = false;
    private int currentQuality = 0;
    private String playMethod = "DirectStream";
    private int seekOffset = 0;
    private boolean stopping = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        try {
            initViews();
            startPlayback();
        } catch (Exception e) {
            Log.e(TAG, "Player error: " + e.getMessage(), e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initViews() {
        rootLayout = (FrameLayout) findViewById(R.id.rootLayout);
        videoView = (VideoView) findViewById(R.id.videoView);
        seekBar = (SeekBar) findViewById(R.id.seekBar);
        currentTimeText = (TextView) findViewById(R.id.currentTime);
        totalTimeText = (TextView) findViewById(R.id.totalTime);
        remainingTimeText = (TextView) findViewById(R.id.remainingTime);
        titleText = (TextView) findViewById(R.id.videoTitle);
        statsLabel = (TextView) findViewById(R.id.statsLabel);
        playPauseBtn = (Button) findViewById(R.id.playPauseBtn);
        rewindBtn = (Button) findViewById(R.id.rewindBtn);
        forwardBtn = (Button) findViewById(R.id.forwardBtn);
        bufferProgress = (ProgressBar) findViewById(R.id.bufferProgress);
        controlsLayout = (LinearLayout) findViewById(R.id.controlsLayout);
        backBtn = findViewById(R.id.backBtn);

        resumeDialogLayout = findViewById(R.id.resumeDialogLayout);
        resumeBtn = (Button) findViewById(R.id.resumeBtn);
        startOverBtn = (Button) findViewById(R.id.startOverBtn);
        resumeTimeLabel = (TextView) findViewById(R.id.resumeTimeLabel);
        stopping = false;

        videoUrl = getIntent().getStringExtra("video_url");
        itemId = getIntent().getStringExtra("item_id");
        serverUrl = getIntent().getStringExtra("server_url");
        accessToken = getIntent().getStringExtra("access_token");
        videoName = getIntent().getStringExtra("video_name");
        apiDuration = getIntent().getLongExtra("duration_ms", 0);
        currentQuality = getIntent().getIntExtra("quality", 0);
        itemImageTag = getIntent().getStringExtra("image_tag");

        Log.d(TAG, "videoUrl: " + videoUrl);
        Log.d(TAG, "itemId: " + itemId);
        Log.d(TAG, "serverUrl: " + serverUrl);
        Log.d(TAG, "accessToken present: " + (accessToken != null));
        Log.d(TAG, "videoName: " + videoName);
        Log.d(TAG, "apiDuration: " + apiDuration);

        if (videoName != null) titleText.setText(videoName);
        if (statsLabel != null) statsLabel.setText("0:00 / 0:00");

        savedPosition = 0;
        final int resumePosition = getIntent().getIntExtra("resume_position", 0);

        if (serverUrl != null && accessToken != null && itemId != null) {
            apiClient = new EmbyApiClient(serverUrl);
            apiClient.setCredentials(accessToken, getIntent().getStringExtra("user_id"));
            String existingSessionId = getIntent().getStringExtra("play_session_id");
            if (existingSessionId != null) {
                playSessionId = existingSessionId;
            } else {
                playSessionId = UUID.randomUUID().toString();
            }
            Log.d(TAG, "apiClient initialized, playSessionId: " + playSessionId);
        }

        if (resumePosition > 0 && apiClient != null) {
            seekOffset = resumePosition;
            videoUrl = apiClient.getPlaybackUrl(itemId, currentQuality, resumePosition, playSessionId);
            Log.d(TAG, "Resume URL with StartTimeTicks: " + videoUrl);
        }

        // Detect HLS URLs and use HlsPlaybackEngine
        useHlsEngine = (videoUrl != null && videoUrl.contains(".m3u8"));

        rootLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!resumeDialogShown) {
                    toggleControls();
                }
            }
        });

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        if (useHlsEngine) {
            setupHlsEngine(videoName);
        } else {
            setupVideoView(videoName, resumePosition);
        }
    }

    private void setupHlsEngine(final String videoName) {
        Log.d(TAG, "Using HlsPlaybackEngine for URL: " + videoUrl);
        hlsEngine = new HlsPlaybackEngine();
        hlsEngine.setDataSource(videoUrl);
        hlsEngine.setDisplay(videoView.getHolder());
        hlsEngine.setListener(new HlsPlaybackEngine.Listener() {
            @Override
            public void onPrepared(final int duration) {
                Log.d(TAG, "HlsEngine onPrepared, duration=" + duration);
                bufferProgress.setVisibility(View.GONE);
                int d = duration;
                if (d <= 0 && apiDuration > 0) {
                    d = (int) apiDuration;
                }
                if (d > 0) {
                    seekBar.setMax(d);
                    if (totalTimeText != null) totalTimeText.setText(formatTime(d));
                    if (remainingTimeText != null) {
                        remainingTimeText.setText("(-" + formatTime(d) + ")");
                    }
                    if (statsLabel != null) statsLabel.setText("0:00 / " + formatTime(d));
                }
                hlsEngine.start();
                playPauseBtn.setText("||");
                startProgressUpdater();
                startFadeControls();
                reportPlayStarted();
            }

            @Override
            public void onError(int what, int extra) {
                Log.e(TAG, "HlsEngine error: " + what + ", " + extra);
                bufferProgress.setVisibility(View.GONE);
                Toast.makeText(PlayerActivity.this, "HLS playback error: " + what, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCompletion() {
                Log.d(TAG, "HlsEngine completed");
                playPauseBtn.setText("\u25B6");
                reportPlayStopped();
            }

            @Override
            public void onInfo(String message) {
                Log.d(TAG, "HlsEngine: " + message);
            }
        });

        resumeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resumeDialogShown = true;
                userResumed = true;
                resumeDialogLayout.setVisibility(View.GONE);
                hlsEngine.seekTo(savedPosition);
                hlsEngine.start();
                playPauseBtn.setText("||");
                startProgressUpdater();
                startFadeControls();
            }
        });

        startOverBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resumeDialogShown = true;
                userResumed = false;
                resumeDialogLayout.setVisibility(View.GONE);
                hlsEngine.start();
                playPauseBtn.setText("||");
                startProgressUpdater();
                startFadeControls();
            }
        });

        hlsEngine.prepareAsync();
    }

    private void setupVideoView(final String videoName, final int resumePosition) {
        Log.d(TAG, "Using VideoView for URL: " + videoUrl);

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d(TAG, "onPrepared() called");
                bufferProgress.setVisibility(View.GONE);
                int duration = videoView.getDuration();

                Log.d(TAG, "VideoView duration from VideoView: " + duration);
                if (duration <= 0) {
                    try {
                        duration = mp.getDuration();
                        Log.i(TAG, "VideoView duration from MediaPlayer: " + duration);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting MediaPlayer duration: " + e.getMessage());
                    }
                }

                if (duration <= 0 && apiDuration > 0) {
                    duration = (int) apiDuration;
                    Log.d(TAG, "Using API duration: " + duration);
                }
                if (duration > 0) {
                    seekBar.setMax(duration);
                    if (totalTimeText != null) totalTimeText.setText(formatTime(duration));
                    if (remainingTimeText != null) {
                        remainingTimeText.setText("(-" + formatTime(duration) + ")");
                    }
                    if (statsLabel != null) statsLabel.setText(formatTime(seekOffset) + " / " + formatTime(duration));
                }

                videoView.start();
                playPauseBtn.setText("||");

                if (seekOffset > 0) {
                    seekBar.setProgress(seekOffset);
                    currentTimeText.setText(formatTime(seekOffset));
                }

                startProgressUpdater();
                startFadeControls();

                reportPlayStarted();

                mp.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                    @Override
                    public void onSeekComplete(MediaPlayer mp) {
                        Log.i(TAG, "Seek complete, position: " + videoView.getCurrentPosition());
                    }
                });
            }
        });

        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                bufferProgress.setVisibility(View.GONE);
                Toast.makeText(PlayerActivity.this, "Playback error: " + what, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d(TAG, "onCompletion() called");
                playPauseBtn.setText("\u25B6");
                reportPlayStopped();
            }
        });

        resumeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resumeDialogShown = true;
                userResumed = true;
                resumeDialogLayout.setVisibility(View.GONE);
                seekToPosition(savedPosition);
            }
        });

        startOverBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resumeDialogShown = true;
                userResumed = false;
                resumeDialogLayout.setVisibility(View.GONE);
                videoView.start();
                playPauseBtn.setText("||");
                startProgressUpdater();
                startFadeControls();
            }
        });
        // Button handlers (shared by both HLS and VideoView modes)
        setupButtonHandlers();
    }

    private void setupButtonHandlers() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTimeText.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                isTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                int progress = sb.getProgress();
                Log.i(TAG, "Seek to: " + progress);
                try {
                    if (useHlsEngine) {
                        hlsEngine.seekTo(progress);
                    } else {
                        seekToPosition(progress);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Seek error: " + e.getMessage());
                }
                isTracking = false;
            }
        });

        playPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean playing = useHlsEngine ? hlsEngine.isPlaying() : videoView.isPlaying();
                if (playing) {
                    if (useHlsEngine) { hlsEngine.pause(); } else { videoView.pause(); }
                    playPauseBtn.setText("\u25B6");
                } else {
                    if (useHlsEngine) { hlsEngine.start(); } else { videoView.start(); }
                    playPauseBtn.setText("||");
                }
            }
        });

        rewindBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = useHlsEngine ? hlsEngine.getCurrentPosition() : videoView.getCurrentPosition() + seekOffset;
                int newPos = Math.max(0, pos - 10000);
                if (useHlsEngine) { hlsEngine.seekTo(newPos); } else { seekToPosition(newPos); }
            }
        });

        forwardBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = useHlsEngine ? hlsEngine.getCurrentPosition() : videoView.getCurrentPosition() + seekOffset;
                int dur = useHlsEngine ? hlsEngine.getDuration() : videoView.getDuration();
                if (dur <= 0) dur = (int) apiDuration;
                int newPos = Math.min(dur, pos + 10000);
                if (useHlsEngine) { hlsEngine.seekTo(newPos); } else { seekToPosition(newPos); }
            }
        });
    }

    private void startPlayback() {
        Log.d(TAG, "startPlayback() called for URL: " + videoUrl);
        bufferProgress.setVisibility(View.VISIBLE);
        if (!useHlsEngine) {
            videoView.setVideoURI(Uri.parse(videoUrl));
        }
    }

    private void seekToPosition(int positionMs) {
        if (useHlsEngine) {
            hlsEngine.seekTo(positionMs);
            return;
        }
        Log.i(TAG, "Server-side seek to: " + positionMs + "ms");
        seekOffset = positionMs;
        seekBar.setProgress(positionMs);
        currentTimeText.setText(formatTime(positionMs));
        bufferProgress.setVisibility(View.VISIBLE);
        String newUrl = apiClient.getPlaybackUrl(itemId, currentQuality, positionMs, null);
        Log.d(TAG, "New seek URL: " + newUrl);
        videoView.stopPlayback();
        videoView.setVideoURI(Uri.parse(newUrl));
        videoView.start();
    }

    private int getSavedPosition() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getInt("position_" + itemId, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private void savePosition(int position) {
        if (position <= 0) return;
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int oldVal = prefs.getInt("position_" + itemId, 0);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("position_" + itemId, position);
            editor.apply();
            if (position > oldVal + 1000 || position < oldVal - 1000) {
                Log.i(TAG, "savePosition: " + itemId + " " + oldVal + " -> " + position);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving position: " + e.getMessage());
        }
    }

    private void reportPlayStarted() {
        Log.d(TAG, "reportPlayStarted() called");
        playMethod = useHlsEngine ? "Transcode" : "DirectStream";
        if (apiClient != null && itemId != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "Reporting playback started for itemId: " + itemId);
                    boolean result = apiClient.reportPlaybackStarted(itemId, 0, true, playMethod, playSessionId);
                    Log.d(TAG, "Playback started report result: " + result);
                }
            }).start();
        }
    }

    private void reportPlayProgress(final long positionTicks) {
        if (apiClient != null && itemId != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    apiClient.reportPlaybackProgress(itemId, positionTicks, true, "TimeUpdate", playSessionId, true, playMethod);
                }
            }).start();
        }
    }

    private void reportPlayStopped() {
        if (stopping) return;
        stopping = true;
        Log.d(TAG, "reportPlayStopped() called");
        handler.removeCallbacksAndMessages(null);
        if (apiClient != null && itemId != null) {
            final int pos;
            if (useHlsEngine) {
                pos = (hlsEngine != null) ? hlsEngine.getCurrentPosition() : 0;
            } else {
                pos = (videoView != null) ? videoView.getCurrentPosition() + seekOffset : 0;
            }
            if (pos > 0) {
                savePosition(pos);
                try {
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    if (videoName != null) editor.putString("name_" + itemId, videoName);
                    if (itemImageTag != null) editor.putString("image_" + itemId, itemImageTag);
                    editor.putLong("time_" + itemId, System.currentTimeMillis());
                    editor.apply();
                } catch (Exception e) { }
            }
            Log.d(TAG, "Saving playback position to server: " + pos);
            final long posTicks = pos * 10000L;
            final String pid = playSessionId;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (posTicks > 0) {
                        apiClient.reportPlaybackProgress(itemId, posTicks, false, "Stop", pid, true, playMethod);
                    }
                }
            }).start();
        }
    }
    
    private void killEncoding(final String pid) {
        if (apiClient == null || pid == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                apiClient.deleteActiveEncoding(pid);
            }
        }).start();
    }

    private void startProgressUpdater() {
        handler.postDelayed(new Runnable() {
            private int reportCounter = 0;

            @Override
            public void run() {
                if (!isTracking) {
                    int pos = 0;
                    int duration = 0;
                    if (useHlsEngine) {
                        pos = hlsEngine.getCurrentPosition();
                        duration = hlsEngine.getDuration();
                    } else if (videoView != null) {
                        pos = videoView.getCurrentPosition() + seekOffset;
                        duration = videoView.getDuration();
                    }
                    if (duration <= 0 && apiDuration > 0) {
                        duration = (int) apiDuration;
                    }
                    seekBar.setProgress(pos);
                    Log.d(TAG, "Updating seekbar to pos=" + pos + " (stream=" + (pos - seekOffset) + " offset=" + seekOffset + ")");
                    currentTimeText.setText(formatTime(pos));
                    if (duration > 0) {
                        if (totalTimeText != null) totalTimeText.setText(formatTime(duration));
                        if (remainingTimeText != null) {
                            remainingTimeText.setText("(-" + formatTime(duration - pos) + ")");
                        }
                        if (statsLabel != null) {
                            statsLabel.setText(formatTime(pos) + " / " + formatTime(duration));
                        }
                    }

                    savePosition(pos);

                    reportCounter++;
                    if (reportCounter >= 10) {
                        reportCounter = 0;
                        reportPlayProgress(pos * 10000L);
                    }
                }
                handler.postDelayed(this, 500);
            }
        }, 500);
    }

    private String formatTime(int ms) {
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes >= 60) {
            int hours = minutes / 60;
            minutes = minutes % 60;
            return hours + ":" + String.format("%02d", minutes) + ":" + String.format("%02d", seconds);
        }
        return minutes + ":" + String.format("%02d", seconds);
    }

    private void startFadeControls() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (controlsVisible && videoView.isPlaying()) {
                    hideControls();
                }
            }
        }, FADE_DELAY);
    }

    private void toggleControls() {
        if (controlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        controlsVisible = true;
        controlsLayout.setVisibility(View.VISIBLE);
        backBtn.setVisibility(View.VISIBLE);
        if (videoView != null && videoView.isPlaying()) {
            startFadeControls();
        }
    }

    private void hideControls() {
        controlsVisible = false;
        controlsLayout.setVisibility(View.GONE);
        backBtn.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (useHlsEngine) {
            if (hlsEngine != null && hlsEngine.isPlaying()) {
                hlsEngine.pause();
            }
        } else {
            if (videoView != null && videoView.isPlaying()) {
                int p = videoView.getCurrentPosition() + seekOffset;
                savePosition(p);
                videoView.pause();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (!useHlsEngine && videoView != null) {
            int p = videoView.getCurrentPosition() + seekOffset;
            savePosition(p);
        }
        reportPlayStopped();
        killEncoding(playSessionId);
        finish();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Save position but do NOT kill encoding — user may return
        if (!useHlsEngine && videoView != null && videoView.isPlaying()) {
            int p = videoView.getCurrentPosition() + seekOffset;
            savePosition(p);
            videoView.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isFinishing()) {
            killEncoding(playSessionId);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!useHlsEngine && videoView != null) {
            try {
                int finalPos = videoView.getCurrentPosition() + seekOffset;
                if (finalPos > 0) savePosition(finalPos);
            } catch (Exception e) { }
        }
        handler.removeCallbacksAndMessages(null);
        reportPlayStopped();
        killEncoding(playSessionId);
        if (useHlsEngine) {
            if (hlsEngine != null) {
                hlsEngine.release();
                hlsEngine = null;
            }
        } else {
            if (videoView != null) {
                videoView.stopPlayback();
            }
        }
    }
}