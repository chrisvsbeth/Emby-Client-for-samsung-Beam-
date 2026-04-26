package com.emby.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ProgressBar;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    private LinearLayout searchLayout;
    private EditText searchInput;
    private ImageButton clearSearchBtn;
    private ListView contentList;
    private ProgressBar loadingProgress;
    private TextView emptyText;
    private TextView recentHeader;
    private HorizontalScrollView recentScroll;
    private LinearLayout recentLayout;
    private View recentDivider;
    private LinearLayout sideIndex;

    private static final int IMAGE_CACHE_SIZE = 20;
    private Map<String, Bitmap> imageCache = new HashMap<String, Bitmap>();

    private List<EmbyApiClient.MediaItem> mediaItems = new ArrayList<EmbyApiClient.MediaItem>();
    private List<EmbyApiClient.MediaItem> filteredItems = new ArrayList<EmbyApiClient.MediaItem>();
    private List<EmbyApiClient.MediaItem> allItems = new ArrayList<EmbyApiClient.MediaItem>();
    private EmbyApiClient apiClient;
    private Stack<String> folderStack = new Stack<String>();
    private Stack<String> folderNames = new Stack<String>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "ContentActivity.onCreate() started");

        try {
            setContentView(R.layout.activity_content);
            Log.i(TAG, "Content layout inflated");

            initViews();
            initializeClient();
            loadMediaItems(null);

            Log.i(TAG, "ContentActivity.onCreate() completed");

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
        searchLayout = (LinearLayout) findViewById(R.id.searchLayout);
        searchInput = (EditText) findViewById(R.id.searchInput);
        clearSearchBtn = (ImageButton) findViewById(R.id.clearSearchBtn);
        contentList = (ListView) findViewById(R.id.contentList);
        loadingProgress = (ProgressBar) findViewById(R.id.loadingProgress);
        emptyText = (TextView) findViewById(R.id.emptyText);
        
        recentHeader = (TextView) findViewById(R.id.recentHeader);
        recentScroll = (HorizontalScrollView) findViewById(R.id.recentScroll);
        recentLayout = (LinearLayout) findViewById(R.id.recentLayout);
        recentDivider = (View) findViewById(R.id.recentDivider);
        sideIndex = (LinearLayout) findViewById(R.id.sideIndex);

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

        contentList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                try {
                    EmbyApiClient.MediaItem item = filteredItems.get(position);
                    Log.i(TAG, "Clicked item: " + item.name + ", ID: " + item.id + ", type: " + item.type + ", isFolder: " + item.isFolder);
                    if (item.id != null && item.id.equals("all_movies")) {
                        loadAllMovies();
                    } else if (item.isFolder) {
                        openFolder(item.id, item.name);
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
                } catch (Exception e) {
                    Log.e(TAG, "Error in item click: " + e.getMessage(), e);
                }
            }
        });
    }
    
    private void loadAllMovies() {
        showLoading(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mediaItems = apiClient.getAllMovies();
                    Log.i(TAG, "Loaded all movies: " + mediaItems.size());
                    
                    final List<EmbyApiClient.MediaItem> emptyRecent = new ArrayList<EmbyApiClient.MediaItem>();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            showLoading(false);
                            displayMediaItems();
                            displayRecentlyAdded(emptyRecent);
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
                if (item.isFolder) {
                    filteredItems.add(item);
                } else {
                    filteredItems.add(item);
                }
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
                String nameA = a.name != null ? a.name.toLowerCase() : "";
                String nameB = b.name != null ? b.name.toLowerCase() : "";
                return nameA.compareTo(nameB);
            }
        });
        
        setupSideIndex();
        
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return filteredItems.size();
            }

            @Override
            public Object getItem(int position) {
                return filteredItems.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_media, null);
                }

                EmbyApiClient.MediaItem item = filteredItems.get(position);
                
                TextView iconView = (TextView) convertView.findViewById(R.id.itemIcon);
                ImageView imageView = (ImageView) convertView.findViewById(R.id.itemImage);
                TextView nameView = (TextView) convertView.findViewById(R.id.itemName);
                TextView typeView = (TextView) convertView.findViewById(R.id.itemType);
                
                if (item.imageTag != null && !item.imageTag.isEmpty()) {
                    String tag = (String) imageView.getTag();
                    if (tag == null || !tag.equals(item.id)) {
                        imageView.setTag(item.id);
                        imageView.setImageBitmap(null);
                        imageView.setVisibility(View.VISIBLE);
                        iconView.setVisibility(View.GONE);
                        new ListImageLoader(imageView, item.id).execute(item.id, item.imageTag);
                    }
                } else {
                    imageView.setTag(null);
                    imageView.setVisibility(View.GONE);
                    imageView.setImageBitmap(null);
                    iconView.setVisibility(View.VISIBLE);
                    iconView.setText(getMediaIcon(item));
                }
                
                nameView.setText(item.name);
                typeView.setText(item.isFolder ? ("all_movies".equals(item.id) ? "Movie Folder" : "Folder") : item.type);

                return convertView;
            }
};
        
        contentList.setAdapter(adapter);
    }
    
    private void setupSideIndex() {
        if (sideIndex == null || filteredItems.isEmpty()) return;
        
        sideIndex.removeAllViews();
        
        final String[] alphabet = {"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z","#"};
        
        for (final String letter : alphabet) {
            final TextView letterView = new TextView(this);
            letterView.setText(letter);
            letterView.setTextSize(10);
            letterView.setTextColor(0xFF888888);
            letterView.setGravity(android.view.Gravity.CENTER);
            letterView.setPadding(2, 2, 2, 2);
            
            final boolean hasItems = hasItemsStartingWith(letter);
            if (hasItems) {
                letterView.setTextColor(0xFFE50914);
                letterView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        scrollToLetter(letter);
                    }
                });
            }
            
            sideIndex.addView(letterView);
        }
    }
    
    private boolean hasItemsStartingWith(String letter) {
        for (EmbyApiClient.MediaItem item : filteredItems) {
            String name = item.name != null ? item.name : "";
            if (name.isEmpty()) continue;
            char first = name.charAt(0);
            String firstChar = String.valueOf(first).toUpperCase();
            if (letter.equals("#")) {
                if (!Character.isLetter(first)) return true;
            } else if (firstChar.equals(letter)) {
                return true;
            }
        }
        return false;
    }
    
    private void scrollToLetter(final String letter) {
        for (int i = 0; i < filteredItems.size(); i++) {
            EmbyApiClient.MediaItem item = filteredItems.get(i);
            String name = item.name != null ? item.name : "";
            if (!name.isEmpty()) {
                char first = name.charAt(0);
                String firstChar = String.valueOf(first).toUpperCase();
                if (firstChar.equals(letter) || (!Character.isLetter(first) && letter.equals("#"))) {
                    contentList.setSelection(i);
                    return;
                }
            }
        }
    }

    private void searchAllDirectories(final String query) {
        showLoading(true);
        
        final Stack<String> searchStack = new Stack<String>();
        searchStack.push(null);
        
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
                            String lowerName = item.name.toLowerCase();
                            String lowerQuery = query.toLowerCase();
                            
                            if (lowerName.contains(lowerQuery)) {
                                item.name = item.name + " (" + (item.isFolder ? "Folder" : getFolderPath(parentId)) + ")";
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
    
    private String getFolderPath(String folderId) {
        return "Folder";
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
                    
                    if ((parentId == null || parentId.isEmpty()) && mediaItems != null) {
                        EmbyApiClient.MediaItem allMoviesFolder = new EmbyApiClient.MediaItem();
                        allMoviesFolder.id = "all_movies";
                        allMoviesFolder.name = "All Movies";
                        allMoviesFolder.type = "Movie Folder";
                        allMoviesFolder.isFolder = true;
                        mediaItems.add(0, allMoviesFolder);
                    }
                    
                    Log.i(TAG, "Loaded " + mediaItems.size() + " items");
                    
                    final List<EmbyApiClient.MediaItem> recentItems;
                    if (parentId == null || parentId.isEmpty()) {
                        recentItems = apiClient.getRecentlyAdded(10);
                    } else {
                        recentItems = new ArrayList<EmbyApiClient.MediaItem>();
                    }

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                showLoading(false);
                                displayMediaItems();
                                displayRecentlyAdded(recentItems);
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
        if (!query.isEmpty()) {
            filterItems(query);
        } else {
            displayFilteredItems();
        }
    }
    
    private void displayRecentlyAdded(List<EmbyApiClient.MediaItem> items) {
        if (items == null || items.isEmpty()) {
            recentHeader.setVisibility(View.GONE);
            recentScroll.setVisibility(View.GONE);
            recentDivider.setVisibility(View.GONE);
            return;
        }
        
        recentHeader.setVisibility(View.VISIBLE);
        recentScroll.setVisibility(View.VISIBLE);
        recentDivider.setVisibility(View.VISIBLE);
        recentLayout.removeAllViews();
        
        for (final EmbyApiClient.MediaItem item : items) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setLayoutParams(new LinearLayout.LayoutParams(82, LinearLayout.LayoutParams.MATCH_PARENT));
            itemLayout.setPadding(3, 3, 3, 3);
            
            if (item.imageTag != null && !item.imageTag.isEmpty()) {
                ImageView imageView = new ImageView(this);
                imageView.setLayoutParams(new LinearLayout.LayoutParams(78, 78));
                imageView.setTag(item.id);
                String cacheKey = item.id + "_" + item.imageTag;
                Bitmap cached = imageCache.get(cacheKey);
                if (cached != null) {
                    imageView.setImageBitmap(cached);
                } else {
                    new ImageLoaderTask(imageView, item.id).execute(item.id, item.imageTag);
                }
                itemLayout.addView(imageView);
            } else {
                TextView iconView = new TextView(this);
                iconView.setLayoutParams(new LinearLayout.LayoutParams(74, 74));
                iconView.setText(getMediaIcon(item));
                iconView.setTextSize(22);
                iconView.setGravity(android.view.Gravity.CENTER);
                itemLayout.addView(iconView);
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
                    Log.i(TAG, "Clicked recently added: " + item.name + ", ID: " + item.id);
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
            });
            
            recentLayout.addView(itemLayout);
        }
    }
    
    private String getMediaIcon(EmbyApiClient.MediaItem item) {
        if (item.isFolder) {
            if ("all_movies".equals(item.id)) return "🎬";
            return "📁";
        }
        String type = item.type != null ? item.type.toLowerCase() : "";
        if (type.contains("movie") || type.contains("video")) return "🎬";
        if (type.contains("series")) return "📺";
        if (type.contains("season")) return "📚";
        if (type.contains("episode")) return "🎞️";
        if (type.contains("music") || type.contains("audio")) return "🎵";
        if (type.contains("photo") || type.contains("image")) return "🖼️";
        return "📄";
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
                Bitmap bmp = BitmapFactory.decodeStream(input);
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
                Bitmap bmp = BitmapFactory.decodeStream(input);
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
        String title = folderStack.isEmpty() ? "Emby Client" : headerTitle.getText().toString();
        headerTitle.setText(title);
        updateBackButton();
        loadMediaItems(parentId);
    }

    private void updateBackButton() {
        backBtn.setVisibility(folderStack.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showQualityDialog(final EmbyApiClient.MediaItem item) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select Quality");

            String[] displayOptions = new String[QUALITY_OPTIONS.length];
            for (int i = 0; i < QUALITY_OPTIONS.length; i++) {
                displayOptions[i] = QUALITY_OPTIONS[i] + " - " + QUALITY_DESCRIPTIONS[i];
            }

            builder.setSingleChoiceItems(displayOptions, 0, null);
            builder.setPositiveButton(getString(R.string.play), new AlertDialog.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    try {
                        int selectedIndex = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                        playMedia(item, selectedIndex);
                    } catch (Exception e) {
                        Log.e(TAG, "Error playing: " + e.getMessage(), e);
                    }
                }
            });
            builder.setNegativeButton(getString(R.string.cancel), null);
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing dialog: " + e.getMessage(), e);
            playMedia(item, 0);
        }
    }

    private void playMedia(EmbyApiClient.MediaItem item, int quality) {
        String playbackUrl = apiClient.getPlaybackUrl(item.id, quality);
        
        Intent intent = new Intent(this, FileDetailActivity.class);
        intent.putExtra("item_id", item.id);
        intent.putExtra("item_name", item.name);
        intent.putExtra("image_tag", item.imageTag);
        intent.putExtra("item_type", item.type);
        intent.putExtra("server_url", apiClient.getServerUrl());
        intent.putExtra("access_token", apiClient.getAccessToken());
        intent.putExtra("user_id", apiClient.getUserId());
        startActivity(intent);
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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}