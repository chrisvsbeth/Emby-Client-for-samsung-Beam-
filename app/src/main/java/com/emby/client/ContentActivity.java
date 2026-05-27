package com.emby.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.ProgressBar;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;

import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class ContentActivity extends Activity {
    private static final String TAG = "EmbyClient";

    private static final String[] QUALITY_OPTIONS = {
        "Original (Direct Play)",
        "1080p HD (~2Mbps)",
        "720p HD (~1.5Mbps)",
        "576p SD (~1Mbps)",
        "480p SD (~800Kbps)",
        "360p Low (~400Kbps)"
    };

    private static final String[] QUALITY_DESCRIPTIONS = {
        "Stream original file directly",
        "720p to 1080p, good quality",
        "Medium quality, data saving",
        "Standard quality, moderate",
        "Lower quality, saves data",
        "Lowest quality, minimal data"
    };

    private ImageView headerLogo;
    private TextView headerTitle;
    private ImageButton backBtn;
    private ImageButton searchBtn;
    private ImageButton refreshBtn;
    private LinearLayout searchLayout;
    private EditText searchInput;
    private ImageButton clearSearchBtn;
    private ListView contentList;
    private ProgressBar loadingProgress;
    private TextView emptyText;
    private List<EmbyApiClient.MediaItem> recentItems = new ArrayList<EmbyApiClient.MediaItem>();
    private List<EmbyApiClient.MediaItem> resumeItems = new ArrayList<EmbyApiClient.MediaItem>();
    private boolean isRootView = false;

    private static final int IMAGE_CACHE_SIZE = 20;
    private Map<String, Bitmap> imageCache = new HashMap<String, Bitmap>();

    private List<EmbyApiClient.MediaItem> mediaItems = new ArrayList<EmbyApiClient.MediaItem>();
    private List<EmbyApiClient.MediaItem> filteredItems = new ArrayList<EmbyApiClient.MediaItem>();
    private List<EmbyApiClient.MediaItem> allItems = new ArrayList<EmbyApiClient.MediaItem>();
    private EmbyApiClient apiClient;
    private Stack<String> folderStack = new Stack<String>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "ContentActivity.onCreate() started");

        try {
            setContentView(R.layout.activity_content);
            initViews();
            initializeClient();
            loadMediaItems(null);
        } catch (Exception e) {
            Log.e(TAG, "Exception in ContentActivity.onCreate(): " + e.getClass().getName(), e);
            Toast.makeText(this, "Error loading content: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initViews() {
        headerLogo = (ImageView) findViewById(R.id.headerLogo);
        headerTitle = (TextView) findViewById(R.id.headerTitle);
        backBtn = (ImageButton) findViewById(R.id.backBtn);
        searchBtn = (ImageButton) findViewById(R.id.searchBtn);
        refreshBtn = (ImageButton) findViewById(R.id.refreshBtn);
        searchLayout = (LinearLayout) findViewById(R.id.searchLayout);
        searchInput = (EditText) findViewById(R.id.searchInput);
        clearSearchBtn = (ImageButton) findViewById(R.id.clearSearchBtn);
        contentList = (ListView) findViewById(R.id.contentList);
        loadingProgress = (ProgressBar) findViewById(R.id.loadingProgress);
        emptyText = (TextView) findViewById(R.id.emptyText);

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goBack();
            }
        });

        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSearch();
            }
        });

        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String currentFolder = folderStack.isEmpty() ? null : folderStack.peek();
                loadMediaItems(currentFolder);
            }
        });

        clearSearchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchInput.setText("");
                allItems.clear();
                allItems.addAll(mediaItems);
                filterItems("");
            }
        });

        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                if (query.length() >= 2 && allItems.isEmpty()) {
                    searchAllDirectories(query);
                } else {
                    filterItems(query);
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Items have their own click handlers set in displayFilteredItems()
    }

    private void loadAllMovies() {
        showLoading(true);
        isRootView = false;
        recentItems = new ArrayList<EmbyApiClient.MediaItem>();
        resumeItems = new ArrayList<EmbyApiClient.MediaItem>();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mediaItems = apiClient.getAllMovies();
                    Log.i(TAG, "Loaded all movies: " + mediaItems.size());

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            displayMediaItems();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error loading all movies: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            showError("Failed to load movies");
                        }
                    });
                }
            }
        }).start();
    }

    private void loadAllTvShows() {
        showLoading(true);
        isRootView = false;
        recentItems = new ArrayList<EmbyApiClient.MediaItem>();
        resumeItems = new ArrayList<EmbyApiClient.MediaItem>();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mediaItems = apiClient.getAllTvShows();
                    Log.i(TAG, "Loaded all TV shows: " + mediaItems.size());

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            displayMediaItems();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error loading TV shows: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            showError("Failed to load TV shows");
                        }
                    });
                }
            }
        }).start();
    }

    private void toggleSearch() {
        if (searchLayout.getVisibility() == View.VISIBLE) {
            searchLayout.setVisibility(View.GONE);
            searchInput.setText("");
            filterItems("");
        } else {
            searchLayout.setVisibility(View.VISIBLE);
            searchInput.requestFocus();
        }
    }

    private void filterItems(String query) {
        filteredItems.clear();
        if (query == null || query.trim().equals("")) {
            for (EmbyApiClient.MediaItem item : allItems) {
                filteredItems.add(item);
            }
        } else {
            String lowerQuery = query.toLowerCase();
            for (EmbyApiClient.MediaItem item : allItems) {
                if (item.name.toLowerCase().contains(lowerQuery)) {
                    filteredItems.add(item);
                }
            }
        }

        if (filteredItems.isEmpty() && !query.trim().equals("")) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText("No results found for: " + query);
            contentList.setVisibility(View.GONE);
        } else if (filteredItems.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText("Loading content...");
            contentList.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            contentList.setVisibility(View.VISIBLE);
        }

        displayFilteredItems();
    }

    private void displayFilteredItems() {
        Collections.sort(filteredItems, new Comparator<EmbyApiClient.MediaItem>() {
            public int compare(EmbyApiClient.MediaItem a, EmbyApiClient.MediaItem b) {
                if (a.indexNumber >= 0 && b.indexNumber >= 0) {
                    int sa = a.parentIndexNumber >= 0 ? a.parentIndexNumber : 0;
                    int sb = b.parentIndexNumber >= 0 ? b.parentIndexNumber : 0;
                    if (sa != sb) return sa - sb;
                    return a.indexNumber - b.indexNumber;
                }
                String nameA = a.name != null ? a.name.toLowerCase() : "";
                String nameB = b.name != null ? b.name.toLowerCase() : "";
                return nameA.compareTo(nameB);
            }
        });

        final boolean hasRecent = isRootView && recentItems != null && !recentItems.isEmpty();
        final boolean hasContinue = isRootView && resumeItems != null && !resumeItems.isEmpty();

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                int count = filteredItems.size();
                if (hasRecent) count++;
                if (hasContinue) count++;
                return count;
            }

            @Override
            public Object getItem(int position) { return position; }

            @Override
            public long getItemId(int position) { return position; }

            @Override
            public int getItemViewType(int position) {
                int idx = 0;
                if (hasRecent) { if (position == idx) return 0; idx++; }
                if (hasContinue) { if (position == idx) return 0; idx++; }
                return 1;
            }

            @Override
            public int getViewTypeCount() { return 2; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                int idx = 0;
                if (hasRecent) {
                    if (position == idx) {
                        if (convertView == null) {
                            convertView = buildHorizontalRowView("Recently Added", recentItems, false);
                        }
                        return convertView;
                    }
                    idx++;
                }
                if (hasContinue) {
                    if (position == idx) {
                        if (convertView == null) {
                            convertView = buildHorizontalRowView("Continue Watching", resumeItems, true);
                        }
                        return convertView;
                    }
                    idx++;
                }

                int itemPos = position - idx;
                if (itemPos < 0 || itemPos >= filteredItems.size()) {
                    return new View(ContentActivity.this);
                }

                final EmbyApiClient.MediaItem item = filteredItems.get(itemPos);
                if (convertView == null || getItemViewType(position) == 0) {
                    convertView = getLayoutInflater().inflate(R.layout.item_media, parent, false);
                }

                ImageView imageView = (ImageView) convertView.findViewById(R.id.itemImage);
                TextView nameView = (TextView) convertView.findViewById(R.id.itemName);
                TextView typeView = (TextView) convertView.findViewById(R.id.itemType);

                if (item.imageTag != null && item.imageTag.length() > 0) {
                    String imgId = item.imageItemId != null && item.imageItemId.length() > 0 ? item.imageItemId : item.id;
                    imageView.setTag(imgId);
                    imageView.setVisibility(View.VISIBLE);
                    imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imageView.clearColorFilter();
                    imageView.setImageBitmap(null);
                    new ListImageLoader(imageView, imgId).execute(imgId, item.imageTag);
                } else {
                    imageView.setVisibility(View.VISIBLE);
                    imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    if ("all_movies".equals(item.id)) {
                        imageView.setBackgroundResource(R.drawable.icon_bg_movies);
                        imageView.setImageResource(android.R.drawable.ic_menu_gallery);
                    } else if ("all_tvshows".equals(item.id)) {
                        imageView.setBackgroundResource(R.drawable.icon_bg_movies);
                        imageView.setImageResource(android.R.drawable.ic_menu_gallery);
                    } else if (item.isFolder) {
                        imageView.setBackgroundResource(R.drawable.icon_bg_folder);
                        imageView.setImageResource(android.R.drawable.ic_menu_sort_by_size);
                    } else {
                        imageView.setBackgroundResource(R.drawable.icon_bg_video);
                        imageView.setImageResource(android.R.drawable.ic_media_play);
                    }
                    imageView.setColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_ATOP);
                }

                nameView.setText(item.name);

                String typeText;
                if (item.indexNumber >= 0) {
                    int sn = item.parentIndexNumber >= 0 ? item.parentIndexNumber : 0;
                    typeText = "S" + (sn < 10 ? "0" : "") + sn + "E" + (item.indexNumber < 10 ? "0" : "") + item.indexNumber;
                } else {
                    typeText = item.isFolder ? ("all_movies".equals(item.id) ? "Movie Folder" : "Folder") : item.type;
                }
                typeView.setText(typeText);

                final TextView resumeBtn = (TextView) convertView.findViewById(R.id.resumeBtn);
                if (!item.isFolder && !"all_movies".equals(item.id)) {
                    SharedPreferences prefs = getSharedPreferences("EmbyPlaybackPrefs", Context.MODE_PRIVATE);
                    final int savedPos = prefs.getInt("position_" + item.id, 0);
                    if (savedPos > 5000) {
                        resumeBtn.setVisibility(View.VISIBLE);
                        resumeBtn.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent intent = new Intent(ContentActivity.this, FileDetailActivity.class);
                                intent.putExtra("item_id", item.id);
                                intent.putExtra("item_name", item.name);
                                intent.putExtra("image_tag", item.imageTag);
                                intent.putExtra("item_type", item.type);
                                intent.putExtra("server_url", apiClient.getServerUrl());
                                intent.putExtra("access_token", apiClient.getAccessToken());
                                intent.putExtra("user_id", apiClient.getUserId());
                                intent.putExtra("resume_position", savedPos);
                                startActivity(intent);
                            }
                        });
                    } else {
                        resumeBtn.setVisibility(View.GONE);
                    }
                } else {
                    resumeBtn.setVisibility(View.GONE);
                }

                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (item.id != null && item.id.equals("all_movies")) {
                            loadAllMovies();
                        } else if (item.id != null && item.id.equals("all_tvshows")) {
                            loadAllTvShows();
                        } else if (item.isFolder) {
                            openFolder(item.id, item.name);
                        } else if (item.type != null && item.type.equals("Audio")) {
                            String albumId = item.parentId;
                            if (albumId == null || albumId.equals("")) albumId = item.albumId;
                            if (albumId == null || albumId.equals("")) albumId = item.id;
                            int trackIndex = itemPos;
                            Intent intent = new Intent(ContentActivity.this, MusicPlayerActivity.class);
                            intent.putExtra("server_url", apiClient.getServerUrl());
                            intent.putExtra("access_token", apiClient.getAccessToken());
                            intent.putExtra("user_id", apiClient.getUserId());
                            intent.putExtra("album_id", albumId);
                            intent.putExtra("album_name", headerTitle.getText());
                            intent.putExtra("track_index", trackIndex);
                            startActivity(intent);
                        } else {
                            Intent intent = new Intent(ContentActivity.this, FileDetailActivity.class);
                            intent.putExtra("item_id", item.id);
                            intent.putExtra("item_name", item.name);
                            intent.putExtra("image_tag", item.imageTag);
                            intent.putExtra("item_type", item.type);
                            intent.putExtra("server_url", apiClient.getServerUrl());
                            intent.putExtra("access_token", apiClient.getAccessToken());
                            intent.putExtra("user_id", apiClient.getUserId());
                            startActivity(intent);
                        }
                    }
                });

                return convertView;
            }
        };

        contentList.setAdapter(adapter);
    }

    private View buildHorizontalRowView(String label, List<EmbyApiClient.MediaItem> items, final boolean isResume) {
        if (items == null || items.isEmpty()) return new View(this);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new ListView.LayoutParams(
            ListView.LayoutParams.MATCH_PARENT, ListView.LayoutParams.WRAP_CONTENT));

        TextView header = new TextView(this);
        header.setText(label);
        header.setTextColor(0xFFFFFFFF);
        header.setTextSize(15);
        header.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.setBackgroundColor(0xFF1A1A1A);
        header.setPadding(12, 12, 12, 12);
        container.addView(header);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 120));
        scroll.setBackgroundColor(0xFF1A1A1A);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(8, 8, 8, 8);
        scroll.addView(row);

        for (final EmbyApiClient.MediaItem item : items) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setLayoutParams(new LinearLayout.LayoutParams(82, LinearLayout.LayoutParams.MATCH_PARENT));
            itemLayout.setPadding(3, 3, 3, 3);

            if (item.imageTag != null && item.imageTag.length() > 0) {
                ImageView iv = new ImageView(this);
                iv.setLayoutParams(new LinearLayout.LayoutParams(78, 78));
                iv.setTag(item.id);
                String cacheKey = item.id + "_" + item.imageTag;
                Bitmap cached = imageCache.get(cacheKey);
                if (cached != null) {
                    iv.setImageBitmap(cached);
                } else {
                    new ImageLoaderTask(iv, item.id).execute(item.id, item.imageTag);
                }
                itemLayout.addView(iv);
            } else {
                ImageView iv = new ImageView(this);
                iv.setLayoutParams(new LinearLayout.LayoutParams(74, 74));
                iv.setImageResource(android.R.drawable.ic_media_play);
                iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                itemLayout.addView(iv);
            }

            TextView nameView = new TextView(this);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(106, LinearLayout.LayoutParams.WRAP_CONTENT));
            nameView.setText(item.name);
            nameView.setTextColor(0xFFFFFFFF);
            nameView.setTextSize(11);
            nameView.setMaxLines(2);
            nameView.setGravity(android.view.Gravity.CENTER);
            itemLayout.addView(nameView);

            itemLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(ContentActivity.this, FileDetailActivity.class);
                    intent.putExtra("item_id", item.id);
                    intent.putExtra("item_name", item.name);
                    intent.putExtra("image_tag", item.imageTag);
                    intent.putExtra("item_type", item.type);
                    intent.putExtra("server_url", apiClient.getServerUrl());
                    intent.putExtra("access_token", apiClient.getAccessToken());
                    intent.putExtra("user_id", apiClient.getUserId());
                    if (isResume) {
                        intent.putExtra("resume_position", (int)(item.playbackPositionTicks / 10000));
                    }
                    startActivity(intent);
                }
            });

            row.addView(itemLayout);
        }

        container.addView(scroll);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFF2D2D2D);
        container.addView(divider);

        return container;
    }

    private void searchAllDirectories(final String query) {
        showLoading(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<EmbyApiClient.MediaItem> results = new ArrayList<EmbyApiClient.MediaItem>();
                    List<String> stack = new ArrayList<String>();
                    stack.add(null);

                    while (!stack.isEmpty()) {
                        String parentId = stack.remove(0);
                        List<EmbyApiClient.MediaItem> items = apiClient.getMediaItems(parentId);

                        for (EmbyApiClient.MediaItem item : items) {
                            if (item.name.toLowerCase().contains(query.toLowerCase())) {
                                results.add(item);
                            }

                            if (item.isFolder) {
                                stack.add(item.id);
                            }
                        }
                    }

                    final List<EmbyApiClient.MediaItem> searchResults = results;

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            allItems.clear();
                            filteredItems.clear();
                            if (searchResults.isEmpty()) {
                                emptyText.setVisibility(View.VISIBLE);
                                emptyText.setText("No results found for: " + query);
                                contentList.setVisibility(View.GONE);
                            } else {
                                filteredItems.addAll(searchResults);
                                emptyText.setVisibility(View.GONE);
                                contentList.setVisibility(View.VISIBLE);
                            }
                            displayFilteredItems();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Search error: " + e.getMessage(), e);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                        }
                    });
                }
            }
        }).start();
    }

    private void initializeClient() {
        String serverUrl = getIntent().getStringExtra("server_url");
        String accessToken = getIntent().getStringExtra("access_token");
        String userId = getIntent().getStringExtra("user_id");

        if (serverUrl == null || accessToken == null || userId == null) {
            Log.e(TAG, "Missing required extras!");
            Toast.makeText(this, "Missing login information", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        apiClient = new EmbyApiClient(serverUrl);
        apiClient.setCredentials(accessToken, userId);
        Log.i(TAG, "Emby client initialized for user: " + userId);
    }

    private void loadMediaItems(final String parentId) {
        showLoading(true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "Loading items, parentId: " + parentId);
                    mediaItems = apiClient.getMediaItems(parentId);

                    if ((parentId == null || parentId.length() == 0) && mediaItems != null) {
                        List<EmbyApiClient.MediaItem> filtered = new ArrayList<EmbyApiClient.MediaItem>();

                        String moviesImageTag = "", moviesImageId = "";
                        String tvImageTag = "", tvImageId = "";
                        for (EmbyApiClient.MediaItem item : mediaItems) {
                            if ("movies".equals(item.collectionType)) { moviesImageTag = item.imageTag; moviesImageId = item.id; }
                            if ("tvshows".equals(item.collectionType)) { tvImageTag = item.imageTag; tvImageId = item.id; }
                        }

                        EmbyApiClient.MediaItem allMoviesFolder = new EmbyApiClient.MediaItem();
                        allMoviesFolder.id = "all_movies";
                        allMoviesFolder.name = "All Movies";
                        allMoviesFolder.type = "Movie Folder";
                        allMoviesFolder.isFolder = true;
                        allMoviesFolder.imageTag = moviesImageTag;
                        allMoviesFolder.imageItemId = moviesImageId;
                        filtered.add(allMoviesFolder);

                        EmbyApiClient.MediaItem allTvShowsFolder = new EmbyApiClient.MediaItem();
                        allTvShowsFolder.id = "all_tvshows";
                        allTvShowsFolder.name = "All TV Shows";
                        allTvShowsFolder.type = "TV Folder";
                        allTvShowsFolder.isFolder = true;
                        allTvShowsFolder.imageTag = tvImageTag;
                        allTvShowsFolder.imageItemId = tvImageId;
                        filtered.add(allTvShowsFolder);

                        for (EmbyApiClient.MediaItem item : mediaItems) {
                            if (item.collectionType != null && (item.collectionType.equals("music") || item.collectionType.equals("boxsets"))) {
                                filtered.add(item);
                            }
                        }
                        mediaItems = filtered;
                    }

                    Log.i(TAG, "Loaded " + mediaItems.size() + " items");

                    if (parentId == null || parentId.length() == 0) {
                        recentItems = apiClient.getRecentlyAdded(10);
                        List<EmbyApiClient.MediaItem> serverResume = apiClient.getResumeItems(10);
                        resumeItems = mergeLocalResumeItems(serverResume);
                        isRootView = true;
                    } else {
                        recentItems = new ArrayList<EmbyApiClient.MediaItem>();
                        resumeItems = new ArrayList<EmbyApiClient.MediaItem>();
                        isRootView = false;
                    }

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                showLoading(false);
                                displayMediaItems();
                            } catch (Exception e) {
                                Log.e(TAG, "Error displaying items: " + e.getMessage(), e);
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error loading items: " + e.getClass().getName() + ": " + e.getMessage(), e);
                    final String errorMsg = e.getMessage();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            showError("Failed to load content: " + errorMsg);
                        }
                    });
                }
            }
        }).start();
    }

    private void displayMediaItems() {
        if (mediaItems == null || mediaItems.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            int code = apiClient.getLastResponseCode();
            emptyText.setText("No content found. Response: " + code);
            contentList.setVisibility(View.GONE);
            filteredItems.clear();
            return;
        }

        emptyText.setVisibility(View.GONE);
        contentList.setVisibility(View.VISIBLE);

        filteredItems.clear();
        filteredItems.addAll(mediaItems);

        String query = searchInput.getText().toString();
        if (query.length() > 0) {
            filterItems(query);
        } else {
            displayFilteredItems();
        }
    }

    private class ImageLoaderTask extends AsyncTask<String, Void, Bitmap> {
        private final ImageView imageView;
        private final String itemId;

        public ImageLoaderTask(ImageView iv, String id) {
            this.imageView = iv;
            this.itemId = id;
        }

        protected Bitmap doInBackground(String... params) {
            try {
                String tag = (String) imageView.getTag();
                if (tag == null || !tag.equals(itemId)) {
                    return null;
                }

                String cacheKey = params[0] + "_" + params[1];
                Bitmap cached = imageCache.get(cacheKey);
                if (cached != null && !cached.isRecycled()) {
                    return cached;
                }

                String url = apiClient.getImageUrl(params[0], params[1]);
                java.net.URL urlObj = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
                conn.setDoInput(true);
                conn.setConnectTimeout(3000);
                conn.connect();
                java.io.InputStream input = conn.getInputStream();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = input.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                input.close();
                conn.disconnect();
                byte[] imageBytes = baos.toByteArray();

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, opts);
                int scale = 1;
                while (opts.outWidth / scale > 128 || opts.outHeight / scale > 128) {
                    scale *= 2;
                }
                opts = new BitmapFactory.Options();
                opts.inSampleSize = scale;
                Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, opts);
                if (bmp != null && imageCache.size() < IMAGE_CACHE_SIZE) {
                    imageCache.put(cacheKey, bmp);
                }
                return bmp;
            } catch (Exception e) {
                return null;
            }
        }

        protected void onPostExecute(Bitmap result) {
            if (result != null && imageView.getTag() != null && imageView.getTag().equals(itemId)) {
                imageView.setImageBitmap(result);
            }
        }
    }

    private class ListImageLoader extends AsyncTask<String, Void, Bitmap> {
        private final ImageView imageView;
        private final String itemId;

        public ListImageLoader(ImageView iv, String id) {
            this.imageView = iv;
            this.itemId = id;
        }

        protected Bitmap doInBackground(String... params) {
            try {
                String tag = (String) imageView.getTag();
                if (tag == null || !tag.equals(itemId)) {
                    return null;
                }

                String cacheKey = params[0] + "_" + params[1];
                Bitmap cached = imageCache.get(cacheKey);
                if (cached != null && !cached.isRecycled()) {
                    return cached;
                }

                String url = apiClient.getImageUrl(params[0], params[1]);
                java.net.URL urlObj = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
                conn.setDoInput(true);
                conn.setConnectTimeout(3000);
                conn.connect();
                java.io.InputStream input = conn.getInputStream();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = input.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                input.close();
                conn.disconnect();
                byte[] imageBytes = baos.toByteArray();

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, opts);
                int scale = 1;
                while (opts.outWidth / scale > 128 || opts.outHeight / scale > 128) {
                    scale *= 2;
                }
                opts = new BitmapFactory.Options();
                opts.inSampleSize = scale;
                Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, opts);
                if (bmp != null && imageCache.size() < IMAGE_CACHE_SIZE) {
                    imageCache.put(cacheKey, bmp);
                }
                return bmp;
            } catch (Exception e) {
                return null;
            }
        }

        protected void onPostExecute(Bitmap result) {
            if (result != null && imageView.getTag() != null && imageView.getTag().equals(itemId)) {
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageBitmap(result);
            }
        }
    }

    private void openFolder(String folderId, String folderName) {
        if (folderId != null) {
            folderStack.push(folderId);
        }
        headerTitle.setText(folderName);
        updateBackButton();
        loadMediaItems(folderId);
    }

    private void goBack() {
        if (!folderStack.isEmpty()) {
            folderStack.pop();
        }
        String parentId = folderStack.isEmpty() ? null : folderStack.peek();
        headerTitle.setText(folderStack.isEmpty() ? "Emby Client" : headerTitle.getText().toString());
        updateBackButton();
        loadMediaItems(parentId);
    }

    private void updateBackButton() {
        backBtn.setVisibility(folderStack.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean show) {
        if (loadingProgress != null) {
            loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showError(String message) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Error");
            builder.setMessage(message);
            builder.setPositiveButton("OK", null);
            builder.show();
        } catch (Exception e) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRootView) {
            refreshResumeItems();
        }
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        String n = name.toLowerCase().trim();
        String[] prefixes = { "3d ", "hd ", "4k ", "uhd " };
        for (String p : prefixes) {
            while (n.startsWith(p)) {
                n = n.substring(p.length());
            }
        }
        return n;
    }

    private boolean sameMovie(String nameA, String nameB) {
        String a = normalizeName(nameA);
        String b = normalizeName(nameB);
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private List<EmbyApiClient.MediaItem> mergeLocalResumeItems(List<EmbyApiClient.MediaItem> serverItems) {
        try {
            SharedPreferences prefs = getSharedPreferences("EmbyPlaybackPrefs", Context.MODE_PRIVATE);
            Map<String, ?> allPrefs = prefs.getAll();
            Log.i(TAG, "mergeLocalResumeItems: " + allPrefs.size() + " total prefs entries, " + serverItems.size() + " server items");
            List<EmbyApiClient.MediaItem> localItems = new ArrayList<EmbyApiClient.MediaItem>();
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("position_") && entry.getValue() instanceof Integer) {
                    int localPosMs = (Integer) entry.getValue();
                    String localItemId = key.substring("position_".length());
                    String localName = prefs.getString("name_" + localItemId, null);
                    if (localPosMs > 5000 && localName != null) {
                        boolean matched = false;
                        for (int si = 0; si < serverItems.size(); si++) {
                            EmbyApiClient.MediaItem ri = serverItems.get(si);
                            if (ri.id.equals(localItemId)) {
                                long serverPosMs = ri.playbackPositionTicks / 10000L;
                                if (localPosMs > serverPosMs) {
                                    ri.playbackPositionTicks = localPosMs * 10000L;
                                    ri.imageTag = prefs.getString("image_" + localItemId, null);
                                    Log.i(TAG, "mergeLocalResumeItems: local position " + localPosMs + "ms > server " + serverPosMs + "ms for " + localName + ", using local");
                                } else {
                                    Log.i(TAG, "mergeLocalResumeItems: server position " + serverPosMs + "ms >= local " + localPosMs + "ms for " + localName + ", keeping server");
                                }
                                matched = true;
                                break;
                            }
                        }
                        if (!matched) {
                            EmbyApiClient.MediaItem mi = new EmbyApiClient.MediaItem();
                            mi.id = localItemId;
                            mi.name = localName;
                            mi.imageTag = prefs.getString("image_" + localItemId, null);
                            mi.type = "Movie";
                            mi.playbackPositionTicks = localPosMs * 10000L;
                            localItems.add(mi);
                            Log.i(TAG, "mergeLocalResumeItems: added local item " + mi.name + " (" + localItemId + ") at " + localPosMs + "ms");
                        }
                    }
                }
            }
            List<EmbyApiClient.MediaItem> result = new ArrayList<EmbyApiClient.MediaItem>();
            result.addAll(serverItems);
            result.addAll(localItems);
            // Deduplicate by name — if two items represent the same movie, keep the higher position
            boolean[] removed = new boolean[result.size()];
            for (int i = 0; i < result.size(); i++) {
                if (removed[i]) continue;
                for (int j = i + 1; j < result.size(); j++) {
                    if (removed[j]) continue;
                    EmbyApiClient.MediaItem a = result.get(i);
                    EmbyApiClient.MediaItem b = result.get(j);
                    if (sameMovie(a.name, b.name)) {
                        long posA = a.playbackPositionTicks;
                        long posB = b.playbackPositionTicks;
                        if (posB > posA) {
                            removed[i] = true;
                            Log.i(TAG, "mergeLocalResumeItems: dedup '" + a.name + "' vs '" + b.name + "', keeping higher position " + (posB / 10000L) + "ms");
                        } else {
                            removed[j] = true;
                            Log.i(TAG, "mergeLocalResumeItems: dedup '" + a.name + "' vs '" + b.name + "', keeping higher position " + (posA / 10000L) + "ms");
                        }
                    }
                }
            }
            List<EmbyApiClient.MediaItem> deduped = new ArrayList<EmbyApiClient.MediaItem>();
            for (int i = 0; i < result.size(); i++) {
                if (!removed[i]) {
                    deduped.add(result.get(i));
                }
            }
            result = deduped;
            Collections.sort(result, new Comparator<EmbyApiClient.MediaItem>() {
                public int compare(EmbyApiClient.MediaItem a, EmbyApiClient.MediaItem b) {
                    long ta = prefs.getLong("time_" + a.id, 0);
                    long tb = prefs.getLong("time_" + b.id, 0);
                    return (ta > tb) ? -1 : (ta < tb) ? 1 : 0;
                }
            });
            Log.i(TAG, "mergeLocalResumeItems: merged list has " + result.size() + " items");
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error merging local resume items: " + e.getMessage());
            return serverItems;
        }
    }

    private void refreshResumeItems() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<EmbyApiClient.MediaItem> newRecent = apiClient.getRecentlyAdded(10);
                    final List<EmbyApiClient.MediaItem> newResume = apiClient.getResumeItems(10);
                    Log.i(TAG, "refreshResumeItems: server sent " + newResume.size() + " resume items");
                    final List<EmbyApiClient.MediaItem> mergedResume = mergeLocalResumeItems(newResume);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            recentItems = newRecent;
                            resumeItems = mergedResume;
                            displayFilteredItems();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing resume items: " + e.getMessage());
                }
            }
        }).start();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
