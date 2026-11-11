# Group Checkan – Android-App

Offline-App zum Abhaken der Anwesenheit von Kindern, mit eigener Liste für jeden Wochentag (Mo–Fr).
Alle Daten bleiben auf dem Gerät – kein Konto, keine Cloud. Internet wird nur für die optionale Update-Prüfung genutzt.

**Download:** die aktuelle `Group-Checkan.apk` unter [Releases](../../releases) herunterladen und auf dem
Android-Gerät öffnen (Android 7.0 oder neuer; einmalig „Installation aus unbekannten Quellen“ erlauben).

## Funktionen

- **Anwesenheit** – öffnet automatisch den heutigen Wochentag (am Wochenende den letzten Freitag)
  - Antippen = anwesend (mit Durchstreich-Animation), erneut antippen = zurücksetzen (mit „Rückgängig“)
  - ⌂ früher abgeholt (mit Uhrzeit), 🌡 krank
  - Zähler, Fortschrittsbalken, Filter nach Gruppe, Klasse oder „offen“, Sortierung nach Vor- oder Nachname
  - „Fertig“-Effekt mit Vibration, sobald alle Kinder erfasst sind
- **Kalender** – vergangene Tage ansehen, nachtragen und als Text-Tabelle exportieren; Monatsübersicht je Kind
- **Stammdaten** – Kinder mit Vorname, Nachname, Gruppe (optional) und Klasse je Wochentag
  - Reiter „Alle“ mit Suche, Wischen zum Löschen, Sortierung, Listen kopieren
  - Gruppen frei benennbar: Farbwörter färben die Gruppe, Tiere & Co. bekommen automatisch ein Emoji
  - Exporte (Tagesliste, Gesamtübersicht, Gesamtliste) und Datensicherung/Wiederherstellung (JSON)
- **Profile** – getrennte Datenbestände; Pille oben links: antippen = verwalten, nach unten/oben wischen = wechseln
- **Einstellungen** – nur allgemeine Einstellungen: App-Update direkt aus der App, Datensicherung (alle oder
  ausgewählte Profile), automatisches Löschen (für ausgewählte Profile), Profile, Rechtliches & Impressum
- Haptisches Feedback, heller und dunkler Modus

## Datenschutz & Sicherheit

- **Internet nur für Updates:** Die App verbindet sich ausschließlich mit GitHub, um nach Updates zu suchen
  und sie herunterzuladen – nur auf Knopfdruck oder wenn die automatische Suche eingeschaltet ist. Es werden
  dabei keine App-Daten übertragen. Die Oberfläche selbst kann keine Webseiten laden (alle Anfragen werden
  blockiert), unverschlüsselte Verbindungen sind verboten. Android installiert ein Update nur, wenn es mit
  demselben Schlüssel signiert ist wie die installierte App.
- **Verschlüsselte Daten:** Die Datendatei wird mit AES-256-GCM verschlüsselt. Der Schlüssel liegt im
  Android-Keystore (wo vorhanden im Sicherheitschip) und kann das Gerät nicht verlassen – eine kopierte
  Datei ist unlesbar. Für Nutzer ändert sich nichts (kein Passwort nötig).
- **Kein Cloud-Backup, keine Geräteübertragung:** Die Daten werden nicht in Google-Backups oder beim
  Gerätewechsel übernommen. Für den Umzug die eigene Datensicherung verwenden.
- **Datensicherung optional mit Passwort** (AES-256-GCM, Schlüssel per PBKDF2-SHA256 mit 310.000 Runden).
- **Alte Einträge automatisch löschen** (optional, für ausgewählte Profile): nach 1, 3, 6, 12 oder 24 Monaten.
- Text-Exporte sind bewusst unverschlüsselt – beim Weitergeben auf den Empfänger achten.

## Echtheit prüfen

Alle offiziellen Versionen sind mit demselben Schlüssel signiert. Fingerabdruck des Zertifikats:

```
SHA-256: 4C:C4:B3:A7:45:64:A3:E2:58:F8:53:82:4E:19:20:5F:1E:CF:7C:FA:F2:FD:AB:9D:23:6C:B0:FB:8D:95:8E:04
```

Prüfen lässt sich das mit `apksigner verify --print-certs Group-Checkan.apk` (Android SDK, Zeile
„certificate SHA-256 digest“) oder mit Tools wie *App Manager* direkt auf dem Handy. Stimmt der Wert nicht
überein, stammt die Datei nicht von hier – bitte nicht installieren. Updates aus der App heraus prüft
Android automatisch.

## Aufbau

| Pfad | Inhalt |
|---|---|
| `app/assets/index.html` | die komplette Oberfläche (HTML/CSS/JS) |
| `app/src/.../MainActivity.java` | Android-Hülle: WebView, verschlüsselte Speicherung, Teilen, Datei-Dialoge, Vibration, Updates |
| `app/src/.../ApkProvider.java` | übergibt ein heruntergeladenes Update an den Android-Installer |
| `build/build.py` | Build ohne Android SDK/Gradle: Manifest, Ressourcen, Icon, Dex, Signatur |
| `build/axml.py` | Encoder für binäres Android-XML und `resources.arsc` |

## Bauen

Benötigt: JDK 17+, Python 3, Node.js mit Playwright (für die Icons) und drei JARs in einem Ordner `$TOOLS`:

- `android.jar` (API 34)
- `dalvik-dx-16.0.1.jar` (Maven Central: `com.jakewharton.android.repackaged:dalvik-dx`)
- `apksig-2.3.0.jar` (Maven Central: `com.android.tools.build:apksig`)

```bash
TOOLS=/pfad/zu/tools KEYSTORE=/pfad/zu/release.p12 KEYSTORE_PASS=... python3 build/build.py
```

Ergebnis: `Group-Checkan.apk` im Projektordner.

**Wichtig:** Der Signaturschlüssel (`release.p12`) gehört **nicht** ins Repository und ist per `.gitignore`
ausgeschlossen. Bewahre ihn sicher auf: Updates lassen sich nur mit demselben Schlüssel über die
installierte App installieren – mit einem anderen Schlüssel müsste die App neu installiert werden und die
Daten auf dem Gerät gingen verloren. Fehlt die Datei, erzeugt der Build einen neuen Schlüssel.

## Hinweis & Haftungsausschluss

Group Checkan ist ein **privates Hobbyprojekt** und wird ohne jede Gewährleistung zur Verfügung gestellt.
Es wird **keine Haftung für Datenverluste** oder sonstige Schäden übernommen, die durch die Nutzung der App
entstehen.

Die App verarbeitet personenbezogene Daten von Kindern. Bitte nutze sie verantwortungsvoll: Gehe sorgsam
mit den Daten um, gib Exporte nur an berechtigte Personen weiter, kläre den Einsatz mit deinem Träger bzw.
deiner Einrichtung (Datenschutz/DSGVO), schütze dein Handy mit einer Displaysperre und erstelle regelmäßig
Datensicherungen. Die Verantwortung für den Umgang mit den Daten liegt bei den Nutzerinnen und Nutzern.
Die App ist ein persönliches Hilfsmittel und ersetzt keine vorgeschriebenen Anwesenheits-, Aufsichts- oder
Dokumentationsnachweise.

Release Notes der einzelnen Versionen: [`docs/releases/`](docs/releases/)

## Lizenz

Der Quellcode steht unter der [MIT-Lizenz](LICENSE).

## Impressum

Arthurtschik  
c/o Block Services  
Stuttgarter Str. 106  
70736 Fellbach

Anfragen: arthurtschik.business@gmail.com
