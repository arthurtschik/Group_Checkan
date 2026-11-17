# Sicherheit

Group Checkan speichert Namen von Kindern. Dieses Dokument beschreibt, wie die App und die
Update-Kette geschützt sind, und welche Regeln für Entwicklung und Releases gelten.

## Sicherheitslücken melden

Bitte **nicht** öffentlich als GitHub-Issue, sondern per E-Mail an arthurtschik.business@gmail.com.

## Schutz der Daten auf dem Gerät

| Maßnahme | Umsetzung |
|---|---|
| Verschlüsselung | Datendatei mit AES-256-GCM, Schlüssel im Android-Keystore (nicht exportierbar). Es gibt **keinen** Rückfall auf unverschlüsseltes Speichern: Schlägt das Verschlüsseln fehl, bleibt der letzte Stand erhalten und die App warnt. |
| Lesefehler | Kann die Datendatei nicht gelesen oder entschlüsselt werden, wird sie unverändert beiseitegelegt (nie überschrieben) und die App erklärt, wie man eine Sicherung wiederherstellt. |
| Kein Backup / keine Geräteübertragung | `allowBackup=false` + `dataExtractionRules` (alles ausgeschlossen) |
| Keine Datenübertragung | Die Oberfläche (WebView) lädt nur die eigene Datei; jede andere Anfrage wird in Android blockiert **und** per Content-Security-Policy verboten (`connect-src 'none'`, `img-src data:` …) |
| Kein Inline-Code | Content-Security-Policy `script-src 'self'`: Programmcode kommt nur aus der mitgelieferten Datei `main.js`; eingeschleuste Skripte oder `on…`-Attribute werden nicht ausgeführt |
| WebView minimal | kein Dateizugriff, kein Content-Zugriff, kein DOM-Storage, keine Geolocation, keine Fenster, Debugging aus, Schutz gegen Überlagerung (Tapjacking) |
| Eingaben & Importe | Alle geladenen und importierten Daten werden Feld für Feld streng geprüft (Typ, Länge, Format, erlaubte Werte); unbekannte Felder werden verworfen. Alle Ausgaben werden HTML-escaped. |
| Sicherungsdateien | optional AES-256-GCM, Schlüssel per PBKDF2-SHA256 (600 000 Runden), Passwort mind. 10 Zeichen, einfache Muster werden abgelehnt; Parameter aus fremden Dateien werden begrenzt |
| Import-Größe | max. 30 MB pro Datei |

## Datenschutz in der App

- Automatische Update-Prüfung ist standardmäßig **aus** (nur nach ausdrücklicher Zustimmung).
- Option „Abwesenheit neutral erfassen“: „Entschuldigt“ statt „Krank“, damit keine Gesundheitsangaben entstehen.
- „Vollständig löschen“ entfernt ein Kind samt aller erfassten Anwesenheiten (Art. 17 DSGVO).
- Exporte mit „krank“-Einträgen zeigen einen Hinweis zur vertraulichen Weitergabe.

## Update-Kette

```
GitHub Release ──HTTPS──▶ App lädt APK ──▶ Prüfung in der App ──▶ Android-Installer ──▶ Prüfung durch Android
```

1. Abfrage nur von `api.github.com/repositories/1462092325/releases/latest` (feste ID des öffentlichen Repositorys `arthurtschik/Group_Checkan`),
   nur HTTPS, nur System-Zertifikate.
2. Es werden nur `.apk`-Dateien aus Releases von `github.com/arthurtschik/…` akzeptiert, max. 50 MB.
3. **Vor der Installation prüft die App selbst** (zweimal: nach dem Download und direkt vor dem Start des
   Installers):
   - Paketname muss `de.anwesenheit.app` sein (verhindert, dass eine *fremde* App untergeschoben wird),
   - Signaturzertifikat muss **genau** das Release-Zertifikat sein (SHA-256 fest im Code hinterlegt),
   - Versionsnummer muss höher sein als die installierte.
   Schlägt eine Prüfung fehl, wird die Datei gelöscht und das Update abgebrochen.
4. Android installiert zusätzlich nur Updates mit identischer Signatur.

**Ergebnis:** Selbst wer Zugriff auf das GitHub-Konto erlangt, kann über die Update-Funktion keine
fremde oder veränderte App installieren – solange der **Signaturschlüssel** geheim bleibt.

Release-Zertifikat (SHA-256):
`4C:C4:B3:A7:45:64:A3:E2:58:F8:53:82:4E:19:20:5F:1E:CF:7C:FA:F2:FD:AB:9D:23:6C:B0:FB:8D:95:8E:04`

## Build

- Build-Werkzeuge sind per SHA-256 festgelegt; bei Abweichung bricht der Build ab.
- Kein Standard-Passwort im Code: `KEYSTORE_PASS` muss gesetzt sein (wird per Umgebung übergeben,
  nicht über die Kommandozeile).
- Fehlt der Signaturschlüssel, bricht der Build ab – es wird **nie** automatisch ein neuer Schlüssel erzeugt.
- Nach dem Signieren wird die APK geprüft; stimmt das Zertifikat nicht mit dem Release-Zertifikat
  überein, bricht der Build ab.
- Der Build gibt die SHA-256-Prüfsumme der APK aus; sie steht in den Release Notes.
- Annotation-Processing ist abgeschaltet (`-proc:none`).

## Regeln für das Repository

- Der Signaturschlüssel (`*.p12`) gehört **niemals** ins Repository (`.gitignore`).
- Zwei-Faktor-Anmeldung für das GitHub-Konto und das zugehörige E-Mail-Konto.
- `main` gegen Force-Push und Löschen schützen; Releases/Tags möglichst unveränderlich machen.
- Vor jedem Release die Änderungen seit dem letzten Release prüfen.
- Zugriffe von Apps/Integrationen auf das Repository regelmäßig überprüfen und nicht benötigte entfernen.
