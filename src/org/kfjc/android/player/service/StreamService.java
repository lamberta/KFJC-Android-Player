package org.kfjc.android.player.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;

import java.util.ArrayList;
import java.util.List;

import org.kfjc.android.player.Constants;
import org.kfjc.android.player.R;
import org.kfjc.android.player.control.PreferenceControl;
import org.kfjc.android.player.intent.PlayerControl;
import org.kfjc.android.player.intent.PlayerState;
import org.kfjc.android.player.model.KfjcMediaSource;
import org.kfjc.android.player.util.NotificationUtil;

/**
 * Background audio streaming service for KFJC audio playback.
 * Extends MediaBrowserServiceCompat to support Android Auto and external media clients.
 */
public class StreamService extends MediaBrowserServiceCompat {

    private static final String TAG = StreamService.class.getSimpleName();
    private static final IntentFilter becomingNoisyIntentFilter =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

	public class LiveStreamBinder extends Binder {
		public StreamService getService() {
			return StreamService.this;
		}
	}

    private KfjcMediaSource mediaSource;
    private PlayerState playerState = new PlayerState();
	private final IBinder liveStreamBinder = new LiveStreamBinder();
    private ExoPlayer player;
    private MediaSessionCompat mediaSession;
    private boolean becomingNoisyReceiverRegistered = false;
    private NotificationUtil notificationUtil;

