package org.kfjc.android.player.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class BroadcastShow implements Parcelable {
    private final String playlistId;

    private final String airName;
    private final String startDateTime;
    List<String> urls;

    BroadcastShow(BroadcastHour hour) {
        urls = new ArrayList<>();
        this.playlistId = hour.getPlaylistId();
        this.airName = hour.getAirName();
        this.startDateTime = "6am Thursday April 24th 2016";
        urls.add(hour.getUrl());
    }

    public BroadcastShow(Parcel in) {
        playlistId = in.readString();
        airName = in.readString();
        startDateTime = in.readString();
        in.readStringList(urls);
    }

    void addHour(BroadcastHour hour) {
        if (!hour.getPlaylistId().equals(playlistId)) {
            return;
        }
        urls.add(hour.getUrl());
    }

    public String getPlaylistId() {
        return playlistId;
    }

    public String getAirName() {
        return airName;
    }

    public String getStartDateTime() {
        return startDateTime;
    }

    public List<String> getUrls() {
        return urls;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(playlistId);
        dest.writeString(airName);
        dest.writeString(startDateTime);
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
