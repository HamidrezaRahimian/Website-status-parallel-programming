# Checkliste zur Pruefungsaufgabe

Diese Checkliste basiert auf der Aufgabenstellung "Programmentwurf fuer Parallel Programming".

## 1. Organisatorische Abgabe

- [x] Abgabeformat ist ein ZIP-Archiv.
- [x] ZIP-Archiv soll nach dem eigenen Namen benannt werden.
- [x] ZIP-Archiv soll genau drei Eintraege enthalten: `Entwurf.pdf`, `Code`, `Fragen.pdf`.
- [x] `Entwurf.pdf` liegt im Projektwurzelordner.
- [x] `Fragen.pdf` liegt im Projektwurzelordner.
- [x] `Code` liegt als eigener Ordner vor.
- [x] `Code` enthaelt keine kompilierten `.class`-Dateien.
- [x] `Code` enthaelt keine gebauten `.jar`, `.war` oder `.ear`-Dateien.
- [x] `Code` enthaelt keinen `target`-Ordner.
- [x] `Code` enthaelt keine lokale Maven-Installation oder temporaeren Tool-Ordner.

## 2. Grundfunktion des Programms

- [x] Es gibt einen Webcrawler, der Webseiten nach Bildern durchsucht.
- [x] Gefundene Bilder werden heruntergeladen.
- [x] Der Crawler verarbeitet Standard-HTML mit `<img ...>`-Tags.
- [x] JavaScript-Dynamik wird nicht beruecksichtigt, wie in der Aufgabe erlaubt.
- [x] HTML-Seiten werden nur analysiert und nicht dauerhaft gespeichert.
- [x] Links auf andere HTML-Seiten werden nicht verfolgt.

## 3. Interfaces und Konfiguration

- [x] `IImageCrawlerConfig` existiert.
- [x] `IImageCrawlerConfig#getNumberOfAllowedParallelWebsiteScans()` existiert.
- [x] `IImageCrawlerConfig#getNumberOfAllowedParallelImageDownloads()` existiert.
- [x] `IImageCrawlerConfig#getDownloadPath()` existiert.
- [x] `IImageCrawler` existiert.
- [x] `IImageCrawler#crawl(URI uri)` existiert.
- [x] `IImageCrawler#isIdle()` existiert.
- [x] `ImageCrawler` implementiert `IImageCrawler`.
- [x] `ImageCrawler` erhaelt im Konstruktor ein `IImageCrawlerConfig`.
- [x] Ungueltige Parallelitaetswerte kleiner als `1` werden abgelehnt.
- [x] Ein fehlender Download-Pfad wird abgelehnt.

## 4. Verhalten von `crawl(URI uri)`

- [x] Jeder Aufruf fuegt eine neue Webseite zur Verarbeitung hinzu.
- [x] Der Methodenaufruf ist thread-safe.
- [x] Mehrere gleichzeitige `crawl`-Aufrufe vergeben keine doppelten Zielordner.
- [x] Die maximale Anzahl paralleler Webseiten-Scans wird eingehalten.
- [x] Wenn alle Scan-Plaetze belegt sind, warten weitere Webseiten-Scans in der Executor-Queue.
- [x] `crawl` blockiert nicht auf den vollstaendigen Abschluss aller Downloads.
- [x] `null` als URI wird abgelehnt.

## 5. Verhalten von `isIdle()`

- [x] Der Methodenaufruf ist thread-safe.
- [x] `isIdle()` ist `false`, solange Webseiten-Scans warten.
- [x] `isIdle()` ist `false`, solange Webseiten-Scans laufen.
- [x] `isIdle()` ist `false`, solange Bilddownloads warten.
- [x] `isIdle()` ist `false`, solange Bilddownloads laufen.
- [x] `isIdle()` ist erst `true`, wenn keine Website- oder Bildarbeit mehr offen ist.

## 6. Parallelisierung

