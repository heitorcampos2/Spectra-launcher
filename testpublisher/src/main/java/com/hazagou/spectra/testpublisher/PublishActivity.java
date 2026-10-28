package com.hazagou.spectra.testpublisher;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/** Publica 2 canais (um normal e um "Inscrições"), 6 programas e 2 itens de "ver a seguir". */
public class PublishActivity extends Activity {
    private static final String TAG = "TestPublisher";
    private static final Uri CHANNELS = Uri.parse("content://android.media.tv/channel");
    private static final Uri PREVIEW = Uri.parse("content://android.media.tv/preview_program");
    private static final Uri WATCH_NEXT = Uri.parse("content://android.media.tv/watch_next_program");

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        new Thread(() -> {
            try {
                publish();
            } catch (Exception e) {
                Log.e(TAG, "falha ao publicar", e);
            }
            runOnUiThread(this::finish);
        }).start();
    }

    private void publish() {
        ContentResolver cr = getContentResolver();
        cr.delete(WATCH_NEXT, null, null);
        cr.delete(CHANNELS, null, null);
        String intent = "intent:#Intent;component=" + getPackageName() + "/.PublishActivity;end";
        long ch1 = channel(cr, "Canal de Teste", intent);
        long ch2 = channel(cr, "Inscrições", intent);
        for (int i = 1; i <= 3; i++) program(cr, ch1, "Filme de teste " + i, i, intent);
        for (int i = 1; i <= 3; i++) program(cr, ch2, "Vídeo inscrito " + i, 10 + i, intent);
        for (int i = 1; i <= 2; i++) {
            ContentValues v = new ContentValues();
            v.put("type", 3); // episódio
            v.put("watch_next_type", 0); // continuar assistindo
            v.put("title", "Série de teste");
            v.put("episode_title", "Episódio " + i);
            v.put("season_display_number", "1");
            v.put("episode_display_number", String.valueOf(i));
            v.put("poster_art_uri", "https://picsum.photos/seed/wn" + i + "/640/360");
            v.put("poster_art_aspect_ratio", 0);
            v.put("intent_uri", intent);
            v.put("duration_millis", 1_500_000);
            v.put("last_playback_position_millis", 780_000);
            v.put("last_engagement_time_utc_millis", System.currentTimeMillis() - i * 1000L);
            Uri u = cr.insert(WATCH_NEXT, v);
            Log.i(TAG, "ver a seguir: " + u);
        }
        Log.i(TAG, "publicado ok");
    }

    private long channel(ContentResolver cr, String name, String intent) {
        ContentValues v = new ContentValues();
        v.put("type", "TYPE_PREVIEW");
        v.put("display_name", name);
        v.put("app_link_intent_uri", intent);
        Uri u = cr.insert(CHANNELS, v);
        Log.i(TAG, "canal " + name + ": " + u);
        return ContentUris.parseId(u);
    }

    private void program(ContentResolver cr, long channelId, String title, int seed, String intent) {
        ContentValues v = new ContentValues();
        v.put("channel_id", channelId);
        v.put("type", 0); // filme
        v.put("title", title);
        v.put("poster_art_uri", "https://picsum.photos/seed/p" + seed + "/640/360");
        v.put("poster_art_aspect_ratio", 0);
        v.put("intent_uri", intent);
        v.put("weight", 100 - seed);
        Log.i(TAG, "programa " + title + ": " + cr.insert(PREVIEW, v));
    }
}
