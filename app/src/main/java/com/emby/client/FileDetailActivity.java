package com.emby.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;
import java.util.HashMap;

public class FileDetailActivity extends Activity {
    private static final String TAG = "FileDetail";
    
    private Map<String, Bitmap> imageCache = new HashMap<String, Bitmap>();
    private static final int IMAGE_CACHE_SIZE = 10;
    
    private ImageView posterImage;
    private TextView titleText;
    private TextView yearText;
    private TextView durationText;
    private TextView plotText;
    private TextView genresText;
    private TextView ratingText;
    private Button playButton;
    private ProgressBar loadingProgress;
    private View infoLayout;
    private View movieDetailsLayout;
    private String itemType;
    private EmbyApiClient apiClient;
    private String itemId;
    private String itemName;
    private String serverUrl;
    private String accessToken;
    
    private static final String[] QUALITY_OPTIONS = {
        "Original (Direct Play)",
        "1080p HD (~2Mbps)",
        "720p HD (~1.5Mbps)",
        "576p SD (~1Mbps)",
        "480p SD (~800Kbps)",
        "360p Low (~400Kbps)"
    };
    
    private static final String[] QUALITY_DESCRIPTIONS = {
        "Best quality, direct stream",
        "High quality HD",
        "Standard HD",
        "DVD quality",
        "Compressed",
        "Lowest bandwidth"
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_detail);
        
        initViews();
        
        itemId = getIntent().getStringExtra("item_id");
        itemName = getIntent().getStringExtra("item_name");
        serverUrl = getIntent().getStringExtra("server_url");
        accessToken = getIntent().getStringExtra("access_token");
        itemType = getIntent().getStringExtra("item_type");
        String imageTag = getIntent().getStringExtra("image_tag");
        String userId = getIntent().getStringExtra("user_id");
        
