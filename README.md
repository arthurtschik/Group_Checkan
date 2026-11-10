# Anwesenheit – Android-App

Offline-App zum Abhaken der Anwesenheit von Kindern, mit eigener Liste für jeden Wochentag (Mo–Fr).
Alle Daten bleiben auf dem Gerät – kein Konto, keine Cloud, keine Internetverbindung nötig.

**Download:** die aktuelle `Anwesenheit.apk` unter [Releases](../../releases) herunterladen und auf dem
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
- Haptisches Feedback, heller und dunkler Modus

## Datenschutz & Sicherheit

- **Kein Internet:** Die App hat keine Internet-Berechtigung und kann keine Daten senden.
- **Verschlüsselte Daten:** Die Datendatei wird mit AES-256-GCM verschlüsselt. Der Schlüssel liegt im
  Android-Keystore (wo vorhanden im Sicherheitschip) und kann das Gerät nicht verlassen – eine kopierte
  Datei ist unlesbar. Für Nutzer ändert sich nichts (kein Passwort nötig).
- **Kein Cloud-Backup, keine Geräteübertragung:** Die Daten werden nicht in Google-Backups oder beim
  Gerätewechsel übernommen. Für den Umzug die eigene Datensicherung verwenden.
- **Datensicherung optional mit Passwort** (AES-256-GCM, Schlüssel per PBKDF2-SHA256 mit 310.000 Runden).
- **Alte Einträge automatisch löschen** (optional, je Profil): nach 1, 3, 6, 12 oder 24 Monaten.
- Text-Exporte sind bewusst unverschlüsselt – beim Weitergeben auf den Empfänger achten.

## Aufbau

| Pfad | Inhalt |
|---|---|
| `app/assets/index.html` | die komplette Oberfläche (HTML/CSS/JS) |
| `app/src/.../MainActivity.java` | Android-Hülle: WebView, Speichern, Teilen, Datei-Dialoge, Vibration |
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

Ergebnis: `Anwesenheit.apk` im Projektordner.

**Wichtig:** Der Signaturschlüssel (`release.p12`) gehört **nicht** ins Repository und ist per `.gitignore`
ausgeschlossen. Bewahre ihn sicher auf: Updates lassen sich nur mit demselben Schlüssel über die
installierte App installieren – mit einem anderen Schlüssel müsste die App neu installiert werden und die
Daten auf dem Gerät gingen verloren. Fehlt die Datei, erzeugt der Build einen neuen Schlüssel.

## Hinweis & Haftungsausschluss

Grupp-Checkan ist ein **privates Hobbyprojekt** und wird ohne jede Gewährleistung zur Verfügung gestellt.
Es wird **keine Haftung für Datenverluste** oder sonstige Schäden übernommen, die durch die Nutzung der App
entstehen.

Die App verarbeitet personenbezogene Daten von Kindern. Bitte nutze sie verantwortungsvoll: Gehe sorgsam
mit den Daten um, gib Exporte nur an berechtigte Personen weiter, kläre den Einsatz mit deinem Träger bzw.
deiner Einrichtung (Datenschutz/DSGVO), schütze dein Handy mit einer Displaysperre und erstelle regelmäßig
Datensicherungen. Die Verantwortung für den Umgang mit den Daten liegt bei den Nutzerinnen und Nutzern.

Release Notes der einzelnen Versionen: [`docs/releases/`](docs/releases/)