- [x] Webseitenanalyse und Bilddownloads sind getrennt parallelisiert.
- [x] Webseitenanalyse nutzt einen eigenen festen Threadpool.
- [x] Bilddownloads nutzen einen eigenen festen Threadpool.
- [x] Die Website-Parallelitaet kommt aus `getNumberOfAllowedParallelWebsiteScans()`.
- [x] Die Download-Parallelitaet kommt aus `getNumberOfAllowedParallelImageDownloads()`.
- [x] Webseitenanalyse kann weiterlaufen, waehrend Bilddownloads laufen.
- [x] Bilddownloads koennen weiterlaufen, waehrend weitere Webseiten analysiert werden.
- [x] Es gibt keine unnoetige globale Synchronisierung um die eigentliche HTTP-Arbeit.

## 7. Modularisierung

- [x] `ImageCrawler` koordiniert den Programmablauf.
- [x] `WebsiteAnalyzer` laedt HTML-Seiten.
- [x] `WebsiteAnalyzer` extrahiert Bild-URLs.
- [x] `WebsiteAnalyzer` laedt keine Bilddateien herunter.
- [x] `ImageDownloader` laedt Bilddateien herunter.
- [x] `ImageDownloader` speichert Bilddateien auf der Festplatte.
- [x] Zusaetzliche Hilfsklassen sind sinnvoll begrenzt.
- [x] `FileNameResolver` kapselt Dateinamen- und Konfliktlogik.
- [x] `ImageReference` kapselt Daten fuer einen Bilddownload.

## 8. Bild- und Dateiverhalten

- [x] Bilder werden aus `<img src="...">` erkannt.
- [x] Relative Bildpfade werden gegen die Webseiten-URI aufgeloest.
- [x] HTTP- und HTTPS-Bild-URIs werden unterstuetzt.
- [x] Ungueltige Bildlinks werden ignoriert.
- [x] Nicht unterstuetzte Bildschemas wie `data:` oder `ftp:` werden ignoriert.
- [x] Bilder werden unter dem konfigurierten Download-Pfad gespeichert.
- [x] Pro uebergebener Webseiten-URL wird ein eigener Unterordner erstellt.
- [x] Unterordner werden aufsteigend ab `1` nummeriert.
- [x] Die Nummerierung entspricht der Reihenfolge der `crawl`-Aufrufe.
- [x] Der Original-Dateiname bleibt erhalten, soweit ein brauchbarer Name vorhanden ist.
- [x] Query-Strings werden nicht Teil des Dateinamens.
- [x] Fehlende Dateinamen erhalten einen einfachen Fallback-Namen.
- [x] Namenskonflikte werden mit Suffixen wie `_2` vor der Dateiendung geloest.
- [x] Doppelte Namen bleiben auch bei parallelen Downloads eindeutig.

## 9. Fehlerbehandlung

- [x] Fehlerhafte Webseiten beenden nicht den gesamten Crawler.
- [x] Fehlerhafte Bilddownloads beenden nicht den gesamten Crawler.
- [x] HTTP-Fehlerstatus bei Webseiten ergeben eine leere Bildliste.
- [x] HTTP-Fehlerstatus bei Bildern werden ignoriert.
- [x] Unterbrochene Threads setzen das Interrupt-Flag erneut.
- [x] Einzelne ungueltige URIs verhindern nicht die Verarbeitung anderer Aufgaben.

## 10. Thread-Safety

- [x] Ordnernummern werden atomar vergeben.
- [x] Aufgabenstatus wird mit thread-sicheren Zaehlern verfolgt.
- [x] Dateinamenreservierung ist synchronisiert.
- [x] Der synchronisierte Bereich ist klein und umfasst keine HTTP-Downloads.
- [x] Executor-Queues uebernehmen das thread-sichere Einreihen wartender Aufgaben.
- [x] Tests pruefen gleichzeitige `crawl`-Aufrufe.
- [x] Tests pruefen Website-Parallelitaetsgrenzen.
- [x] Tests pruefen Download-Parallelitaetsgrenzen.
- [x] Tests pruefen Ueberlappung von Website-Analyse und Bilddownload.

