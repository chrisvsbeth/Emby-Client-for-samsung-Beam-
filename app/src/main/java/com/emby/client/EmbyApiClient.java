package com.emby.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

public class EmbyApiClient {
    private String serverUrl;
    private String accessToken;
    private String userId;
    private String lastError;
    private int lastResponseCode;

    public EmbyApiClient(String serverUrl) {
        this.serverUrl = cleanUrl(serverUrl);
    }

    private String cleanUrl(String url) {
        if (url == null) return "";
        url = url.trim().toLowerCase();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public String getLastError() {
        return lastError;
    }

    public int getLastResponseCode() {
        return lastResponseCode;
    }

    public boolean authenticate(String username, String password) {
        lastError = null;
        HttpURLConnection conn = null;
        try {
            String authUrl = serverUrl + "/emby/Users/AuthenticateByName";
            Log.i("EmbyClient", "Auth URL: " + authUrl);
            Log.i("EmbyClient", "Auth payload: {\"Username\":\"" + username + "\",\"Pw\":\"[hidden]\"}");
            
            URL url = new URL(authUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Emby-Client", "Emby Client");
            conn.setRequestProperty("X-Emby-Client-Version", "1.0.2");
            conn.setRequestProperty("X-Emby-Device-Id", "android-samsung-galaxy-beam-gt-i8530");
            conn.setRequestProperty("X-Emby-Device-Name", "Samsung Galaxy Beam");
            conn.setRequestProperty("X-Emby-Protocol", "api");
            conn.setRequestProperty("X-Emby-Authorization", "Emby UserId=\"" + username + "\"");
            conn.setRequestProperty("reportedDeviceId", "android-samsung-galaxy-beam-gt-i8530");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);

            JSONObject authParams = new JSONObject();
            authParams.put("Username", username);
            authParams.put("Pw", password);

            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
            writer.write(authParams.toString());
            writer.flush();
            writer.close();

            lastResponseCode = conn.getResponseCode();
            Log.i("EmbyClient", "Auth response code: " + lastResponseCode);

            BufferedReader reader;
            if (lastResponseCode == 200 || lastResponseCode == 201) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            }

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();

            String respBody = response.toString();
            Log.i("EmbyClient", "Auth response body: " + respBody);

            if (lastResponseCode == 200 || lastResponseCode == 201) {
                JSONObject jsonResponse = new JSONObject(respBody);
                if (jsonResponse.has("AccessToken")) {
                    accessToken = jsonResponse.getString("AccessToken");
                    if (jsonResponse.has("User")) {
                        JSONObject user = jsonResponse.getJSONObject("User");
                        userId = user.getString("Id");
                    }
                    return true;
                } else {
                    lastError = "Missing access token";
                }
            } else if (lastResponseCode == 401) {
                lastError = "Invalid username or password";
            } else {
                lastError = "HTTP " + lastResponseCode + ": " + respBody;
            }

        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }
        return false;
    }

