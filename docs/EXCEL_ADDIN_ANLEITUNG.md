# FinDatEx Validator für Excel – Anleitung

Das Add-in prüft eine TPT-, EET-, EMT- oder EPT-Datei direkt in Excel und
färbt die Zellen ein, in denen etwas nicht stimmt. Die Prüfregeln sind
dieselben wie auf <https://www.findatex-validator.eu> und in der Desktop-App.

> **Status: Prototyp.** Funktionsumfang und Bedienung können sich noch ändern.

## Voraussetzungen

- **Excel im Browser** (Microsoft 365) oder **Excel für Microsoft 365** unter
  Windows oder Mac. Excel 2016/2019 als Kaufversion unter Windows und Excel
  2013 oder älter werden nicht unterstützt.
- Keine Installation und keine Admin-Rechte nötig. Manche Firmen sperren
  allerdings selbst hochgeladene Add-ins; dann fehlt der Menüpunkt
  „Mein Add-in hochladen“ (siehe *Probleme*).
- Internetzugang zu `www.findatex-validator.eu`.

## Datenschutz

Zur Prüfung wird die Arbeitsmappe an den Server `findatex-validator.eu`
geschickt – genau wie beim Hochladen auf der Webseite. Die Datei wird nach der
Prüfung gelöscht und nicht gespeichert. Dateien, die das Haus nicht verlassen
dürfen, besser mit der Desktop-App prüfen (dort bleibt alles auf dem eigenen
Rechner).

## Einmalig: Add-in laden

1. Die Datei **`manifest.xml`** speichern – sie hängt an dieser Anleitung oder
   liegt unter <https://www.findatex-validator.eu/addin/manifest.xml>.
2. In Excel eine beliebige Arbeitsmappe öffnen.
3. **Start → Add-ins → Weitere Add-ins** (bzw. *Weitere Einstellungen*)
   → Reiter **Meine Add-ins** → **Mein Add-in hochladen**.
4. `manifest.xml` auswählen und **Hochladen** klicken.
5. Auf der Registerkarte **Start** erscheint rechts die Gruppe **FinDatEx**
   mit der Schaltfläche **Validate file**.

In Excel im Browser bleibt das Add-in nach dem Hochladen für das eigene Konto
verfügbar. In Excel unter Windows muss es unter Umständen nach einem Neustart
erneut über *Meine Add-ins* geöffnet werden.

### Alternative unter Windows (ohne „Mein Add-in hochladen“)

1. `manifest.xml` in einen freigegebenen Netzwerkordner legen,
   z. B. `\\server\freigabe\excel-addins`.
2. **Datei → Optionen → Trust Center → Einstellungen für das Trust Center
   → Vertrauenswürdige Add-In-Kataloge**.
3. Den UNC-Pfad des Ordners unter *Katalog-URL* eintragen, **Katalog
   hinzufügen**, Häkchen bei **Im Menü anzeigen**, mit OK bestätigen.
4. Excel neu starten, dann **Start → Add-ins → Weitere Add-ins →
   Freigegebener Ordner** → *FinDatEx Validator* → **Hinzufügen**.

## Datei prüfen

1. Die zu prüfende Datei in Excel öffnen. **Geprüft wird immer das erste
   Tabellenblatt** – das Add-in zeigt oben an, welches das ist.
2. **Start → FinDatEx → Validate file** öffnet den Seitenbereich rechts.
3. **Template** (TPT, EET, EMT, EPT) und **Version** wählen. Vorbelegt ist die
   neueste Version.
4. Optional **Profile** ankreuzen (z. B. Solvency II, NW675). Ohne Auswahl
   gelten die Standardprofile.
5. **Validate** klicken. Je nach Dateigröße dauert das einige Sekunden.

## Ergebnis lesen

- Oben stehen Zeilenzahl, **Qualitäts-Score** und die Zahl der Fehler,
  Warnungen und Hinweise.
- **Farbige Zellen** im ersten Blatt zeigen, wo etwas gefunden wurde – jeweils
  nach dem schwersten Befund der Zelle:

  | Farbe | Bedeutung |
  |---|---|
  | Rot | Fehler (z. B. Pflichtfeld leer, ungültiger Code) |
  | Gelb | Warnung |
  | Blau | Hinweis |

  Befunde, die eine ganze Zeile betreffen, sind an der Zelle in Spalte A
  markiert.
- Darunter steht die **Liste aller Befunde** mit Zelladresse, Regel-Nummer,
  Feld und Meldung. **Ein Klick auf einen Befund springt zur Zelle.**
  Mit den Schaltflächen *All / Error / Warning / Info* lässt sich die Liste
  filtern.
- Befunde ohne Zellbezug (sie betreffen die Datei oder einen Fonds als Ganzes)
  sind mit „file“ gekennzeichnet.
- Die Bedeutung einer Regel steht in der Regel-Referenz:
  <https://www.findatex-validator.eu/rules>.

## Korrigieren und erneut prüfen

1. Werte in den markierten Zellen korrigieren.
2. Erneut **Validate** klicken. Die Markierungen des vorigen Laufs werden
   vorher automatisch entfernt.

## Markierungen entfernen

- **Remove markers** stellt die ursprünglichen Zellfarben wieder her.
- **Wichtig:** Das funktioniert nur, solange der Seitenbereich seit der
  Prüfung offen ist. Die Farben werden sonst **mit der Datei gespeichert**.
  Vor dem Speichern bzw. Versenden der Datei also *Remove markers* klicken.
  Wer ganz sicher gehen will, prüft eine Kopie der Datei.

## Grenzen

- Nur das erste Tabellenblatt wird geprüft.
- Keine externe Prüfung von ISIN/LEI (OpenFIGI/GLEIF) – dafür die Webseite
  oder die Desktop-App verwenden.
- Bei sehr großen Dateien (über 20.000 Zeilen) werden keine Zellen gefärbt;
  die Befundliste ist trotzdem vollständig.
- Die Prüfungen sind begrenzt (derzeit 30 pro Stunde und Netzwerk).

## Probleme

| Problem | Lösung |
|---|---|
| „Mein Add-in hochladen“ fehlt | Die Firma hat das Hochladen gesperrt. Unter Windows die Variante mit freigegebenem Ordner probieren, sonst die IT um Freigabe bitten. |
| Seitenbereich bleibt leer | Excel neu laden. In Excel im Browser zeigt die Entwicklerkonsole (F12) blockierte Adressen – bitte melden. |
| „Rate limit reached“ | Prüflimit erreicht; nach der angegebenen Zeit erneut versuchen. |
| „The workbook is larger than the 25 MB upload limit“ | Datei verkleinern (z. B. andere Blätter entfernen) oder als CSV auf der Webseite prüfen. |
| Falsches Blatt geprüft | Das Datenblatt in Excel an die erste Position verschieben und erneut prüfen. |
| Fehler „unknown template“ / keine Spalten erkannt | Stimmt die Template-Auswahl? Steht die Kopfzeile mit den FinDatEx-Feldnamen in den ersten 50 Zeilen? |

Rückmeldungen und Fehler bitte an das Projekt:
<https://github.com/karlkauc/findatex-validator/issues>.
