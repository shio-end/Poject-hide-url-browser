package com.hideurl.browser;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.view.View;

final class TapSound {
    private static SoundPool pool;
    private static int sample;
    private static volatile boolean ready;

    static void prepare(Context context) {
        if (pool != null) return;
        pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((sounds, id, result) -> ready = result == 0);
        sample = pool.load(context.getApplicationContext(), R.raw.tap, 1);
    }

    static void play(Context context) {
        if (ready && context.getSharedPreferences("browser", Context.MODE_PRIVATE).getBoolean("tapSound", true)
                && context.getSystemService(AudioManager.class).getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
            pool.play(sample, .55f, .55f, 1, 0, 1f);
        }
    }

    static void bind(View view, Runnable action) {
        view.setSoundEffectsEnabled(false);
        view.setOnClickListener(v -> {
            play(v.getContext());
            action.run();
        });
    }
}