    /**
     * The Becoming Noisy broadcast intent is sent when audio output hardware changes, perhaps
     * from headphones to internal speaker. In such cases, we stop the stream to avoid
     * embarrassment.
     */
    private BroadcastReceiver onAudioBecomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                if (mediaSource == null) {
                    return;
                }
                switch (mediaSource.type) {
                    case ARCHIVE:
                        pause();
                        break;
                    case LIVESTREAM:
                        stop();
                        break;
                }
            }
        }
    };

    /** Sets up MediaSessionCompat for Android Auto and media controls. */
    @Override
    public void onCreate() {
        super.onCreate();
        mediaSession = new MediaSessionCompat(this, TAG);
        setSessionToken(mediaSession.getSessionToken());
        mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC);
        mediaSession.setActive(true);
        updateMediaSessionState(PlaybackStateCompat.STATE_STOPPED);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                KfjcMediaSource source = PreferenceControl.getStreamPreference();
                if (source != null) {
                    StreamService.this.play(source);
                }
            }

            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                KfjcMediaSource targetSource = null;
                for (KfjcMediaSource s : PreferenceControl.getKfjcMediaSources()) {
                    if (mediaId != null && mediaId.equals(s.url)) {
                        targetSource = s;
                        break;
                    }
                }
                if (targetSource == null) {
                    targetSource = PreferenceControl.getStreamPreference();
                }
                StreamService.this.play(targetSource);
            }

            @Override
            public void onPlayFromSearch(String query, Bundle extras) {
                KfjcMediaSource source = PreferenceControl.getStreamPreference();
                if (source != null) {
                    StreamService.this.play(source);
                }
            }

            @Override
            public void onPause() {
                StreamService.this.pause();
            }

            @Override
            public void onStop() {
                StreamService.this.stop();
            }
        });
    }

    /** Required for Android Auto / external media clients: allows browsing stream hierarchy. */
    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot("KFJC_ROOT", null);
    }

    /** Required for Android Auto / external media clients: returns playable stream items. */
    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> items = new ArrayList<>();
        for (KfjcMediaSource source : PreferenceControl.getKfjcMediaSources()) {
            MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                    .setMediaId(source.url)
                    .setTitle(source.name)
                    .setSubtitle(source.description)
                    .setIconBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                    .build();
            items.add(new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
        }
        result.sendResult(items);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        notificationUtil = new NotificationUtil(this);

        if (intent != null) {
            if (PlayerControl.INTENT_STOP.equals(intent.getAction())) {
                stop();
            } else if (PlayerControl.INTENT_PAUSE.equals(intent.getAction())) {
                pause();
            } else if (PlayerControl.INTENT_UNPAUSE.equals(intent.getAction())) {
                unpause();
            } else if (PlayerControl.INTENT_PLAY.equals(intent.getAction())) {
                KfjcMediaSource source = intent.getParcelableExtra(PlayerControl.INTENT_SOURCE);
                if (getSource() == null || !getSource().equals(source) || !isPlaying()) {
                    stop();
                    play(source);
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /** Routes MediaBrowserService intent to super for Android Auto; uses liveStreamBinder for app UI. */
    @Override
	public IBinder onBind(Intent intent) {
        if (SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return liveStreamBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		return false;
	}

    @Override
    public void onDestroy() {
        super.onDestroy();
        notificationUtil.cancelKfjcNotification();
        if (player != null) {
            player.stop();
            player.release();
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }

    public boolean isPlaying() {
        return player != null && (
               player.getPlayWhenReady() &&
               player.getPlaybackState() == ExoPlayer.STATE_READY ||
               player.getPlaybackState() == ExoPlayer.STATE_BUFFERING);
	}

    private boolean isPaused() {
        return player.getPlaybackState() == ExoPlayer.STATE_READY
                && !player.getPlayWhenReady();
    }

    private void play(KfjcMediaSource mediaSource) {
        this.mediaSource = mediaSource;
        stop();
        if (mediaSession != null) {
            mediaSession.setActive(true);
        }
        updateMediaSessionState(PlaybackStateCompat.STATE_BUFFERING);
        if (mediaSource.type == KfjcMediaSource.Type.LIVESTREAM) {
            Notification n = notificationUtil.kfjcStreamNotification(
                    getApplicationContext(),
                    getSource(),
                    PlayerControl.INTENT_STOP,
                    true);
            startForeground(NotificationUtil.KFJC_NOTIFICATION_ID, n);
            play(mediaSource.getMediaSource(this));
        } else if (mediaSource.type == KfjcMediaSource.Type.ARCHIVE) {
            Notification n = notificationUtil.kfjcStreamNotification(
                    getApplicationContext(),
                    getSource(),
                    PlayerControl.INTENT_PAUSE,
                    false);
            startForeground(NotificationUtil.KFJC_NOTIFICATION_ID, n);
            play(mediaSource.getMediaSource(this));
        }
    }

    private void requestAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
    }

    private void abandonAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.abandonAudioFocus(audioFocusListener);
    }

    private void play(MediaSource source) {
        if (player == null) {
            player = ExoPlayerFactory.newSimpleInstance(getApplicationContext());
            player.addListener(exoEventListener);
        }
        requestAudioFocus();
        player.prepare(source);
        player.setPlayWhenReady(true);
    }

    private void pause() {
        if (player == null) {
            return;
        }
        player.setPlayWhenReady(false);
        abandonAudioFocus();
        updateMediaSessionState(PlaybackStateCompat.STATE_PAUSED);
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        Notification n = NotificationUtil.kfjcStreamNotification(
                getApplicationContext(),
                getSource(),
                PlayerControl.INTENT_UNPAUSE,
                false);
        notificationManager.notify(NotificationUtil.KFJC_NOTIFICATION_ID, n);
    }

    private void unpause() {
        if (mediaSource.type == KfjcMediaSource.Type.LIVESTREAM) {
            play(mediaSource);
        }
        else if (isPaused()) {
            Log.i(TAG, "Unpausing");
            requestAudioFocus();
            player.setPlayWhenReady(true);
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
            Notification n = NotificationUtil.kfjcStreamNotification(
                    getApplicationContext(),
                    getSource(),
                    PlayerControl.INTENT_PAUSE,
                    false);
            notificationManager.notify(NotificationUtil.KFJC_NOTIFICATION_ID, n);
            return;
        }
    }

    private void stop() {
        stop(true);
	}

    private void stop(boolean alsoReset) {
        abandonAudioFocus();
        if (player != null) {
            player.stop();
            Log.i(TAG, "Player stopped");
        }
        if (alsoReset) {
            reset();
        }
    }

    private void reset() {
        if (player != null) {
            player.release();
            player = null;
            playerState.send(getApplicationContext(), PlayerState.State.STOP, mediaSource);
        }
        updateMediaSessionState(PlaybackStateCompat.STATE_STOPPED);
        if (mediaSession != null) {
            mediaSession.setActive(false);
        }
        unregisterReceivers();
        becomingNoisyReceiverRegistered = false;
        stopForeground(true);
        Log.i(TAG, "Service stopped");
    }

    private void unregisterReceivers() {
        try {
            if (becomingNoisyReceiverRegistered) {
                unregisterReceiver(onAudioBecomingNoisyReceiver);
            }
        } catch (IllegalArgumentException e) {
            // receiver was already unregistered.
        }
    }

    private Player.EventListener exoEventListener = new Player.EventListener() {
        @Override
        public void onLoadingChanged(boolean isLoading) {}

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int state) {
            switch (state) {
                case Player.STATE_READY:
                    if (playWhenReady) {
                        playerState.send(getApplicationContext(), PlayerState.State.PLAY, mediaSource);
                        updateMediaSessionState(PlaybackStateCompat.STATE_PLAYING);
                        registerReceiver(onAudioBecomingNoisyReceiver, becomingNoisyIntentFilter);
                    } else {
                        playerState.send(getApplicationContext(), PlayerState.State.PAUSE, mediaSource);
                        updateMediaSessionState(PlaybackStateCompat.STATE_PAUSED);
                    }
                    break;
                case Player.STATE_BUFFERING:
                    playerState.send(getApplicationContext(), PlayerState.State.BUFFER, mediaSource);
                    updateMediaSessionState(PlaybackStateCompat.STATE_BUFFERING);
                    break;
                case Player.STATE_ENDED:
                    playerState.send(getApplicationContext(), PlayerState.State.STOP, mediaSource);
                    updateMediaSessionState(PlaybackStateCompat.STATE_STOPPED);
                    unregisterReceivers();
                    stop(true);
                    break;
                case Player.STATE_IDLE:
                    break;
            }
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {

        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

        }

        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
            Log.i("KFJC StreamService", "exoplayer onTracksChanged");
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            Log.e(TAG, "ExoPlaybackException: " + error.getMessage());
            playerState.send(getApplicationContext(), PlayerState.State.ERROR, error.getMessage());
            updateMediaSessionState(PlaybackStateCompat.STATE_ERROR);
        }

        @Override
        public void onPositionDiscontinuity(int reason) {}

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {}

        @Override
        public void onSeekProcessed() {

        }
    };

    public void sendPlaybackState() {
        if (player != null && exoEventListener != null) {
            exoEventListener.onPlayerStateChanged(
                    player.getPlayWhenReady(), player.getPlaybackState());
        }
    }

    /** Syncs ExoPlayer state and metadata (Title, Artist, Album Art) with MediaSessionCompat. */
    private void updateMediaSessionState(int state) {
        if (mediaSession == null) {
            return;
        }
        long actions = PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_STOP |
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH;
        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build();
        mediaSession.setPlaybackState(playbackState);

        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "KFJC 89.7 FM")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaSource != null ? mediaSource.name : "Live Stream")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Foothill College Radio")
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                        BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher));
        mediaSession.setMetadata(metadataBuilder.build());
    }

    public long getPlayerPosition() {
        if (player == null) {
            return 0;
        }
        return Constants.SHOW_SEGMENT_LENGTH * player.getCurrentPeriodIndex() + player.getCurrentPosition();
    }

    public void seek(long positionMillis) {
        long HOUR = 3600000;
        int targetWindow = 0;
        while (positionMillis > HOUR) {
            positionMillis -= HOUR;
            targetWindow++;
        }
        player.seekTo(targetWindow, positionMillis);
    }

    public KfjcMediaSource getSource() {
        return mediaSource;
    }

    /**
     * Audio Focus changes when another app requests access to the audio output stream. It could be
     * total access (eg. another music player) or transient (eg. spoken directions from maps). In
     * the latter case, we dip the volume for the other app and raise it again when the other app is
     * done.
     */
    private AudioManager.OnAudioFocusChangeListener audioFocusListener = new AudioManager.OnAudioFocusChangeListener() {

        private int volumeBeforeLoss;
        private static final String AUDIOFOCUS_KEY =
                "org.kfjc.android.player.control_AUDIO_FOCUS_CHANGE_LISTENER";

        @Override public void onAudioFocusChange(int focusChange) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.i(TAG, "Lost audio focus");
                    volumeBeforeLoss = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    if (mediaSource != null && mediaSource.type == KfjcMediaSource.Type.ARCHIVE) {
                        pause();
                    } else {
                        stop();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.i(TAG, "Ducking audio focus");
                    volumeBeforeLoss = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBeforeLoss / 2, 0);
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.i(TAG, "Gained audio focus");
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBeforeLoss, 0);
                    break;
            }
        }

        // Objects are recreated after activity resume. Audio focus is requested and released
        // by reference to an OnAudioFocusChangeListener, and subsequently its toString()
        // method. By returning a constant string, we can consistently refer to the audio
        // focus we requested before the app was paused and resumed.
        @Override public String toString() {
            return AUDIOFOCUS_KEY;
        }
    };

}
