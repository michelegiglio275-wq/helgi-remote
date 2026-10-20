package com.helgi.remote;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Invisible launcher Activity.
 * Immediately starts the RemoteService foreground service, then finishes itself
 * so no window ever appears on the Helgi board's screen.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent serviceIntent = new Intent(this, RemoteService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Finish immediately — no UI shown
        finish();
    }
}
