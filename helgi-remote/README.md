# Helgi Board Remote Control

> Un server HTTP incorporato nell'Android della lavagna Helgi, controllabile da qualsiasi browser sul telefono nella stessa rete WiFi o P2P.

---

## 📦 Come ottenere l'APK tramite GitHub Actions

1. **Fai un fork** di questo repository sul tuo account GitHub (o caricalo come nuovo repo pubblico/privato).
2. Vai su **Actions** → seleziona il workflow **"Build Helgi Remote APK"**.
3. Clicca **"Run workflow"** → **"Run workflow"** (oppure aspetta un push su `main`).
4. A build completata, clicca sul workflow run → scorri fino ad **Artifacts** → scarica **`helgi-remote-debug.apk`**.

> [!NOTE]
> GitHub Actions compila automaticamente l'APK a ogni push sul branch `main`, senza necessità di Android Studio o Java installati localmente.

---

## 📲 Installare l'APK sulla lavagna Helgi

1. Copia il file `helgi-remote-debug.apk` sulla lavagna (via USB, condivisione file o download diretto).
2. Sulla lavagna, vai in **Impostazioni → Sicurezza → Origini sconosciute** e abilita l'installazione da origini sconosciute (oppure usa **"Installa app sconosciute"** per il file manager specifico).
3. Apri il file manager della lavagna, naviga fino all'APK e toccalo per installarlo.
4. Dopo l'installazione, **avvia l'app** una volta dalla schermata delle app (l'icona sparirà subito — è normale, non ha UI).
5. Vedrai una **notifica persistente** nella barra di stato: _"Helgi Remote — Attivo"_ con l'IP del dispositivo.

> [!IMPORTANT]
> Per il controllo della luminosità (Android 6+) devi concedere il permesso speciale:
> **Impostazioni → App → Helgi Remote → Permessi speciali → Modifica impostazioni di sistema → Consenti**

---

## 🌐 Trovare l'indirizzo IP della lavagna

Il modo più semplice è leggere la **notifica** di Helgi Remote nella barra di stato: mostra direttamente `http://<IP>:8080`.

Alternativamente:
- **Impostazioni → Wi-Fi → (rete connessa) → dettagli** → cerca "Indirizzo IP"
- Da terminale ADB: `adb shell ip addr show wlan0`

---

## 📱 Connettersi dal telefono

1. Assicurati che il telefono e la lavagna siano **sulla stessa rete WiFi** (o connessi in P2P / hotspot).
2. Apri il browser del telefono e naviga su:

   ```
   http://<IP-lavagna>:8080
   ```

   Esempio: `http://192.168.1.50:8080`

3. Si aprirà la **dashboard di controllo** con tema scuro e interfaccia in italiano.

---

## 🎛️ Controlli disponibili

| Endpoint | Metodo | Descrizione |
|---|---|---|
| `/` | GET | Dashboard HTML (interfaccia principale) |
| `/status` | GET | JSON con volume, IP, nome dispositivo, versione Android, uptime |
| `/volume/up` | POST | Aumenta il volume |
| `/volume/down` | POST | Diminuisce il volume |
| `/volume/set?level=50` | POST | Imposta volume al valore % (0–100) |
| `/brightness/set?level=50` | POST | Imposta luminosità al valore % (0–100) |
| `/screen/on` | POST | Accende lo schermo |
| `/screen/off` | POST | Spegne lo schermo |
| `/close-apps` | POST | Chiude le app in background |
| `/reboot` | POST | Riavvia il dispositivo |
| `/shutdown` | POST | Spegne il dispositivo |

---

## 🔧 Note tecniche

- **NanoHTTPD 2.3.1** — server HTTP embedded, nessuna dipendenza esterna a runtime.
- Il servizio è dichiarato `START_STICKY`: viene riavviato automaticamente dal sistema se viene terminato.
- `BootReceiver` avvia il servizio automaticamente ad ogni accensione della lavagna.
- Operazioni come spegnimento e riavvio potrebbero richiedere **privilegi di root** o che l'app sia firmata come app di sistema, a seconda del firmware della lavagna.
- La luminosità richiede il permesso **"Modifica impostazioni di sistema"** (concedibile manualmente nelle impostazioni di Android).

---

## 🗂️ Struttura del progetto

```
helgi-remote/
├── .github/workflows/build.yml       # GitHub Actions CI
├── app/
│   ├── build.gradle                  # Config modulo app + dipendenza NanoHTTPD
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/helgi/remote/
│       │   ├── MainActivity.java     # Launcher invisibile
│       │   ├── RemoteService.java    # Foreground service
│       │   ├── ControlServer.java    # Server HTTP + dashboard HTML
│       │   └── BootReceiver.java     # Avvio automatico al boot
│       └── res/
│           ├── drawable/ic_notification.xml
│           └── values/strings.xml
├── build.gradle                      # Root build
├── settings.gradle
├── gradlew / gradlew.bat
└── gradle/wrapper/
    ├── gradle-wrapper.jar
    └── gradle-wrapper.properties
```
