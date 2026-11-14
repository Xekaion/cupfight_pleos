package io.autohmg.cupfight;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/** Short water cues. Call on the main thread along with the Activity lifecycle. */
final class WaterAudio {
    private static final long COOLDOWN_MS = 1800L;
    private static final long FOCUS_WINDOW_MS = 420L;
    private static final int[] RESOURCES = {
            R.raw.water_drop_1, R.raw.water_drop_2, R.raw.water_drop_3
    };
    private static final float[] VOLUMES = {0.20f, 0.24f, 0.28f};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable endCue = this::stop;
    private final AudioManager audioManager;
    private final AudioFocusRequest focusRequest;
    private final SoundPool pool;
    private final int[] samples = new int[RESOURCES.length];
    private final boolean[] loaded = new boolean[RESOURCES.length];

    private long nextCueAtMillis;
    private int streamId;
    private boolean hasFocus;
    private boolean released;

    WaterAudio(Context context) {
        audioManager = context.getSystemService(AudioManager.class);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (change < 0) stop();
                }, handler)
                .build();
        pool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(attributes)
                .build();
        pool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (released || status != 0) return;
            for (int i = 0; i < samples.length; i++) {
                if (samples[i] == sampleId) loaded[i] = true;
            }
        });
        for (int i = 0; i < RESOURCES.length; i++) {
            samples[i] = pool.load(context, RESOURCES[i], 1);
        }
    }

    void play(int level) {
        if (released || hasFocus || audioManager == null || level < 1 || level > samples.length) return;
        int index = level - 1;
        long now = SystemClock.uptimeMillis();
        if (!loaded[index] || now < nextCueAtMillis) return;
        nextCueAtMillis = now + COOLDOWN_MS;

        // Yield to current audio without asking its owner to pause or lower the volume.
        if (audioManager.isMusicActive() || !audioManager.getActivePlaybackConfigurations().isEmpty()) {
            return;
        }
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return;
        }
        hasFocus = true;
        float volume = VOLUMES[index];
        streamId = pool.play(samples[index], volume, volume, 1, 0, 1f);
        if (streamId == 0) {
            stop();
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.d("CupFightAudio", "Playing water cue " + level);
        }
        handler.postDelayed(endCue, FOCUS_WINDOW_MS);
    }

    void stop() {
        handler.removeCallbacks(endCue);
        if (streamId != 0) {
            pool.stop(streamId);
            streamId = 0;
        }
        if (hasFocus) {
            hasFocus = false;
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
    }

    void release() {
        if (released) return;
        released = true;
        stop();
        pool.setOnLoadCompleteListener(null);
        pool.release();
    }
}
