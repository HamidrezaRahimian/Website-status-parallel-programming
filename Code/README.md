# Image Crawler

Java-Maven-Projekt fuer den Programmentwurf im Modul Paralleles Programmieren.

## Voraussetzungen

- JDK 17 oder neuer. Auf diesem Rechner wird das IntelliJ-Bundle verwendet:
  `C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.4\jbr`
- Maven 3.8 oder neuer

Falls `mvn` in PowerShell nicht gefunden wird, kann auf diesem Rechner stattdessen der lokale Starter `.\mvnw.cmd` verwendet werden.

## Tests ausfuehren

```powershell
mvn clean test
```

## Projekt bauen

```powershell
mvn clean package
```

Das JAR wird unter `target\image-crawler-1.0.0.jar` erstellt.

## Programm starten

```powershell
.\run.cmd https://example.com downloads
```

Optional koennen die Parallelitaetswerte angegeben werden:

```powershell
.\run.cmd https://example.com downloads 2 4
```

Die Ausgabe zeigt die geladene Website, den Download-Ordner, die verwendete Parallelitaet und nach Abschluss eine Liste der heruntergeladenen Dateien. Pro gecrawlter Website wird ein nummerierter Unterordner erstellt, zum Beispiel `downloads\1`.

Der Maven-Projektordner ist `Code`. In IntelliJ kann dieser Ordner direkt als Maven-Projekt geoeffnet werden.
