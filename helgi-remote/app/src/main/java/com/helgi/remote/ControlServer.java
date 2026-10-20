package com.helgi.remote;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * NanoHTTPD subclass that implements all REST endpoints and serves the
 * embedded HTML control dashboard.
 */
public class ControlServer extends NanoHTTPD {

    private static final String TAG = "HelgiControlServer";

    private final Context context;
    private final AudioManager audioManager;
    private final PowerManager powerManager;

    // =========================================================================
    // Construction
    // =========================================================================

    public ControlServer(Context context, int port) throws IOException {
        super(port);
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        this.powerManager = (PowerManager) this.context.getSystemService(Context.POWER_SERVICE);
    }

    // =========================================================================
    // Request routing
    // =========================================================================

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, method + " " + uri);

        try {
            switch (uri) {
                case "/":
                    return serveHtmlDashboard();

                case "/status":
                    if (Method.GET.equals(method)) return handleStatus();
                    break;

                case "/volume/up":
                    if (Method.POST.equals(method)) return handleVolumeUp();
                    break;

                case "/volume/down":
                    if (Method.POST.equals(method)) return handleVolumeDown();
                    break;

                case "/volume/set":
                    if (Method.POST.equals(method)) return handleVolumeSet(session);
                    break;

                case "/shutdown":
                    if (Method.POST.equals(method)) return handleShutdown();
                    break;

                case "/reboot":
                    if (Method.POST.equals(method)) return handleReboot();
                    break;

                case "/close-apps":
                    if (Method.POST.equals(method)) return handleCloseApps();
                    break;

                case "/screen/on":
                    if (Method.POST.equals(method)) return handleScreenOn();
                    break;

                case "/screen/off":
                    if (Method.POST.equals(method)) return handleScreenOff();
                    break;

                case "/brightness/set":
                    if (Method.POST.equals(method)) return handleBrightnessSet(session);
                    break;

                default:
                    return newFixedLengthResponse(Response.Status.NOT_FOUND,
                            MIME_PLAINTEXT, "404 Not Found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling " + uri, e);
            return jsonError("Errore interno: " + e.getMessage());
        }

        return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED,
                MIME_PLAINTEXT, "Method Not Allowed");
    }

    // =========================================================================
    // Handlers
    // =========================================================================

    /** GET /status */
    private Response handleStatus() {
        try {
            int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int volPct = (maxVol > 0) ? (curVol * 100 / maxVol) : 0;

            String ip = RemoteService.getLocalIpAddress();
            String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
            String androidVersion = "Android " + Build.VERSION.RELEASE
                    + " (API " + Build.VERSION.SDK_INT + ")";
            long uptimeSec = android.os.SystemClock.elapsedRealtime() / 1000;

            JSONObject json = new JSONObject();
            json.put("volume", volPct);
            json.put("ip", ip != null ? ip : "N/A");
            json.put("deviceName", deviceName);
            json.put("androidVersion", androidVersion);
            json.put("uptime", uptimeSec);

            return corsResponse(Response.Status.OK, "application/json", json.toString());
        } catch (Exception e) {
            return jsonError(e.getMessage());
        }
    }

    /** POST /volume/up */
    private Response handleVolumeUp() {
        audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI);
        return jsonOk("Volume aumentato");
    }

    /** POST /volume/down */
    private Response handleVolumeDown() {
        audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI);
        return jsonOk("Volume diminuito");
    }

    /** POST /volume/set?level=50 */
    private Response handleVolumeSet(IHTTPSession session) {
        String levelStr = getParam(session, "level");
        if (levelStr == null) return jsonError("Parametro 'level' mancante");
        try {
            int pct = Math.max(0, Math.min(100, Integer.parseInt(levelStr)));
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int target = (int) Math.round(pct / 100.0 * max);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
            return jsonOk("Volume impostato al " + pct + "%");
        } catch (NumberFormatException e) {
            return jsonError("Valore non valido: " + levelStr);
        }
    }

    /** POST /shutdown */
    private Response handleShutdown() {
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
            // Try shell command first (works on rooted / system-signed apps)
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot -p"});
                return;
            } catch (IOException ignored) {}
            try {
                Runtime.getRuntime().exec("reboot -p");
            } catch (IOException ignored) {}
            // Fallback: broadcast (works only with proper system permissions)
            Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
            intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }).start();
        return jsonOk("Spegnimento in corso…");
    }

    /** POST /reboot */
    private Response handleReboot() {
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"});
                return;
            } catch (IOException ignored) {}
            try {
                Runtime.getRuntime().exec("reboot");
            } catch (IOException ignored) {}
            powerManager.reboot(null);
        }).start();
        return jsonOk("Riavvio in corso…");
    }

    /** POST /close-apps */
    private Response handleCloseApps() {
        try {
            android.app.ActivityManager am =
                    (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                java.util.List<android.app.ActivityManager.RunningAppProcessInfo> procs =
                        am.getRunningAppProcesses();
                if (procs != null) {
                    String ownPackage = context.getPackageName();
                    int killed = 0;
                    for (android.app.ActivityManager.RunningAppProcessInfo proc : procs) {
                        if (proc.importance >
                                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE
                                && !proc.processName.equals(ownPackage)) {
                            am.killBackgroundProcesses(proc.processName);
                            killed++;
                        }
                    }
                    return jsonOk("Chiuse " + killed + " app in background");
                }
            }
            return jsonOk("Nessuna app da chiudere");
        } catch (Exception e) {
            return jsonError("Errore: " + e.getMessage());
        }
    }

    /** POST /screen/on */
    @SuppressWarnings("deprecation")
    private Response handleScreenOn() {
        try {
            PowerManager.WakeLock wl = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "helgi:screenwake");
            wl.acquire(3000); // release after 3 s, screen stays on via WindowManager
            return jsonOk("Schermo acceso");
        } catch (Exception e) {
            return jsonError("Errore: " + e.getMessage());
        }
    }

    /** POST /screen/off */
    @SuppressWarnings("deprecation")
    private Response handleScreenOff() {
        try {
            // Acquiring PARTIAL_WAKE_LOCK and then releasing lets the system
            // decide to turn off the display. Combined with a reboot of the
            // screen policy via goToSleep (API 20+).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                try { java.lang.reflect.Method m = powerManager.getClass().getMethod("goToSleep", long.class); m.invoke(powerManager, android.os.SystemClock.uptimeMillis()); } catch (Exception e) { /* not available */ }
            } else {
                PowerManager.WakeLock wl = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, "helgi:screenoff");
                wl.acquire(100);
                wl.release();
            }
            return jsonOk("Schermo spento");
        } catch (Exception e) {
            return jsonError("Errore (permessi insufficienti?): " + e.getMessage());
        }
    }

    /** POST /brightness/set?level=50 */
    private Response handleBrightnessSet(IHTTPSession session) {
        String levelStr = getParam(session, "level");
        if (levelStr == null) return jsonError("Parametro 'level' mancante");
        try {
            int pct = Math.max(0, Math.min(100, Integer.parseInt(levelStr)));
            int brightness = (int) Math.round(pct / 100.0 * 255);

            // Requires WRITE_SETTINGS (or system-level on Android 6+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.System.canWrite(context)) {
                    return jsonError("Permesso WRITE_SETTINGS non concesso. " +
                            "Vai in Impostazioni → App → Helgi Remote → " +
                            "Permessi speciali → Modifica impostazioni di sistema.");
                }
            }
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness);
            return jsonOk("Luminosità impostata al " + pct + "%");
        } catch (NumberFormatException e) {
            return jsonError("Valore non valido: " + levelStr);
        }
    }

    // =========================================================================
    // HTML Dashboard
    // =========================================================================

    private Response serveHtmlDashboard() {
        String html = "<!DOCTYPE html>\n" +
"<html lang=\"it\">\n" +
"<head>\n" +
"<meta charset=\"UTF-8\">\n" +
"<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
"<title>Helgi Remote</title>\n" +
"<style>\n" +
"  :root {\n" +
"    --bg: #0f0f14;\n" +
"    --surface: #1a1a24;\n" +
"    --surface2: #22222f;\n" +
"    --accent: #6c63ff;\n" +
"    --accent2: #ff6584;\n" +
"    --green: #43e97b;\n" +
"    --yellow: #f9ca24;\n" +
"    --red: #ff4757;\n" +
"    --text: #e8e8f0;\n" +
"    --muted: #8888a8;\n" +
"    --radius: 16px;\n" +
"  }\n" +
"  * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
"  body {\n" +
"    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
"    background: var(--bg);\n" +
"    color: var(--text);\n" +
"    min-height: 100vh;\n" +
"    padding: 0 0 80px;\n" +
"  }\n" +
"  header {\n" +
"    background: linear-gradient(135deg, #1a1a2e, #16213e);\n" +
"    padding: 24px 20px 20px;\n" +
"    border-bottom: 1px solid #2a2a3e;\n" +
"    position: sticky; top: 0; z-index: 100;\n" +
"  }\n" +
"  header h1 { font-size: 1.4rem; font-weight: 700; letter-spacing: -0.5px; }\n" +
"  header h1 span { color: var(--accent); }\n" +
"  .status-bar {\n" +
"    display: flex; flex-wrap: wrap; gap: 8px;\n" +
"    margin-top: 12px;\n" +
"  }\n" +
"  .status-chip {\n" +
"    background: var(--surface2);\n" +
"    border-radius: 20px;\n" +
"    padding: 4px 12px;\n" +
"    font-size: 0.75rem;\n" +
"    color: var(--muted);\n" +
"    display: flex; align-items: center; gap: 5px;\n" +
"  }\n" +
"  .status-chip .val { color: var(--text); font-weight: 600; }\n" +
"  .ip-badge {\n" +
"    background: var(--accent);\n" +
"    color: #fff;\n" +
"    border-radius: 20px;\n" +
"    padding: 4px 14px;\n" +
"    font-size: 0.8rem;\n" +
"    font-weight: 700;\n" +
"    letter-spacing: 0.3px;\n" +
"  }\n" +
"  .container { padding: 16px; max-width: 480px; margin: 0 auto; }\n" +
"  .section { margin-bottom: 20px; }\n" +
"  .section-title {\n" +
"    font-size: 0.7rem;\n" +
"    text-transform: uppercase;\n" +
"    letter-spacing: 1.5px;\n" +
"    color: var(--muted);\n" +
"    margin-bottom: 10px;\n" +
"    padding-left: 2px;\n" +
"  }\n" +
"  .card {\n" +
"    background: var(--surface);\n" +
"    border-radius: var(--radius);\n" +
"    padding: 16px;\n" +
"    border: 1px solid #2a2a3e;\n" +
"  }\n" +
"  .btn-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }\n" +
"  .btn-grid-3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; }\n" +
"  button {\n" +
"    background: var(--surface2);\n" +
"    border: 1px solid #2e2e42;\n" +
"    border-radius: 12px;\n" +
"    color: var(--text);\n" +
"    padding: 14px 10px;\n" +
"    font-size: 0.85rem;\n" +
"    font-weight: 600;\n" +
"    cursor: pointer;\n" +
"    display: flex; flex-direction: column; align-items: center; gap: 6px;\n" +
"    transition: all 0.15s ease;\n" +
"    width: 100%;\n" +
"    -webkit-tap-highlight-color: transparent;\n" +
"  }\n" +
"  button .icon { font-size: 1.4rem; line-height: 1; }\n" +
"  button:active { transform: scale(0.94); opacity: 0.85; }\n" +
"  button.accent { background: var(--accent); border-color: var(--accent); color: #fff; }\n" +
"  button.green  { background: var(--green);  border-color: var(--green);  color: #111; }\n" +
"  button.red    { background: var(--red);    border-color: var(--red);    color: #fff; }\n" +
"  button.yellow { background: var(--yellow); border-color: var(--yellow); color: #111; }\n" +
"  .slider-row {\n" +
"    display: flex; align-items: center; gap: 12px;\n" +
"    margin-top: 12px;\n" +
"  }\n" +
"  .slider-row label { font-size: 0.85rem; color: var(--muted); min-width: 90px; }\n" +
"  .slider-row .val-display {\n" +
"    min-width: 38px; text-align: right;\n" +
"    font-size: 0.85rem; font-weight: 700; color: var(--accent);\n" +
"  }\n" +
"  input[type=range] {\n" +
"    flex: 1; height: 6px;\n" +
"    -webkit-appearance: none;\n" +
"    background: #2e2e42; border-radius: 6px; outline: none;\n" +
"  }\n" +
"  input[type=range]::-webkit-slider-thumb {\n" +
"    -webkit-appearance: none;\n" +
"    width: 22px; height: 22px;\n" +
"    border-radius: 50%;\n" +
"    background: var(--accent);\n" +
"    cursor: pointer;\n" +
"    box-shadow: 0 2px 8px rgba(108,99,255,0.4);\n" +
"  }\n" +
"  .toast {\n" +
"    position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%) translateY(100px);\n" +
"    background: #333;\n" +
"    color: #fff;\n" +
"    padding: 12px 24px;\n" +
"    border-radius: 24px;\n" +
"    font-size: 0.85rem;\n" +
"    font-weight: 600;\n" +
"    z-index: 999;\n" +
"    transition: transform 0.3s cubic-bezier(0.34,1.56,0.64,1);\n" +
"    white-space: nowrap;\n" +
"    max-width: 90vw;\n" +
"    overflow: hidden; text-overflow: ellipsis;\n" +
"  }\n" +
"  .toast.show { transform: translateX(-50%) translateY(0); }\n" +
"  .divider { height: 1px; background: #2a2a3e; margin: 14px 0; }\n" +
"  @media (prefers-color-scheme: light) { body { background: #f0f0f8; } }\n" +
"</style>\n" +
"</head>\n" +
"<body>\n" +
"\n" +
"<header>\n" +
"  <h1>🖥️ Helgi <span>Remote</span></h1>\n" +
"  <div class=\"status-bar\" id=\"statusBar\">\n" +
"    <span class=\"status-chip\">Caricamento…</span>\n" +
"  </div>\n" +
"</header>\n" +
"\n" +
"<div class=\"container\">\n" +
"\n" +
"  <!-- VOLUME -->\n" +
"  <div class=\"section\">\n" +
"    <div class=\"section-title\">🔊 Volume</div>\n" +
"    <div class=\"card\">\n" +
"      <div class=\"btn-grid-3\">\n" +
"        <button onclick=\"post('/volume/down')\">\n" +
"          <span class=\"icon\">🔉</span>Meno\n" +
"        </button>\n" +
"        <button onclick=\"post('/volume/up')\">\n" +
"          <span class=\"icon\">🔊</span>Più\n" +
"        </button>\n" +
"        <button onclick=\"post('/volume/set?level=0')\">\n" +
"          <span class=\"icon\">🔇</span>Muto\n" +
"        </button>\n" +
"      </div>\n" +
"      <div class=\"slider-row\">\n" +
"        <label>Volume</label>\n" +
"        <input type=\"range\" id=\"volSlider\" min=\"0\" max=\"100\" value=\"50\"\n" +
"               oninput=\"document.getElementById('volVal').textContent=this.value+'%'\"\n" +
"               onchange=\"post('/volume/set?level='+this.value)\">\n" +
"        <span class=\"val-display\" id=\"volVal\">50%</span>\n" +
"      </div>\n" +
"    </div>\n" +
"  </div>\n" +
"\n" +
"  <!-- LUMINOSITÀ -->\n" +
"  <div class=\"section\">\n" +
"    <div class=\"section-title\">☀️ Luminosità</div>\n" +
"    <div class=\"card\">\n" +
"      <div class=\"slider-row\">\n" +
"        <label>Luminosità</label>\n" +
"        <input type=\"range\" id=\"brightSlider\" min=\"0\" max=\"100\" value=\"50\"\n" +
"               oninput=\"document.getElementById('brightVal').textContent=this.value+'%'\"\n" +
"               onchange=\"post('/brightness/set?level='+this.value)\">\n" +
"        <span class=\"val-display\" id=\"brightVal\">50%</span>\n" +
"      </div>\n" +
"    </div>\n" +
"  </div>\n" +
"\n" +
"  <!-- SCHERMO -->\n" +
"  <div class=\"section\">\n" +
"    <div class=\"section-title\">📱 Schermo</div>\n" +
"    <div class=\"card\">\n" +
"      <div class=\"btn-grid\">\n" +
"        <button class=\"green\" onclick=\"post('/screen/on')\">\n" +
"          <span class=\"icon\">💡</span>Accendi\n" +
"        </button>\n" +
"        <button onclick=\"post('/screen/off')\">\n" +
"          <span class=\"icon\">🌑</span>Spegni\n" +
"        </button>\n" +
"      </div>\n" +
"    </div>\n" +
"  </div>\n" +
"\n" +
"  <!-- APP -->\n" +
"  <div class=\"section\">\n" +
"    <div class=\"section-title\">📋 Applicazioni</div>\n" +
"    <div class=\"card\">\n" +
"      <button class=\"accent\" onclick=\"post('/close-apps')\">\n" +
"        <span class=\"icon\">🗑️</span>Chiudi tutte le app\n" +
"      </button>\n" +
"    </div>\n" +
"  </div>\n" +
"\n" +
"  <!-- SISTEMA -->\n" +
"  <div class=\"section\">\n" +
"    <div class=\"section-title\">⚙️ Sistema</div>\n" +
"    <div class=\"card\">\n" +
"      <div class=\"btn-grid\">\n" +
"        <button class=\"yellow\" onclick=\"confirmAction('Riavviare il dispositivo?', '/reboot')\">\n" +
"          <span class=\"icon\">🔄</span>Riavvia\n" +
"        </button>\n" +
"        <button class=\"red\" onclick=\"confirmAction('Spegnere il dispositivo?', '/shutdown')\">\n" +
"          <span class=\"icon\">⏻</span>Spegni\n" +
"        </button>\n" +
"      </div>\n" +
"    </div>\n" +
"  </div>\n" +
"\n" +
"</div>\n" +
"\n" +
"<div class=\"toast\" id=\"toast\"></div>\n" +
"\n" +
"<script>\n" +
"let toastTimer;\n" +
"\n" +
"function showToast(msg, ok) {\n" +
"  const t = document.getElementById('toast');\n" +
"  t.textContent = (ok ? '✅ ' : '❌ ') + msg;\n" +
"  t.style.background = ok ? '#1e7a3a' : '#7a1e1e';\n" +
"  t.classList.add('show');\n" +
"  clearTimeout(toastTimer);\n" +
"  toastTimer = setTimeout(() => t.classList.remove('show'), 2800);\n" +
"}\n" +
"\n" +
"async function post(path) {\n" +
"  try {\n" +
"    const r = await fetch(path, { method: 'POST' });\n" +
"    const j = await r.json();\n" +
"    showToast(j.message || JSON.stringify(j), r.ok);\n" +
"  } catch (e) { showToast('Errore di rete', false); }\n" +
"}\n" +
"\n" +
"function confirmAction(msg, path) {\n" +
"  if (confirm(msg)) post(path);\n" +
"}\n" +
"\n" +
"async function refreshStatus() {\n" +
"  try {\n" +
"    const r = await fetch('/status');\n" +
"    const d = await r.json();\n" +
"    const bar = document.getElementById('statusBar');\n" +
"    bar.innerHTML = `\n" +
"      <span class=\"ip-badge\">📡 ${d.ip}:8080</span>\n" +
"      <span class=\"status-chip\">🔊 <span class=\"val\">${d.volume}%</span></span>\n" +
"      <span class=\"status-chip\">📱 <span class=\"val\">${d.deviceName}</span></span>\n" +
"      <span class=\"status-chip\">🤖 <span class=\"val\">${d.androidVersion}</span></span>\n" +
"      <span class=\"status-chip\">⏱ <span class=\"val\">${formatUptime(d.uptime)}</span></span>\n" +
"    `;\n" +
"    // Sync volume slider\n" +
"    const vs = document.getElementById('volSlider');\n" +
"    if (vs && document.activeElement !== vs) {\n" +
"      vs.value = d.volume;\n" +
"      document.getElementById('volVal').textContent = d.volume + '%';\n" +
"    }\n" +
"  } catch(e) {}\n" +
"}\n" +
"\n" +
"function formatUptime(sec) {\n" +
"  const h = Math.floor(sec/3600), m = Math.floor((sec%3600)/60), s = sec%60;\n" +
"  return h>0 ? h+'h '+m+'m' : m>0 ? m+'m '+s+'s' : s+'s';\n" +
"}\n" +
"\n" +
"refreshStatus();\n" +
"setInterval(refreshStatus, 5000);\n" +
"</script>\n" +
"</body>\n" +
"</html>\n";

        Response resp = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
        resp.addHeader("Access-Control-Allow-Origin", "*");
        return resp;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String getParam(IHTTPSession session, String key) {
        Map<String, List<String>> params = session.getParameters();
        if (params.containsKey(key) && !params.get(key).isEmpty()) {
            return params.get(key).get(0);
        }
        return null;
    }

    private Response jsonOk(String message) {
        try {
            JSONObject j = new JSONObject();
            j.put("ok", true);
            j.put("message", message);
            return corsResponse(Response.Status.OK, "application/json", j.toString());
        } catch (Exception e) {
            return newFixedLengthResponse("{\"ok\":true}");
        }
    }

    private Response jsonError(String message) {
        try {
            JSONObject j = new JSONObject();
            j.put("ok", false);
            j.put("message", message);
            return corsResponse(Response.Status.INTERNAL_ERROR, "application/json", j.toString());
        } catch (Exception e) {
            return newFixedLengthResponse("{\"ok\":false}");
        }
    }

    private Response corsResponse(Response.Status status, String mimeType, String body) {
        Response resp = newFixedLengthResponse(status, mimeType, body);
        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type");
        return resp;
    }
}
