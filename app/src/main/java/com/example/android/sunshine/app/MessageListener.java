package com.example.android.sunshine.app;

import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by DivyaM on 4/21/16.
 */
public class MessageListener extends WearableListenerService {

    private static final String  SYNC_NOW = "/sync_now";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        Log.i("Log", "message received");

        if(messageEvent.getPath().equals(SYNC_NOW)){
            SunshineSyncAdapter.syncImmediately(getApplicationContext());
        }
    }
}
