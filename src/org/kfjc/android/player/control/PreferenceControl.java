package org.kfjc.android.player.control;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kfjc.android.player.Constants;
import org.kfjc.android.player.util.HttpUtil;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceControl {
	
	private static final String PREFERENCE_KEY = "kfjc.preferences";
	private static final String STREAM_PREFERENCE_KEY = "kfjc.preferences.streamname";
    private static final int PREFERENCE_MODE = Context.MODE_PRIVATE;
	
	private static SharedPreferences preferences;
	private static Map<String, String> streamMap = new LinkedHashMap<String, String>();
	
	public PreferenceControl(Context context) {
		preferences = context.getSharedPreferences(PREFERENCE_KEY, PREFERENCE_MODE);
		loadStreams();
	}
	
	private void loadStreams() {
		try {
			JSONArray streams = new JSONArray(getAvailableStreams());
			for (int i = 0; i < streams.length(); i++) {
				JSONObject stream = streams.getJSONObject(i);
				String name = stream.getString("name");
				String url = stream.getString("url");
				streamMap.put(name, url);
			}
		} catch (JSONException e) {
			streamMap = new HashMap<String, String>();
		}
	}
	
	private String getAvailableStreams() {
        try {
            return HttpUtil.getUrl(Constants.AVAILABLE_STREAMS_URL);
        } catch (IOException e) {
            return Constants.FALLBACK_STREAM_JSON;
        }
	}
	
	public static String getStreamNamePreference() {
		String pref = preferences.getString(STREAM_PREFERENCE_KEY, "");
		if (pref == null || pref.isEmpty()) { return Constants.FALLBACK_STREAM_NAME; }
		return pref;
	}
	
	public static String getUrlPreference() {
		String pref = streamMap.get(getStreamNamePreference());
        if (pref == null || pref.isEmpty()) { return Constants.FALLBACK_STREAM_URL; }
        return pref;
    }
	
	public List<String> getStreamNames() {
		return new ArrayList<String>(streamMap.keySet());
	}
	
	public void setStreamNamePreference(String streamName) {
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString(STREAM_PREFERENCE_KEY, streamName);
		editor.commit();
	}
}
