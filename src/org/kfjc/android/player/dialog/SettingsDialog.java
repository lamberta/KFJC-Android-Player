package org.kfjc.android.player.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.SwitchCompat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;

import org.kfjc.android.player.R;
import org.kfjc.android.player.activity.HomeScreenInterface;
import org.kfjc.android.player.control.PreferenceControl;
import org.kfjc.android.player.model.KfjcMediaSource;

import java.util.List;

public class SettingsDialog extends KfjcDialog {

    public static final String KEY_ONLY_VOLUME = "onlyVolume";

    public interface StreamUrlPreferenceChangeHandler {
        void onStreamUrlPreferenceChange();
    }

    private SeekBar volumeSeekbar;
    private AudioManager audioManager;
    private Spinner spinner;
    private SwitchCompat backgroundSwitch;
    private KfjcMediaSource previousPreference;
    private StreamUrlPreferenceChangeHandler urlPreferenceChangeHandler;
    private ContextThemeWrapper themeWrapper;
    private HomeScreenInterface home;
    private BroadcastReceiver volumeChangeReceiver;
    private ContentObserver settingsContentObserver;

    public static SettingsDialog newInstance(boolean onlyVolume) {
        Bundle args = new Bundle();
        args.putBoolean(KEY_ONLY_VOLUME, onlyVolume);

        SettingsDialog fragment = new SettingsDialog();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        previousPreference = PreferenceControl.getStreamPreference();

        themeWrapper = new ContextThemeWrapper(getActivity(), R.style.AppTheme);
        View view = View.inflate(themeWrapper, R.layout.layout_settings, null);
        initVolumeBar(view);

        boolean onlyVolumeMode = getArguments().getBoolean(KEY_ONLY_VOLUME, false);
        if (!onlyVolumeMode) {
            View divider = view.findViewById(R.id.settingDivider);
            View quality = view.findViewById(R.id.settingQuality);
            View backgrounds = view.findViewById(R.id.settingBackgrounds);
            divider.setVisibility(View.VISIBLE);
            quality.setVisibility(View.VISIBLE);
            backgrounds.setVisibility(View.VISIBLE);

            spinner = (Spinner) view.findViewById(R.id.streamPreferenceSpinner);
            spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    KfjcMediaSource kfjcMediaSource = (KfjcMediaSource) parent.getItemAtPosition(position);
                    PreferenceControl.setStreamPreference(kfjcMediaSource);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            backgroundSwitch = (SwitchCompat) view.findViewById(R.id.backgroundSwitch);
            backgroundSwitch.setChecked(PreferenceControl.areBackgroundsEnabled());
            backgroundSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    PreferenceControl.setEnableBackgrounds(isChecked);
                    home.updateBackground();
                }
            });

            initStreamOptions();
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity(), R.style.AppTheme);
        dialog.setView(view);
        dialog.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                boolean urlPreferenceChanged =
                        !previousPreference.equals(PreferenceControl.getStreamPreference());
                if (urlPreferenceChanged && urlPreferenceChangeHandler != null) {
                    urlPreferenceChangeHandler.onStreamUrlPreferenceChange();
                }
            }
        });
        dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialogInterface, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                    keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                    keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                    if (volumeSeekbar != null && audioManager != null) {
                        volumeSeekbar.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (audioManager != null && volumeSeekbar != null) {
                                    int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                                    volumeSeekbar.setProgress(currentVol);
                                }
                            }
                        }, 50);
                    }
                }
                return false;
            }
        });
        return dialog.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            boolean isAutomotive = getActivity() != null &&
                    getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
            if (isAutomotive) {
                int maxWidthPx = (int) (520 * getResources().getDisplayMetrics().density);
                int screenWidthPx = getResources().getDisplayMetrics().widthPixels;
                int targetWidthPx = Math.min(maxWidthPx, (int) (screenWidthPx * 0.55f));
                dialog.getWindow().setLayout(targetWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof HomeScreenInterface)) {
            throw new IllegalStateException(
                    "Can only attach to " + HomeScreenInterface.class.getSimpleName());
        }
        this.home = (HomeScreenInterface) activity;
    }

    public void setUrlPreferenceChangeHandler(StreamUrlPreferenceChangeHandler handler) {
        this.urlPreferenceChangeHandler = handler;
    }

    private void initStreamOptions() {
        List<KfjcMediaSource> kfjcMediaSources = PreferenceControl.getKfjcMediaSources();
        StreamAdapter streamAdapter = new StreamAdapter(
                themeWrapper, android.R.layout.simple_spinner_item, kfjcMediaSources);
        spinner.setAdapter(streamAdapter);
        int selectedIndex = kfjcMediaSources.indexOf(PreferenceControl.getStreamPreference());
        spinner.setSelection(Math.max(0, selectedIndex));
    }

    private void initVolumeBar(View view) {
        volumeSeekbar = (SeekBar) view.findViewById(R.id.volumeSeekBar);
        audioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        volumeSeekbar.setMax(audioManager
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        volumeSeekbar.setProgress(audioManager
                .getStreamVolume(AudioManager.STREAM_MUSIC));
        volumeSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar arg0) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar arg0) {
            }

            @Override
            public void onProgressChanged(SeekBar arg0, int progress, boolean arg2) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
            }
        });

        settingsContentObserver =
                new ContentObserver(new Handler()) {
                    @Override
                    public void onChange(boolean selfChange) {
                        int volumeLevel = audioManager
                                .getStreamVolume(AudioManager.STREAM_MUSIC);
                        volumeSeekbar.setProgress(volumeLevel);
                    }
                };
        getActivity().getContentResolver().registerContentObserver(
                android.provider.Settings.System.CONTENT_URI, true, settingsContentObserver);

        volumeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (audioManager != null && volumeSeekbar != null) {
                    int volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    volumeSeekbar.setProgress(volumeLevel);
                }
            }
        };
        getActivity().registerReceiver(volumeChangeReceiver,
                new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
    }

    @Override
    public void onStop() {
        super.onStop();
        if (settingsContentObserver != null && getActivity() != null) {
            getActivity().getContentResolver().unregisterContentObserver(settingsContentObserver);
            settingsContentObserver = null;
        }
        if (volumeChangeReceiver != null && getActivity() != null) {
            try {
                getActivity().unregisterReceiver(volumeChangeReceiver);
            } catch (IllegalArgumentException ignored) {}
            volumeChangeReceiver = null;
        }
    }

    private class StreamAdapter extends ArrayAdapter<KfjcMediaSource> {
        private List<KfjcMediaSource> kfjcMediaSources;
        private Context context;
        public StreamAdapter(Context context, int resource, List<KfjcMediaSource> kfjcMediaSources) {
            super(context, resource, kfjcMediaSources);
            this.context = context;
            this.kfjcMediaSources = kfjcMediaSources;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = LayoutInflater.from(context).inflate(
                    android.R.layout.simple_spinner_item, parent, false);
            TextView streamName = (TextView) view.findViewById(android.R.id.text1);
            streamName.setText(kfjcMediaSources.get(position).name);
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View view = LayoutInflater.from(context).inflate(
                    android.R.layout.simple_list_item_2, parent, false);
            TextView streamName = (TextView) view.findViewById(android.R.id.text1);
            TextView streamDesc = (TextView) view.findViewById(android.R.id.text2);
            streamName.setText(kfjcMediaSources.get(position).name);
            streamDesc.setText(kfjcMediaSources.get(position).description);
            streamDesc.setTextColor(ContextCompat.getColor(context, R.color.kfjc_secondary_text));
            return view;
        }
    }
}