package com.emby.client;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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

public class PlayerActivity extends Activity {
    private VideoView videoView;
    private SeekBar seekBar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView remainingTimeText;
    private TextView titleText;
    private TextView qualityLabel;
    private TextView statsLabel;
    private Button playPauseBtn;
    private Button rewindBtn;
    private Button forwardBtn;
    private ProgressBar bufferProgress;
    private LinearLayout controlsLayout;
    private FrameLayout rootLayout;
    private View backBtn;
    private View externalPlayerBtn;
    private View resumeDialogLayout;
    private View resumeBtn;
    private View startOverBtn;
    private TextView resumeTimeLabel;

    private Handler handler = new Handler();
    private boolean isTracking = false;
    private boolean controlsVisible = true;
    private static final int FADE_DELAY = 3000;
    private static final String TAG = "Player";
    private static final String PREFS_NAME = "EmbyPlaybackPrefs";
    
    private String itemId;
    private String videoUrl;
    private int savedPosition = 0;
    private long apiDuration = 0;
    private boolean resumeDialogShown = false;
    private boolean userResumed = false;
    private String serverUrl;
    private String accessToken;

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
        qualityLabel = (TextView) findViewById(R.id.qualityLabel);
        statsLabel = (TextView) findViewById(R.id.statsLabel);
        playPauseBtn = (Button) findViewById(R.id.playPauseBtn);
        rewindBtn = (Button) findViewById(R.id.rewindBtn);
        forwardBtn = (Button) findViewById(R.id.forwardBtn);
        bufferProgress = (ProgressBar) findViewById(R.id.bufferProgress);
        controlsLayout = (LinearLayout) findViewById(R.id.controlsLayout);
        backBtn = findViewById(R.id.backBtn);
        externalPlayerBtn = findViewById(R.id.externalPlayerBtn);
        resumeDialogLayout = findViewById(R.id.resumeDialogLayout);
        resumeBtn = findViewById(R.id.resumeBtn);
        startOverBtn = findViewById(R.id.startOverBtn);
        resumeTimeLabel = (TextView) findViewById(R.id.resumeTimeLabel);
        
        videoUrl = getIntent().getStringExtra("video_url");
        itemId = getIntent().getStringExtra("item_id");
        serverUrl = getIntent().getStringExtra("server_url");
        accessToken = getIntent().getStringExtra("access_token");
        final String videoName = getIntent().getStringExtra("video_name");
        final String quality = getIntent().getStringExtra("quality");
        apiDuration = getIntent().getLongExtra("duration_ms", 0);
        
        if (videoName != null) titleText.setText(videoName);
        if (quality != null) qualityLabel.setText(quality);
        if (statsLabel != null) statsLabel.setText("0:00 / 0:00");
        
        savedPosition = 0;
        
        rootLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!resumeDialogShown) {
                    toggleControls();
                }
            }
        });
        
        resumeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resumeDialogShown = true;
                userResumed = true;
                resumeDialogLayout.setVisibility(View.GONE);
                videoView.seekTo(savedPosition);
                videoView.start();
                playPauseBtn.setText("Pause");
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
                videoView.start();
                playPauseBtn.setText("Pause");
                startProgressUpdater();
                startFadeControls();
            }
        });
        
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                bufferProgress.setVisibility(View.GONE);
                int duration = videoView.getDuration();
                Log.i(TAG, "VideoView duration from VideoView: " + duration);
                
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
                    Log.i(TAG, "Using API duration: " + duration);
                }
                if (duration > 0) {
                    seekBar.setMax(duration);
                    totalTimeText.setText(formatTime(duration));
                    if (remainingTimeText != null) {
                        remainingTimeText.setText("(-" + formatTime(duration) + ")");
                    }
                    if (statsLabel != null) statsLabel.setText("0:00 / " + formatTime(duration));
                } else {
                    Log.w(TAG, "No duration available - duration: " + duration + ", apiDuration: " + apiDuration);
                }
                
                if (pendingSeekPosition > 0) {
                    Log.i(TAG, "Applying pending seek to: " + pendingSeekPosition);
                    videoView.seekTo(pendingSeekPosition);
                    pendingSeekPosition = -1;
                }
                
                videoView.start();
                playPauseBtn.setText("Pause");
                startProgressUpdater();
                startFadeControls();
                
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
                playPauseBtn.setText("Play");
            }
        });

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
                    videoView.pause();
                    videoView.seekTo(progress);
                    videoView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            videoView.start();
                            videoView.invalidate();
                        }
                    }, 200);
                    Log.i(TAG, "After seek, position: " + videoView.getCurrentPosition());
                } catch (Exception e) {
                    Log.e(TAG, "Seek error: " + e.getMessage());
                }
                isTracking = false;
            }
        });

        playPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoView.isPlaying()) {
                    videoView.pause();
                    playPauseBtn.setText("Play");
                } else {
                    videoView.start();
                    playPauseBtn.setText("Pause");
                }
            }
        });

        rewindBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(PlayerActivity.this, "Skip disabled for now", Toast.LENGTH_SHORT).show();
            }
        });

        forwardBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(PlayerActivity.this, "Skip disabled for now", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private int pendingSeekPosition = -1;

private void startPlayback() {
        String quality = getIntent().getStringExtra("quality");
        
        bufferProgress.setVisibility(View.VISIBLE);
        videoView.setVideoURI(Uri.parse(videoUrl));
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
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("position_" + itemId, position);
            editor.commit();
        } catch (Exception e) {
            Log.e(TAG, "Error saving position: " + e.getMessage());
        }
    }

    private void startProgressUpdater() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isTracking && videoView != null) {
                    int pos = videoView.getCurrentPosition();
                    int duration = videoView.getDuration();
                    if (duration <= 0 && apiDuration > 0) {
                        duration = (int) apiDuration;
                    }
                    seekBar.setProgress(pos);
                    currentTimeText.setText(formatTime(pos));
                    if (duration > 0) {
                        totalTimeText.setText(formatTime(duration));
                        if (remainingTimeText != null) {
                            remainingTimeText.setText("(-" + formatTime(duration - pos) + ")");
                        }
                        if (statsLabel != null) {
                            statsLabel.setText(formatTime(pos) + " / " + formatTime(duration));
                        }
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
    
    private String formatTimeRemaining(int pos, int duration) {
        int remaining = duration - pos;
        if (remaining < 0) remaining = 0;
        return "-" + formatTime(remaining);
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
        externalPlayerBtn.setVisibility(View.VISIBLE);
        if (videoView != null && videoView.isPlaying()) {
            startFadeControls();
        }
    }

    private void hideControls() {
        controlsVisible = false;
        controlsLayout.setVisibility(View.GONE);
        backBtn.setVisibility(View.GONE);
        externalPlayerBtn.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }
}