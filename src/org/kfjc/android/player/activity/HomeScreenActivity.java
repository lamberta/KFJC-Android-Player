package org.kfjc.android.player.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

import org.kfjc.android.player.model.TrackInfo;
import org.kfjc.android.player.control.HomeScreenControl;
import org.kfjc.android.player.util.GraphicsUtil;
import org.kfjc.android.player.util.UiUtil;
import org.kfjc.android.player.R;

public class HomeScreenActivity extends Activity {
	
	public enum PlayerState {
		PLAY,
        STOP,
        BUFFER
	}

    public enum StatusState {
        HIDDEN,                 // Shows current track instead
        CONNECTING,
        CONNECTION_ERROR
    }

    private ImageView radioDevil;
    private ImageView settingsButton;
    private ImageView backgroundImageView;
    private FloatingActionButton playStopButton;

    private LinearLayout nowPlayingContainer;
	private TextView currentDjTextView;
	private TextView currentTrackTextView;

    private LinearLayout statusContainer;
    private TextView statusMessageTextView;

    private StatusState connectionStatusState = StatusState.CONNECTION_ERROR;
	private PlayerState playerState = PlayerState.PLAY;
	private GraphicsUtil graphics;
	private static HomeScreenControl control;
    private boolean isForegroundActivity = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

		setContentView(R.layout.activity_home_screen);
		graphics = new GraphicsUtil(getResources());
		playStopButton = (FloatingActionButton) findViewById(R.id.playstopbutton);
        backgroundImageView = (ImageView) findViewById(R.id.backgroundImageView);
		settingsButton = (ImageView) findViewById(R.id.settingsButton);
		radioDevil = (ImageView) findViewById(R.id.logo);
		radioDevil.setImageResource(graphics.radioDevilOff());

        nowPlayingContainer = (LinearLayout) findViewById(R.id.nowPlayingContainer);
		currentDjTextView = (TextView) findViewById(R.id.currentDJ);
		currentTrackTextView = (TextView) findViewById(R.id.currentTrack);

        statusContainer = (LinearLayout) findViewById(R.id.statusContainer);
        statusMessageTextView = (TextView) findViewById(R.id.statusMessage);

		addButtonListeners();
        setStatusState(StatusState.CONNECTING);

        control = new HomeScreenControl(this);
        control.onCreate();
	}
	
	@Override
	protected void onStart() {
		super.onStart();
        control.onStart();
	}

    @Override
    protected void onStop() {
        super.onStop();
        control.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isForegroundActivity = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForegroundActivity = true;
        control.onResume();
        int hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        backgroundImageView.setImageResource(GraphicsUtil.imagesOfTheHour[hourOfDay]);
    }

    @Override
	public void onDestroy() {
        if (isFinishing()) {
            control.destroy();
        }
		super.onDestroy();
	}

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    private void addButtonListeners() {
		playStopButton.setOnTouchListener(UiUtil.buttonTouchListener);
		playStopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (playerState) {
                    case STOP:
                        control.playStream();
                        break;
                    case BUFFER:
                    case PLAY:
                        control.stopStream();
                        break;
                }
            }
        });
        playStopButton.setEnabled(false);
		radioDevil.setOnTouchListener(UiUtil.buttonTouchListener);
		radioDevil.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent fullscreenIntent = new Intent(
                        HomeScreenActivity.this, LavaLampActivity.class);
                fullscreenIntent.setAction("org.kfjc.android.player.FULLSCREEN");
                startActivity(fullscreenIntent);
            }
        });
        radioDevil.setEnabled(false);
        settingsButton.setEnabled(false);
		settingsButton.setOnTouchListener(UiUtil.buttonTouchListener);
		settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                control.showSettings();
            }
        });
	}

    public void setState(PlayerState state) {
        playerState = state;
        switch(state) {
            case STOP:
                graphics.bufferDevil(radioDevil, false);
                playStopButton.setImageResource(R.drawable.ic_play_arrow_white_48dp);
                radioDevil.setImageResource(graphics.radioDevilOff());
                radioDevil.setEnabled(false);
                break;
            case PLAY:
                graphics.bufferDevil(radioDevil, false);
                playStopButton.setImageResource(R.drawable.ic_stop_white_48dp);
                radioDevil.setImageResource(graphics.radioDevilOn());
                radioDevil.setEnabled(true);
                break;
            case BUFFER:
                graphics.bufferDevil(radioDevil, true);
                playStopButton.setImageResource(R.drawable.ic_stop_white_48dp);
                radioDevil.setEnabled(false);
                break;
        }
    }

	public void updateTrackInfo(TrackInfo nowPlaying) {
        setStatusState(StatusState.HIDDEN);
        if (nowPlaying.getCouldNotFetch()) {
            setStatusState(StatusState.CONNECTION_ERROR);
        } else {
            currentDjTextView.setText(nowPlaying.getDjName());
            currentTrackTextView.setText(nowPlaying.artistTrackHtml());
        }
	}

    public void enableSettingsButton() {
        this.settingsButton.setEnabled(true);
    }

    public void enablePlayStopButton() {
        this.playStopButton.setEnabled(true);
    }

    public void setStatusState(StatusState state) {
        nowPlayingContainer.setVisibility(View.GONE);
        statusContainer.setVisibility(View.GONE);
        switch(state) {
            case HIDDEN:
                nowPlayingContainer.setVisibility(View.VISIBLE);
                break;
            case CONNECTING:
                if (this.connectionStatusState == StatusState.CONNECTION_ERROR) {
                    statusContainer.setVisibility(View.VISIBLE);
                    statusMessageTextView.setText(R.string.status_connecting);
                } else {
                    setStatusState(StatusState.HIDDEN);
                }
                break;
            case CONNECTION_ERROR:
                statusContainer.setVisibility(View.VISIBLE);
                statusMessageTextView.setText(R.string.status_not_connected);
                break;
        }
        this.connectionStatusState = state;
    }

    public boolean isForegroundActivity() {
        return isForegroundActivity;
    }

    public void showDebugAlert(final String message) {
        new AlertDialog.Builder(this)
                .setTitle("Error details")
                .setMessage(message)
                .setPositiveButton("Copy", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        ClipboardManager clipboard = (ClipboardManager)
                                getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("KFJC error", message);
                        clipboard.setPrimaryClip(clip);
                    }
                })
                .show();
    }
}