        if (itemId == null || serverUrl == null || accessToken == null) {
            Toast.makeText(this, "Missing item info", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        apiClient = new EmbyApiClient(serverUrl);
        apiClient.setCredentials(accessToken, userId != null ? userId : "unknown");
        
        loadingProgress.setVisibility(View.GONE);
        infoLayout.setVisibility(View.VISIBLE);
        titleText.setText(itemName);
        
        if (movieDetailsLayout != null) {
            movieDetailsLayout.setVisibility(View.VISIBLE);
        }
        
        if (imageTag != null && !imageTag.isEmpty()) {
            loadPosterImage(imageTag);
        }
        loadItemDetails();
    }
    
    private void initViews() {
        posterImage = (ImageView) findViewById(R.id.posterImage);
        titleText = (TextView) findViewById(R.id.titleText);
        yearText = (TextView) findViewById(R.id.yearText);
        durationText = (TextView) findViewById(R.id.durationText);
        plotText = (TextView) findViewById(R.id.plotText);
        genresText = (TextView) findViewById(R.id.genresText);
        ratingText = (TextView) findViewById(R.id.ratingText);
        playButton = (Button) findViewById(R.id.playButton);
        loadingProgress = (ProgressBar) findViewById(R.id.loadingProgress);
        infoLayout = findViewById(R.id.infoLayout);
        movieDetailsLayout = findViewById(R.id.movieDetailsLayout);
        
        if (movieDetailsLayout != null) {
            movieDetailsLayout.setVisibility(View.VISIBLE);
        }
        
        findViewById(R.id.backBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQualityDialog();
            }
        });
    }
    
    private void showQualityDialog() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select Quality");
            
            String[] displayOptions = new String[QUALITY_OPTIONS.length];
            for (int i = 0; i < QUALITY_OPTIONS.length; i++) {
                displayOptions[i] = QUALITY_OPTIONS[i] + " - " + QUALITY_DESCRIPTIONS[i];
            }
            
            builder.setSingleChoiceItems(displayOptions, 0, null);
            builder.setPositiveButton("Play", new AlertDialog.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        int selectedIndex = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                        playMedia(selectedIndex);
                    } catch (Exception e) {
                        Log.e(TAG, "Error playing: " + e.getMessage(), e);
                    }
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing dialog: " + e.getMessage(), e);
            playMedia(0);
        }
    }
    
    private void playMedia(int quality) {
        String playbackUrl = apiClient.getPlaybackUrl(itemId, quality);
        
        Intent intent = new Intent(FileDetailActivity.this, PlayerActivity.class);
        intent.putExtra("video_url", playbackUrl);
        intent.putExtra("video_name", itemName);
        intent.putExtra("item_id", itemId);
        intent.putExtra("duration_ms", lastKnownDuration);
        intent.putExtra("server_url", serverUrl);
        intent.putExtra("access_token", accessToken);
        startActivity(intent);
    }
    
    private long lastKnownDuration = 0;
    
    private void loadItemDetails() {
        loadingProgress.setVisibility(View.VISIBLE);
        infoLayout.setVisibility(View.GONE);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    EmbyApiClient.MediaItemInfo info = apiClient.getItemInfo(itemId);
                    
                    if (info != null) {
                        final long runTimeTicks = info.runTimeTicks;
                        final String year = info.productionYear > 0 ? String.valueOf(info.productionYear) : "";
                        final String duration = formatDuration(runTimeTicks);
                        final String plot = info.plot != null ? info.plot : "";
                        final String genres = info.genres != null ? info.genres : "";
                        final String rating = info.communityRating > 0 ? String.format("%.1f", info.communityRating) : "";
                        final String posterTag = info.primaryImageTag;
                        final String type = info.type;
                        
                        Log.d(TAG, "Loaded info - year: " + year + ", duration: " + duration + ", plot: " + plot + ", genres: " + genres + ", rating: " + rating);
                        
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                lastKnownDuration = runTimeTicks / 10000;
                                
                                loadingProgress.setVisibility(View.GONE);
                                infoLayout.setVisibility(View.VISIBLE);
                                
                                yearText.setText(year);
                                durationText.setText(duration);
                                plotText.setText(plot);
                                genresText.setText(genres);
                                ratingText.setText(rating);
                                
                                Log.d(TAG, "UI updated - year: '" + year + "', duration: '" + duration + "'");
                                
                                if (movieDetailsLayout != null) {
                                    movieDetailsLayout.setVisibility(View.VISIBLE);
                                }
                                
                                if (posterTag != null && !posterTag.isEmpty()) {
                                    loadPosterImage(posterTag);
                                }
                            }
                        });
                    } else {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                loadingProgress.setVisibility(View.GONE);
                                infoLayout.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading details: " + e.getMessage());
                    Log.e(TAG, "Item ID was: " + itemId);
                    Toast.makeText(FileDetailActivity.this, "Failed to load details.\nID: " + itemId, Toast.LENGTH_SHORT).show();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            loadingProgress.setVisibility(View.GONE);
                            infoLayout.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        }).start();
    }
    
    private void loadPosterImage(final String imageTag) {
        final String cacheKey = itemId + "_" + imageTag;
        
        if (imageCache.containsKey(cacheKey)) {
            posterImage.setImageBitmap(imageCache.get(cacheKey));
            return;
        }
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = apiClient.getImageUrl(itemId, imageTag, 400, 600);
                    java.net.URL urlObj = new java.net.URL(url);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
                    conn.setDoInput(true);
                    conn.setConnectTimeout(10000);
                    conn.connect();
                    java.io.InputStream input = conn.getInputStream();
                    Bitmap bmp = BitmapFactory.decodeStream(input);
                    
                    if (bmp != null) {
                        if (imageCache.size() >= IMAGE_CACHE_SIZE) {
                            imageCache.clear();
                        }
                        imageCache.put(cacheKey, bmp);
                        
                        final Bitmap finalBmp = bmp;
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                posterImage.setImageBitmap(finalBmp);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading poster: " + e.getMessage());
                }
            }
        }).start();
    }
    
    private String formatDuration(long ticks) {
        if (ticks <= 0) return "";
        long minutes = (ticks / 10000) / 60000;
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + " min";
    }
}