    public boolean setCredentials(String token, String uid) {
        this.accessToken = token;
        this.userId = uid;
        return true;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getUserId() {
        return userId;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public List<MediaItem> getRecentlyAdded(int limit) {
        List<MediaItem> items = new ArrayList<MediaItem>();
        HttpURLConnection conn = null;
        try {
            String endpoint = serverUrl + "/emby/Users/" + userId + "/Items/Latest?Limit=" + limit + "&Fields=BasicSyncInfo,MediaInfo,Images&api_key=" + accessToken;
            
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
            lastResponseCode = conn.getResponseCode();
            if (lastResponseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONArray jsonArray = new JSONArray(response.toString());
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    MediaItem item = new MediaItem();
                    item.id = obj.optString("Id", "");
                    item.name = obj.optString("Name", "Unknown");
                    item.type = obj.optString("Type", "Unknown");
                    item.isFolder = obj.optBoolean("IsFolder", false);
                    
                    if (obj.has("ImageTags")) {
                        JSONObject imageTags = obj.getJSONObject("ImageTags");
                        if (imageTags.has("Primary")) {
                            item.imageTag = imageTags.getString("Primary");
                        }
                    }
                    
                    items.add(item);
                }
            }
        } catch (Exception e) {
            Log.e("EmbyClient", "Error getting recent items: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return items;
    }

    public List<MediaItem> getResumeItems(int limit) {
        List<MediaItem> items = new ArrayList<MediaItem>();
        HttpURLConnection conn = null;
        try {
            String endpoint = serverUrl + "/emby/Users/" + userId + "/Items?Filters=IsResumable&Recursive=true&Limit=" + limit + "&Fields=BasicSyncInfo,MediaInfo,Images,UserData&api_key=" + accessToken;
            Log.i("EmbyClient", "Resume URL: " + endpoint);
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            lastResponseCode = conn.getResponseCode();
            Log.i("EmbyClient", "Resume response code: " + lastResponseCode);
            if (lastResponseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                String respStr = response.toString();
                Log.i("EmbyClient", "Resume response: " + respStr.substring(0, Math.min(respStr.length(), 500)));
                JSONObject jsonResp = new JSONObject(respStr);
                if (jsonResp.has("Items")) {
                    JSONArray jsonArray = jsonResp.getJSONArray("Items");
                    Log.i("EmbyClient", "Resume items count: " + jsonArray.length());
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.getJSONObject(i);
                        MediaItem item = new MediaItem();
                        item.id = obj.optString("Id", "");
                        item.name = obj.optString("Name", "Unknown");
                        item.type = obj.optString("Type", "Unknown");
                        item.isFolder = obj.optBoolean("IsFolder", false);
                        item.runTimeTicks = obj.optLong("RunTimeTicks", 0);
                        if (obj.has("ImageTags")) {
                            JSONObject imageTags = obj.getJSONObject("ImageTags");
                            if (imageTags.has("Primary")) {
                                item.imageTag = imageTags.getString("Primary");
                            }
                        }
                        if (obj.has("UserData")) {
                            JSONObject userData = obj.getJSONObject("UserData");
                            item.playbackPositionTicks = userData.optLong("PlaybackPositionTicks", 0);
                        }
                        items.add(item);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("EmbyClient", "Error getting resume items: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return items;
    }

    public List<MediaItem> getViews() {
        List<MediaItem> views = new ArrayList<MediaItem>();
        HttpURLConnection conn = null;
        try {
            String endpoint = serverUrl + "/emby/Users/" + userId + "/Views?api_key=" + accessToken;
            
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
            lastResponseCode = conn.getResponseCode();
            if (lastResponseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONObject json = new JSONObject(response.toString());
                JSONArray itemsArray = json.optJSONArray("Items");
                if (itemsArray != null) {
                    for (int i = 0; i < itemsArray.length(); i++) {
                        JSONObject obj = itemsArray.getJSONObject(i);
                        MediaItem item = new MediaItem();
                        item.id = obj.optString("Id", "");
                        item.name = obj.optString("Name", "Unknown");
                        item.type = obj.optString("CollectionType", "");
                        item.isFolder = true;
                        views.add(item);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("EmbyClient", "Error getting views: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return views;
    }
    
    public List<MediaItem> getAllMovies() {
        List<MediaItem> items = new ArrayList<MediaItem>();
        HttpURLConnection conn = null;
        try {
            String endpoint = serverUrl + "/emby/Users/" + userId + "/Items?Recursive=true&IncludeItemTypes=Movie&Fields=BasicSyncInfo,MediaInfo,Images&api_key=" + accessToken;
            
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
            lastResponseCode = conn.getResponseCode();
            Log.i("EmbyClient", "getAllMovies response: " + lastResponseCode);
            
            if (lastResponseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONObject json = new JSONObject(response.toString());
                JSONArray itemsArray = json.optJSONArray("Items");
                if (itemsArray != null) {
                    for (int i = 0; i < itemsArray.length(); i++) {
                        JSONObject obj = itemsArray.getJSONObject(i);
                        MediaItem item = new MediaItem();
                        item.id = obj.optString("Id", "");
                        item.name = obj.optString("Name", "Unknown");
                        item.type = obj.optString("Type", "Unknown");
                        item.isFolder = false;
                        item.runTimeTicks = obj.optLong("RunTimeTicks", 0);
                        
                        if (obj.has("ImageTags")) {
                            JSONObject imageTags = obj.getJSONObject("ImageTags");
                            if (imageTags.has("Primary")) {
                                item.imageTag = imageTags.getString("Primary");
                            }
                        }
                        
                        items.add(item);
                    }
                }
                Log.i("EmbyClient", "Found " + items.size() + " movies");
            }
        } catch (Exception e) {
            Log.e("EmbyClient", "Error getting movies: " + e.getMessage(), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return items;
    }

    public List<MediaItem> getMediaItems(String parentId) {
        List<MediaItem> items = new ArrayList<MediaItem>();
        HttpURLConnection conn = null;
        try {
            String endpoint;
            if (parentId == null || parentId.equals("")) {
                endpoint = serverUrl + "/emby/Users/" + userId + "/Views?api_key=" + accessToken;
            } else {
                endpoint = serverUrl + "/emby/Users/" + userId + "/Items?ParentId=" + URLEncoder.encode(parentId, "UTF-8") + "&Fields=BasicSyncInfo,MediaInfo,Images&Limit=500&api_key=" + accessToken;
            }

            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Emby-Client", "Emby Client");
            conn.setRequestProperty("X-Emby-Client-Version", "1.0.2");
            conn.setRequestProperty("X-Emby-Device-Id", "android-samsung-galaxy-beam-gt-i8530");
            conn.setRequestProperty("X-Emby-Device-Name", "Samsung Galaxy Beam");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);

            lastResponseCode = conn.getResponseCode();
            Log.i("EmbyClient", "Items URL: " + endpoint);
            Log.i("EmbyClient", "Items response: " + lastResponseCode);

            if (lastResponseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()), 8192);
                StringBuilder response = new StringBuilder(16384);
                String line;
                int lineCount = 0;
                int maxLines = 500;
                while ((line = reader.readLine()) != null && lineCount < maxLines) {
                    response.append(line);
                    lineCount++;
                }
                reader.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                if (jsonResponse.has("Items")) {
                    JSONArray itemsArray = jsonResponse.getJSONArray("Items");
                    for (int i = 0; i < itemsArray.length(); i++) {
                        JSONObject item = itemsArray.getJSONObject(i);
                        MediaItem mediaItem = new MediaItem();
                        mediaItem.id = item.optString("Id", "");
                        mediaItem.name = item.optString("Name", "Unknown");
                        mediaItem.type = item.optString("Type", "Unknown");
                        mediaItem.isFolder = item.optBoolean("IsFolder", false);
                        mediaItem.runTimeTicks = item.optLong("RunTimeTicks", 0);
                        mediaItem.indexNumber = item.optInt("IndexNumber", -1);
                        mediaItem.parentIndexNumber = item.optInt("ParentIndexNumber", -1);
                        mediaItem.parentId = item.optString("ParentId", null);
                        mediaItem.albumId = item.optString("AlbumId", null);
                        mediaItem.collectionType = item.optString("CollectionType", null);
                        
                        if (item.has("ImageTags")) {
                            JSONObject imageTags = item.getJSONObject("ImageTags");
                            if (imageTags.has("Primary")) {
                                mediaItem.imageTag = imageTags.getString("Primary");
                            }
                        }
                        
                        items.add(mediaItem);
                    }
                }
                lastError = null;
            } else {
                lastError = "HTTP " + lastResponseCode;
            }
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }
        return items;
    }

    public List<MediaItem> getAllTvShows() {
        List<MediaItem> items = new ArrayList<MediaItem>();
        HttpURLConnection conn = null;
        try {
            String endpoint = serverUrl + "/emby/Users/" + userId + "/Items?Recursive=true&IncludeItemTypes=Series&Fields=BasicSyncInfo,MediaInfo,Images&api_key=" + accessToken;

            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            lastResponseCode = conn.getResponseCode();
            Log.i("EmbyClient", "getAllTvShows response: " + lastResponseCode);

            if (lastResponseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                JSONArray itemsArray = json.optJSONArray("Items");
                if (itemsArray != null) {
                    for (int i = 0; i < itemsArray.length(); i++) {
                        JSONObject obj = itemsArray.getJSONObject(i);
                        MediaItem item = new MediaItem();
                        item.id = obj.optString("Id", "");
                        item.name = obj.optString("Name", "Unknown");
                        item.type = obj.optString("Type", "Series");
                        item.isFolder = true;
                        item.runTimeTicks = obj.optLong("RunTimeTicks", 0);

                        if (obj.has("ImageTags")) {
                            JSONObject imageTags = obj.getJSONObject("ImageTags");
                            if (imageTags.has("Primary")) {
                                item.imageTag = imageTags.getString("Primary");
                            }
                        }

                        items.add(item);
                    }
                }
                Log.i("EmbyClient", "Found " + items.size() + " TV shows");
            }
        } catch (Exception e) {
            Log.e("EmbyClient", "Error getting TV shows: " + e.getMessage(), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return items;
    }

    public String getPlaybackUrl(String itemId, int quality, long startPositionMs, String playSessionId) {
        int videoBitrate = 0;
        int maxWidth = 0;
        boolean directPlay = false;

        switch (quality) {
            case 0: directPlay = true; break;
            case 1: videoBitrate = 2000000; maxWidth = 1920; break;
            case 2: videoBitrate = 1500000; maxWidth = 1280; break;
            case 3: videoBitrate = 1000000; maxWidth = 1280; break;
            case 4: videoBitrate = 800000; maxWidth = 854; break;
            case 5: videoBitrate = 400000; maxWidth = 640; break;
            case 6: videoBitrate = 250000; maxWidth = 480; break;
        }

        String psId = (playSessionId != null) ? playSessionId : java.util.UUID.randomUUID().toString();
        String baseUrl = serverUrl + "/emby/Videos/" + itemId + "/stream";
        long startTicks = startPositionMs * 10000;

        if (directPlay && startPositionMs == 0) {
            String url = baseUrl + ".mp4?api_key=" + accessToken +
                   "&PlaySessionId=" + psId +
                   "&static=true";
            Log.i("EmbyClient", "getPlaybackUrl direct: " + url);
            return url;
        } else {
            String url = baseUrl + ".ts?api_key=" + accessToken +
                   "&PlaySessionId=" + psId +
                   "&VideoBitrate=" + videoBitrate +
                   "&MaxWidth=" + maxWidth +
                   "&VideoCodec=h264" +
                   "&AudioCodec=aac" +
                   "&TranscodeContainer=ts" +
                   "&RequireNonSegmentalStreaming=true";
            if (startPositionMs > 0) {
                url += "&StartTimeTicks=" + startTicks;
            }
            Log.i("EmbyClient", "getPlaybackUrl transcode: " + url);
            return url;
        }
    }
    
    public String getPlaybackUrl(String itemId, int quality, long startPositionMs) {
        return getPlaybackUrl(itemId, quality, startPositionMs, null);
    }
    
    public String getPlaybackUrl(String itemId, int quality) {
        return getPlaybackUrl(itemId, quality, 0, null);
    }

    public boolean reportCapabilities() {
        HttpURLConnection conn = null;
        try {
            String urlStr = serverUrl + "/emby/Sessions/Capabilities?api_key=" + accessToken;
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Emby-Client", "Emby Client");
            conn.setRequestProperty("X-Emby-Client-Version", "1.0.2");
            conn.setRequestProperty("X-Emby-Device-Id", "android-samsung-galaxy-beam-gt-i8530");
            conn.setRequestProperty("X-Emby-Device-Name", "Samsung Galaxy Beam");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            JSONObject caps = new JSONObject();
            JSONArray playableTypes = new JSONArray();
            playableTypes.put("Video");
            playableTypes.put("Audio");
            caps.put("PlayableMediaTypes", playableTypes);
            caps.put("SupportedMediaTypes", playableTypes);
            caps.put("SupportsContentUploading", false);
            caps.put("SupportsSync", false);
            caps.put("SupportsMediaControl", false);
            // Tell server we do NOT support HLS so it uses progressive MP4/TS
            caps.put("SupportsHls", false);

            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
            writer.write(caps.toString());
            writer.flush();
            writer.close();

            int code = conn.getResponseCode();
            Log.i("EmbyClient", "Capabilities report: " + code);
            return code == 200 || code == 204;
        } catch (Exception e) {
            Log.e("EmbyClient", "Error reporting capabilities: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public String getHlsPlaybackUrl(String itemId, int quality, long startPositionMs) {
        int videoBitrate = 0;
        int maxWidth = 0;

        switch (quality) {
            case 1: videoBitrate = 2000000; maxWidth = 1920; break;
            case 2: videoBitrate = 1500000; maxWidth = 1280; break;
            case 3: videoBitrate = 1000000; maxWidth = 1280; break;
            case 4: videoBitrate = 800000; maxWidth = 854; break;
            case 5: videoBitrate = 400000; maxWidth = 640; break;
            case 6: videoBitrate = 250000; maxWidth = 480; break;
            default: videoBitrate = 400000; maxWidth = 640; break;
        }

        String playSession = java.util.UUID.randomUUID().toString();
        String baseUrl = serverUrl + "/emby/Videos/" + itemId + "/stream";
        long startTicks = startPositionMs * 10000;

        String url = baseUrl + ".m3u8?api_key=" + accessToken +
               "&PlaySessionId=" + playSession +
               "&VideoBitrate=" + videoBitrate +
               "&MaxWidth=" + maxWidth +
               "&VideoCodec=h264" +
               "&AudioCodec=aac" +
               "&TranscodeContainer=ts" +
               "&AllowVideoStreamCopy=false";
        if (startPositionMs > 0) {
            url += "&StartTimeTicks=" + startTicks;
        }
        Log.i("EmbyClient", "getHlsPlaybackUrl: " + url);
        return url;
    }

    public String getAudioUrl(String itemId) {
        String url = serverUrl + "/emby/Audio/" + itemId + "/stream.mp3?api_key=" + accessToken + "&static=true";
        Log.i("EmbyClient", "getAudioUrl: " + url);
        return url;
    }

    public String getHlsPlaybackUrl(String itemId, int quality) {
        return getHlsPlaybackUrl(itemId, quality, 0);
    }

    public String getMediaSourceId(String itemId) {
        HttpURLConnection conn = null;
        try {
            String urlStr = serverUrl + "/emby/Items/" + itemId + "?api_key=" + accessToken + "&Fields=MediaSources";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Emby-Client", "Emby Client");
            conn.setRequestProperty("X-Emby-Client-Version", "1.0.2");
            conn.setRequestProperty("X-Emby-Device-Id", "android-samsung-galaxy-beam-gt-i8530");
            conn.setRequestProperty("X-Emby-Device-Name", "Samsung Galaxy Beam");

            int code = conn.getResponseCode();
            Log.i("EmbyClient", "Item info response: " + code);

            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                Log.i("EmbyClient", "Item name: " + json.optString("Name", ""));

                if (json.has("MediaSources")) {
                    JSONArray sources = json.getJSONArray("MediaSources");
                    if (sources.length() > 0) {
                        JSONObject source = sources.getJSONObject(0);
                        String container = source.optString("Container", "");
                        String mediaType = source.optString("MediaType", "");
                        String path = source.optString("Path", "");
                        Log.i("EmbyClient", "Container: " + container);
                        Log.i("EmbyClient", "MediaType: " + mediaType);
                        Log.i("EmbyClient", "Path: " + path);
                        return source.getString("Id");
                    }
                }
            }
        } catch (Exception e) {
            Log.e("EmbyClient", "Error getting media source: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return itemId;
    }

    public static class MediaItem {
        public String id;
        public String name;
        public String type;
        public String mediaType;
        public boolean isFolder;
        public String imageTag;
        public long runTimeTicks;
        public int indexNumber = -1;
        public int parentIndexNumber = -1;
        public String parentId;
        public String albumId;
        public String collectionType;
        public String imageItemId;
        public long playbackPositionTicks;
    }
    
    public static class MediaItemInfo {
        public String id;
        public String name;
        public String type;
        public long runTimeTicks;
        public long dateCreated;
        public String container;
        public long fileSize;
        public int productionYear;
        public String plot;
        public String genres;
        public double communityRating;
        public String primaryImageTag;
        
        public long getRunTimeMs() {
            return runTimeTicks / 10000;
        }
    }
    
    public MediaItemInfo getItemInfo(String itemId) {
        MediaItemInfo info = null;
        HttpURLConnection conn = null;
        try {
            String urlStr = serverUrl + "/emby/Users/" + userId + "/Items/" + itemId + "?api_key=" + accessToken + "&Fields=BasicSyncInfo,MediaInfo,Overview";
            Log.i("EmbyClient", "getItemInfo URL: " + urlStr);
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int code = conn.getResponseCode();
            Log.i("EmbyClient", "getItemInfo response code: " + code + " for itemId: " + itemId);
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                Log.i("EmbyClient", "Full JSON response: " + json.toString());
                Log.i("EmbyClient", "JSON response keys: " + json.keys().toString());
                Log.i("EmbyClient", "Has ProductionYear: " + json.has("ProductionYear") + ", Has Overview: " + json.has("Overview") + ", Has RunTimeTicks: " + json.has("RunTimeTicks"));
                
                info = new MediaItemInfo();
                info.id = json.optString("Id", "");
                info.name = json.optString("Name", "Unknown");
                info.type = json.optString("Type", "Unknown");
                
                String runTime = json.optString("RunTimeTicks", "");
                if (runTime.length() > 0) {
                    info.runTimeTicks = Long.parseLong(runTime);
                }
                
                String prodYear = json.optString("ProductionYear", "");
                if (prodYear.length() > 0) {
                    info.productionYear = Integer.parseInt(prodYear);
                }
                
                info.plot = json.optString("Overview", "");
                String rating = json.optString("CommunityRating", "");
                if (rating.length() > 0) {
                    info.communityRating = Double.parseDouble(rating);
                }
                
                JSONArray genresArray = json.optJSONArray("Genres");
                if (genresArray != null) {
                    StringBuilder genresBuilder = new StringBuilder();
                    for (int i = 0; i < genresArray.length(); i++) {
                        if (i > 0) genresBuilder.append(", ");
                        genresBuilder.append(genresArray.getString(i));
                    }
                    info.genres = genresBuilder.toString();
                }
                
                if (json.has("ImageTags")) {
                    JSONObject imageTags = json.getJSONObject("ImageTags");
                    if (imageTags.has("Primary")) {
                        info.primaryImageTag = imageTags.getString("Primary");
                    }
                }
                info.dateCreated = json.optLong("DateCreated", 0);
                
                if (json.has("MediaSources")) {
                    JSONArray sources = json.getJSONArray("MediaSources");
                    if (sources.length() > 0) {
                        JSONObject source = sources.getJSONObject(0);
                        info.container = source.optString("Container", "");
                        info.fileSize = source.optLong("Size", 0);
                    }
                }
                Log.i("EmbyClient", "Item info loaded: " + info.name + ", runtime: " + info.getRunTimeMs() + "ms");
            }
        } catch (Exception e) {
            Log.e("EmbyClient", "Error getting item info: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return info;
    }
    
    public String getImageUrl(String itemId, String imageTag) {
        return serverUrl + "/emby/Items/" + itemId + "/Images/Primary?maxHeight=128&maxWidth=128&tag=" + imageTag + "&api_key=" + accessToken;
    }
    
    public String getImageUrl(String itemId, String imageTag, int maxWidth, int maxHeight) {
        return serverUrl + "/emby/Items/" + itemId + "/Images/Primary?maxWidth=" + maxWidth + "&maxHeight=" + maxHeight + "&tag=" + imageTag + "&api_key=" + accessToken;
    }
    
    public boolean reportPlaybackStarted(String itemId, long positionTicks, boolean canSeek, String playMethod, String playSessionId) {
        HttpURLConnection conn = null;
        try {
            String urlStr = serverUrl + "/emby/Sessions/Playing?api_key=" + accessToken;
            
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            JSONObject body = new JSONObject();
            body.put("ItemId", itemId);
            body.put("CanSeek", canSeek);
            body.put("QueueableMediaTypes", new JSONArray().put("Video"));
            body.put("IsPaused", false);
            if (positionTicks > 0) {
                body.put("PositionTicks", positionTicks);
            }
            if (playMethod != null) {
                body.put("PlayMethod", playMethod);
            }
            if (playSessionId != null) {
                body.put("PlaySessionId", playSessionId);
            }
            
            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
            writer.write(body.toString());
            writer.flush();
            writer.close();
            
            int code = conn.getResponseCode();
            Log.i("EmbyClient", "Playback started report: " + code);
            return code == 200 || code == 204;
        } catch (Exception e) {
            Log.e("EmbyClient", "Error reporting playback start: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
    
    public boolean reportPlaybackProgress(String itemId, long positionTicks, boolean isPlaying, String eventName, String playSessionId, boolean canSeek, String playMethod) {
        HttpURLConnection conn = null;
        try {
            String urlStr = serverUrl + "/emby/Sessions/Playing/Progress?api_key=" + accessToken;
            
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            JSONObject body = new JSONObject();
            body.put("ItemId", itemId);
            body.put("CanSeek", canSeek);
            body.put("IsPaused", !isPlaying);
            body.put("PositionTicks", positionTicks);
            body.put("MediaSourceId", "mediasource_" + itemId);
            if (eventName != null) {
                body.put("EventName", eventName);
            }
            if (playSessionId != null) {
                body.put("PlaySessionId", playSessionId);
            }
            if (playMethod != null) {
                body.put("PlayMethod", playMethod);
            }
            
            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
            writer.write(body.toString());
            writer.flush();
            writer.close();
            
            int code = conn.getResponseCode();
            Log.i("EmbyClient", "Progress report (" + eventName + "): " + code);
            return code == 200 || code == 204;
        } catch (Exception e) {
            Log.e("EmbyClient", "Error reporting progress: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
    
    public boolean deleteActiveEncoding(String playSessionId) {
        HttpURLConnection conn = null;
        try {
            String urlStr = serverUrl + "/emby/Videos/ActiveEncodings?api_key=" + accessToken + "&PlaySessionId=" + playSessionId;
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            Log.i("EmbyClient", "Delete active encoding: " + code);
            return code == 200 || code == 204;
        } catch (Exception e) {
            Log.e("EmbyClient", "Error deleting active encoding: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public boolean reportPlaybackStopped(String itemId, long positionTicks, String playSessionId, String playMethod) {
        HttpURLConnection conn = null;
        try {
            String urlStr = serverUrl + "/emby/Sessions/Playing/Stopped?api_key=" + accessToken;
            
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            JSONObject body = new JSONObject();
            body.put("ItemId", itemId);
            body.put("PositionTicks", positionTicks);
            body.put("MediaSourceId", "mediasource_" + itemId);
            if (playSessionId != null) {
                body.put("PlaySessionId", playSessionId);
            }
            if (playMethod != null) {
                body.put("PlayMethod", playMethod);
            }
            
            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
            writer.write(body.toString());
            writer.flush();
            writer.close();
            
            int code = conn.getResponseCode();
            Log.i("EmbyClient", "Playback stopped report: " + code);
            return code == 200 || code == 204;
        } catch (Exception e) {
            Log.e("EmbyClient", "Error reporting playback stop: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}