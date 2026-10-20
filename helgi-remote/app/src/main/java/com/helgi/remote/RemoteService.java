package com.helgi.remote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

/**
 * Foreground service that keeps the ControlServer HTTP server alive.
 * A persistent status-bar notification shows the board's IP address so the
 * user always knows where to connect from their phone.
 */
public class RemoteService extends Service {

    private static final String TAG = "HelgiRemote";
    private static final String CHANNEL_ID = "helgi_remote_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int SERVER_PORT = 8080;

    private ControlServer controlServer;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String ip = getLocalIpAddress();
        startForeground(NOTIFICATION_ID, buildNotification(ip));

        if (controlServer == null) {
            try {
                controlServer = new ControlServer(this, SERVER_PORT);
                controlServer.start();
                Log.i(TAG, "HTTP server started on port " + SERVER_PORT);
            } catch (IOException e) {
                Log.e(TAG, "Failed to start HTTP server", e);
                stopSelf();
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (controlServer != null) {
            controlServer.stop();
            controlServer = null;
            Log.i(TAG, "HTTP server stopped");
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Helgi Remote",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Helgi Board Remote Control server");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String ip) {
        String contentText = (ip != null)
                ? "Connettiti su http://" + ip + ":" + SERVER_PORT
                : "Server HTTP attivo sulla porta " + SERVER_PORT;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Helgi Remote — Attivo")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    // -------------------------------------------------------------------------
    // Network helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the device's primary IPv4 address, preferring the Wi-Fi interface.
     * Returns null if no address is found.
     */
    public static String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces =
                    Collections.list(NetworkInterface.getNetworkInterfaces());
            // Prefer wlan0 / eth0
            for (NetworkInterface intf : interfaces) {
                String name = intf.getName().toLowerCase();
                if (!intf.isUp() || intf.isLoopback()) continue;
                if (name.startsWith("wlan") || name.startsWith("eth")) {
                    List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                    for (InetAddress addr : addrs) {
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
            // Fallback: any IPv4
            for (NetworkInterface intf : interfaces) {
                if (!intf.isUp() || intf.isLoopback()) continue;
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getLocalIpAddress failed", e);
        }
        return null;
    }
}
