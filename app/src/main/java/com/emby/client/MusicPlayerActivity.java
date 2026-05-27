package com.emby.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayerActivity extends Activity {
    private static final String TAG = "MusicPlayer";
    private static final String PREFS_NAME = "EmbyPlaybackPrefs";

    private ImageButton backBtn;
    private ImageButton prevBtn;
    private ImageButton playPauseBtn;
    private ImageButton nextBtn;
    private TextView albumTitle;
    private TextView currentTrackName;
    private TextView currentTrackInfo;
    private SeekBar progressBar;
    private TextView elapsedTime;
    private TextView remainingTime;
    private ListView trackListView;

    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<EmbyApiClient.MediaItem> trackList = new ArrayList<EmbyApiClient.MediaItem>();
    private int currentIndex = 0;
    private boolean isPlaying = false;

    private String serverUrl;
    private String accessToken;
    private String albumId;
    private String albumName;
    private EmbyApiClient apiClient;
    private int resumePosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);

        initViews();

        serverUrl = getIntent().getStringExtra("server_url");
        accessToken = getIntent().getStringExtra("access_token");
        String userId = getIntent().getStringExtra("user_id");
        albumId = getIntent().getStringExtra("album_id");
        albumName = getIntent().getStringExtra("album_name");

        if (serverUrl == null || accessToken == null) {
            Log.e(TAG, "Missing server URL or access token");
            finish();
            return;
        }

        apiClient = new EmbyApiClient(serverUrl);
        apiClient.setCredentials(accessToken, userId != null ? userId : "");

        albumTitle.setText(albumName != null ? albumName : "Music");

        if (albumId == null || albumId.length() == 0) {
            Log.e(TAG, "No album ID provided");
            currentTrackName.setText("No album context");
            return;
        }

        loadTrackList();
    }

    private void initViews() {
        backBtn = (ImageButton) findViewById(R.id.backBtn);
        prevBtn = (ImageButton) findViewById(R.id.prevBtn);
        playPauseBtn = (ImageButton) findViewById(R.id.playPauseBtn);
        nextBtn = (ImageButton) findViewById(R.id.nextBtn);
        albumTitle = (TextView) findViewById(R.id.albumTitle);
        currentTrackName = (TextView) findViewById(R.id.currentTrackName);
        currentTrackInfo = (TextView) findViewById(R.id.currentTrackInfo);
        progressBar = (SeekBar) findViewById(R.id.progressBar);
        elapsedTime = (TextView) findViewById(R.id.elapsedTime);
        remainingTime = (TextView) findViewById(R.id.remainingTime);
        trackListView = (ListView) findViewById(R.id.trackListView);

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPlayback();
                finish();
            }
        });

        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentIndex > 0) {
                    playTrack(currentIndex - 1);
                }
            }
        });

        playPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaPlayer == null) return;
                if (isPlaying) {
                    mediaPlayer.pause();
                    isPlaying = false;
                    playPauseBtn.setImageResource(android.R.drawable.ic_media_play);
                } else {
                    mediaPlayer.start();
                    isPlaying = true;
                    playPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
                }
            }
        });

        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentIndex < trackList.size() - 1) {
                    playTrack(currentIndex + 1);
                }
            }
        });
    }

    private void loadTrackList() {
        if (apiClient == null) return;
        Log.i(TAG, "Loading tracks for albumId=" + albumId);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<EmbyApiClient.MediaItem> items = apiClient.getMediaItems(albumId);
                    Log.i(TAG, "getMediaItems returned " + items.size() + " items for albumId=" + albumId);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            trackList.clear();
                            for (EmbyApiClient.MediaItem item : items) {
                                Log.d(TAG, "  item: " + item.name + " isFolder=" + item.isFolder + " type=" + item.type);
                                if (!item.isFolder) {
                                    trackList.add(item);
                                }
                            }
                            Log.i(TAG, "trackList has " + trackList.size() + " non-folder items");
                            if (trackList.isEmpty()) {
                                currentTrackName.setText("No tracks found");
                                return;
                            }
                            int startIndex = getIntent().getIntExtra("track_index", 0);
                            if (startIndex >= trackList.size()) startIndex = 0;
                            currentIndex = startIndex;
                            setupTrackList();
                            playTrack(currentIndex);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error loading tracks: " + e.getMessage(), e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            currentTrackName.setText("Failed to load tracks");
                        }
                    });
                }
            }
        }).start();
    }

    private void setupTrackList() {
        trackListView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() { return trackList.size(); }

            @Override
            public Object getItem(int i) { return trackList.get(i); }

            @Override
            public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View v, ViewGroup p) {
                if (v == null) {
                    v = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
                }
                TextView tv = (TextView) v;
                EmbyApiClient.MediaItem item = trackList.get(i);
                String prefix = item.indexNumber >= 0 ? item.indexNumber + ". " : "";
                tv.setText(prefix + item.name);
                tv.setTextColor(i == currentIndex ? 0xFFFFAA00 : 0xFFB3B3B3);
                tv.setTextSize(14);
                tv.setPadding(16, 10, 16, 10);
                return v;
            }
        });

        trackListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                playTrack(position);
            }
        });
    }

    private void playTrack(int index) {
        if (index < 0 || index >= trackList.size()) return;
        if (apiClient == null) return;
        stopPlayback();
        currentIndex = index;
        EmbyApiClient.MediaItem track = trackList.get(index);

        currentTrackName.setText(track.name);
        String info = track.indexNumber >= 0 ? "Track " + track.indexNumber : "";
        currentTrackInfo.setText(info);
        elapsedTime.setText("0:00");
        remainingTime.setText("0:00");
        progressBar.setProgress(0);

        String url = apiClient.getAudioUrl(track.id);
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (resumePosition > 0 && resumePosition < mp.getDuration()) {
                        mp.seekTo(resumePosition);
                        resumePosition = 0;
                    }
                    mp.start();
                    isPlaying = true;
                    playPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
                    startProgressUpdater();
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (currentIndex < trackList.size() - 1) {
                        playTrack(currentIndex + 1);
                    } else {
                        isPlaying = false;
                        playPauseBtn.setImageResource(android.R.drawable.ic_media_play);
                    }
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                    return true;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Log.e(TAG, "Error playing track: " + e.getMessage());
        }

        trackListView.invalidateViews();
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            try {
                savePosition(mediaPlayer.getCurrentPosition());
            } catch (Exception e) { }
            mediaPlayer.release();
            mediaPlayer = null;
            isPlaying = false;
        }
    }

    private void startProgressUpdater() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && isPlaying) {
                    try {
                        int pos = mediaPlayer.getCurrentPosition();
                        int dur = mediaPlayer.getDuration();
                        savePosition(pos);
                        elapsedTime.setText(formatTime(pos));
                        remainingTime.setText("-" + formatTime(dur - pos));
                        if (dur > 0) {
                            progressBar.setProgress((int) ((long) pos * 1000 / dur));
                        }
                    } catch (Exception e) { }
                    handler.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }

    private String formatTime(int ms) {
        int totalSec = ms / 1000;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    private void savePosition(int pos) {
        if (pos <= 0) return;
        try {
            EmbyApiClient.MediaItem track = trackList.get(currentIndex);
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("position_" + track.id, pos);
            editor.apply();
            Log.i(TAG, "Saved position for " + track.id + ": " + pos);
        } catch (Exception e) { }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        stopPlayback();
    }
}