## 11. Java, Maven und Dependencies

- [x] Das Projekt ist in Java implementiert.
- [x] Maven wird als Build-System verwendet.
- [x] `pom.xml` liegt im `Code`-Ordner.
- [x] Java-Version ist ueber Maven auf Release 17 gesetzt.
- [x] Jsoup ist als Dependency in `pom.xml` enthalten.
- [x] JUnit 5 ist als Test-Dependency in `pom.xml` enthalten.
- [x] Maven Surefire ist fuer Tests konfiguriert.
- [x] Maven Javadoc Plugin ist konfiguriert.
- [x] Build und Tests laufen erfolgreich.
- [x] Javadoc-Erzeugung laeuft erfolgreich.

## 12. Tests

- [x] JUnit-5-Tests existieren.
- [x] Tests pruefen erfolgreiche Downloads.
- [x] Tests pruefen relative Bild-URLs.
- [x] Tests pruefen nummerierte Zielordner.
- [x] Tests pruefen doppelte Dateinamen.
- [x] Tests pruefen stabile Reihenfolge bei doppelten Namen.
- [x] Tests pruefen Dateinamen mit Query-Strings und fehlenden Namen.
- [x] Tests pruefen parallele Website-Scan-Grenze.
- [x] Tests pruefen parallele Download-Grenze.
- [x] Tests pruefen paralleles Ueberlappen der Arbeitsstufen.
- [x] Tests pruefen `isIdle()`.
- [x] Tests pruefen thread-safe `crawl`-Aufrufe.
- [x] Tests pruefen ungueltige Bild-URLs.
- [x] Tests pruefen fehlerhafte Webseiten.

## 13. Dokumentation

- [x] `docs/Entwurf.md` beschreibt Ziel und Architektur.
- [x] `docs/Entwurf.md` beschreibt Modularisierung und Datenfluss.
- [x] `docs/Entwurf.md` beschreibt parallelisierte Bereiche.
- [x] `docs/Entwurf.md` benennt Algorithmic Strategy Patterns.
- [x] `docs/Entwurf.md` benennt Implementation Strategy Patterns.
- [x] `Entwurf.pdf` wurde daraus erstellt.
- [x] `docs/Fragen.md` beantwortet Frage 1.
- [x] `docs/Fragen.md` beantwortet Frage 2.
- [x] `docs/Fragen.md` beantwortet Frage 3.
- [x] `Fragen.pdf` wurde daraus erstellt.
- [x] Die Zuordnung der Antworten zu den Fragen ist durch Nummerierung klar.

## 14. Code-Qualitaet

- [x] Klassennamen sind aussagekraeftig.
- [x] Methodennamen sind aussagekraeftig.
- [x] Variablennamen sind nachvollziehbar.
- [x] Zustaendigkeiten sind getrennt.
- [x] Die Loesung nutzt keine unnoetig komplexen eigenen Worker-Queues.
- [x] Die Loesung nutzt Java-Standardmittel fuer Parallelitaet.
- [x] Synchronisierung ist auf gemeinsam genutzten Zustand beschraenkt.
- [x] Produktionsmethoden sind mit JavaDoc dokumentiert.
- [x] Der Code kompiliert ohne Testfehler.
- [x] Die Loesung bleibt auf den Aufgabenumfang fokussiert.

## 15. Letzte technische Pruefung

- [x] `.\mvnw.cmd clean test javadoc:javadoc package` wurde erfolgreich ausgefuehrt.
- [x] CLI-Smoke-Test wurde erfolgreich ausgefuehrt.
- [x] Danach wurde `.\mvnw.cmd clean` ausgefuehrt.
- [x] Nach dem Clean liegen keine kompilierten Binaries in `Code`.
- [x] Nach dem Clean liegt kein `target`-Ordner in `Code`.

