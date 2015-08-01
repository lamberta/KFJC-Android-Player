package org.kfjc.android.player.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.source.DefaultSampleSource;
import com.google.android.exoplayer.source.FrameworkSampleExtractor;

import org.kfjc.android.player.model.TrackInfo;

// TODO: Stop playlist fetcher when not playing and in background.
public class LiveStreamService extends Service {

    public interface MediaListener {
        public void onBuffer();
        public void onPlay();
        public void onError(String message);
        public void onEnd();
        public void onTrackInfoFetched(TrackInfo trackInfo);
    }

	public class LiveStreamBinder extends Binder {
		public LiveStreamService getService() {
			return LiveStreamService.this;
		}
	}

	private MediaListener mediaListener;
	private static NowPlayingFetcher nowPlayingFetcher;
	private final IBinder liveStreamBinder = new LiveStreamBinder();
	private boolean isFetching = false;
    private ExoPlayer player;
	
	@Override
	public IBinder onBind(Intent intent) {
		return liveStreamBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		nowPlayingFetcher.stop();
        if (player != null) {
            player.stop();
            player.release();
        }
		return false;
	}
	
	public boolean isPlaying() {
        return player != null && (
               player.getPlaybackState() == ExoPlayer.STATE_READY ||
               player.getPlaybackState() == ExoPlayer.STATE_BUFFERING);
	}
	
	public void setMediaEventListener(MediaListener listener) {
		this.mediaListener = listener;
		this.nowPlayingFetcher = new NowPlayingFetcher(mediaListener);
	}
	
	public void play(Context context, String streamUrl) {
        initPlayer();

        Uri streamUri = Uri.parse(streamUrl);
        FrameworkSampleExtractor sampleExtractor = new FrameworkSampleExtractor(context, streamUri, null);
        DefaultSampleSource sampleSource = new DefaultSampleSource(sampleExtractor, 1);
        MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(
                sampleSource, null, true);
        player.prepare(audioRenderer);
        player.setPlayWhenReady(true);
        player.addListener(makeExoplayerListener());

    }

	public void stop() {
        if (player != null) {
            player.stop();
        }
	}

    private void initPlayer() {
        player = ExoPlayer.Factory.newInstance(1);
    }
	
	public void runPlaylistFetcherOnce() {
		nowPlayingFetcher.runOnce();
	}
	
	public void runPlaylistFetcher() {
		if (!isFetching) {
			nowPlayingFetcher.runOnce();
			nowPlayingFetcher.run();
		}
		isFetching = true;
	}

    private ExoPlayer.Listener makeExoplayerListener() {
        return new ExoPlayer.Listener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int state) {
                switch (state) {
                    case ExoPlayer.STATE_READY:
                        if (playWhenReady) {
                            mediaListener.onPlay();
                        }
                        break;
                    case ExoPlayer.STATE_PREPARING:
                        mediaListener.onBuffer();
                        break;
                    case ExoPlayer.STATE_ENDED:
                    case ExoPlayer.STATE_IDLE:
                        mediaListener.onEnd();
                }

            }

            @Override
            public void onPlayWhenReadyCommitted() {}

            @Override
            public void onPlayerError(ExoPlaybackException e) {
               mediaListener.onError(e.getMessage());
            }
        };
    }

}
