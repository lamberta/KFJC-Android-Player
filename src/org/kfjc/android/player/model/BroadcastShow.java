package org.kfjc.android.player.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class BroadcastShow implements Parcelable {

    private static final String KEY_PLAYLIST_ID = "playlistId";
    private static final String KEY_AIRNAME = "airName";
    private static final String KEY_STARTTIME = "startTime";
    private static final String KEY_URLS = "urls";

    private final String playlistId;
    private final String airName;
    private long timestamp;
    private final List<String> urls;

    BroadcastShow(BroadcastHour hour) {
        urls = new ArrayList<>();
        this.playlistId = hour.getPlaylistId();
        this.airName = hour.getAirName();
        this.timestamp = hour.getTimestamp();
        urls.add(hour.getUrl());
    }

    public BroadcastShow(String jsonString) {
        // TODO: ripe for a unit test!
        urls = new ArrayList<>();
        String playlistId = "";
        String airName = "";
        long timestamp = 0L;
        try {
            JSONObject in = new JSONObject(jsonString);
            playlistId = in.getString(KEY_PLAYLIST_ID);
            airName = in.getString(KEY_AIRNAME);
            timestamp = in.getLong(KEY_STARTTIME);
            JSONArray inUrls = in.getJSONArray(KEY_URLS);
            for (int i = 0; i < inUrls.length(); i++) {
                urls.add(inUrls.getString(i));
            }

        } catch (JSONException e) {}
        this.playlistId = playlistId;
        this.airName = airName;
        this.timestamp = timestamp;
    }

    public BroadcastShow(Parcel in) {
        urls = new ArrayList<>();
        playlistId = in.readString();
        airName = in.readString();
        timestamp = in.readLong();
        in.readStringList(urls);
    }

    void addHour(BroadcastHour hour) {
        if (!hour.getPlaylistId().equals(playlistId)) {
            return;
        }
        // Use earliest timestamp as start of show.
        timestamp = Math.min(timestamp, hour.getTimestamp());
        urls.add(hour.getUrl());
    }

    public String getPlaylistId() {
        return playlistId;
    }

    public String getAirName() {
        return airName;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public String getTimestampString() {
        SimpleDateFormat df = new SimpleDateFormat("ha, EEEE d MMMM yyyy");
        df.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
        return df.format(new Date(roundUpHour(timestamp) * 1000));
    }

    private static long roundUpHour(long timestampSec) {
        long remainder = timestampSec % 3600;
        long timeToAdd = 3600 - remainder;
        return timestampSec + timeToAdd;
    }

    public List<String> getUrls() {
        return urls;
    }

    public String toJsonString() {
        JSONObject out = new JSONObject();
        try {
            out.put(KEY_PLAYLIST_ID, playlistId);
            out.put(KEY_AIRNAME, airName);
            out.put(KEY_STARTTIME, timestamp);
            JSONArray urls = new JSONArray(this.urls);
            out.put(KEY_URLS, urls);
        } catch (JSONException e) {}
        return out.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(playlistId);
        dest.writeString(airName);
        dest.writeLong(timestamp);
        dest.writeStringList(urls);
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
        public BroadcastShow createFromParcel(Parcel in) {
            return new BroadcastShow(in);
        }

        public BroadcastShow[] newArray(int size) {
            return new BroadcastShow[size];
        }
    };
}
