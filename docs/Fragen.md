# Fragen

## 1. Welche Teile des Programms muessen gegenueber parallelem Zugriff geschuetzt werden?

Geschuetzt werden muss zuerst der Zaehler fuer die nummerierten Zielordner der einzelnen `crawl`-Aufrufe. Da mehrere Threads gleichzeitig `crawl(uri)` aufrufen koennen, darf keine Ordnernummer doppelt vergeben werden.

Ebenfalls kritisch ist die Zustandsverfolgung der Arbeitspakete. Dazu gehoeren die Anzahl wartender und laufender Webseiten-Scans sowie die Anzahl wartender und laufender Bilddownloads. Diese Werte werden von mehreren Threads veraendert: vom aufrufenden Thread beim Einreichen neuer Arbeit und von den Worker-Threads beim Starten und Beenden der Aufgaben.

Auch der Idle-Zustand des Crawlers haengt von gemeinsam genutzten Daten ab. `isIdle()` darf nur dann `true` liefern, wenn wirklich keine Aufgabe mehr wartet oder laeuft. Deshalb muessen die zugrunde liegenden Zaehler sicher lesbar und aktualisierbar sein.

Beim Speichern der Bilder muss außerdem die Behandlung gleicher Dateinamen geschuetzt werden. Wenn zwei Downloads im selben Zielordner gleichzeitig ein Bild mit dem Namen `image.jpg` speichern wollen, muss eindeutig entschieden werden, welche Datei `image.jpg` und welche Datei `image_2.jpg` heißt. Dafuer verwendet der Crawler eine gemeinsame Map im `FileNameResolver`, die pro Zielordner bereits vergebene Namen zaehlt.

Weitere gemeinsam genutzte Strukturen sind die Executor-Queues. Diese werden nicht selbst implementiert, sondern durch `ExecutorService` bereitgestellt. Die Java-Bibliothek uebernimmt hier die thread-sichere Verwaltung der wartenden Aufgaben.

## 2. Wie stellen Sie den Schutz gegenueber dem parallelen Zugriff an den genannten Stellen sicher? Welche Alternativen haette es gegeben? Warum haben Sie sich fuer diese Variante des Schutzes entschieden?

Die Zaehler fuer Ordnernummern und Aufgabenstatus sind als `AtomicInteger` umgesetzt. Dadurch koennen Inkrementieren, Dekrementieren und Lesen ohne einen groesseren gemeinsamen Lock erfolgen. Fuer den Ordnerzaehler ist das besonders passend, weil nur eine fortlaufende Nummer benoetigt wird. Bei den Statuszaehlern ist wichtig, dass jeder Task beim Einreichen, Starten und Beenden sauber gezaehlt wird.

Die Parallelitaetsgrenzen werden ueber zwei feste `ExecutorService`-Threadpools sichergestellt. Ein Pool ist nur fuer Webseiten-Scans verantwortlich, der andere nur fuer Bilddownloads. Die maximale Threadanzahl entspricht jeweils dem Wert aus der Konfiguration. Wartende Aufgaben bleiben in der internen Queue des Executors, bis ein Worker frei wird.

Die Dateinamenvergabe im `FileNameResolver` ist mit einer `synchronized`-Methode geschuetzt. Der kritische Bereich ist klein: Es wird nur der naechste freie Name berechnet und in der Map reserviert. HTTP-Downloads und Dateischreiboperationen laufen nicht innerhalb dieser Synchronisierung. So wird verhindert, dass parallele Downloads unnoetig lange blockiert werden.

Als Alternative waeren komplett synchronisierte Methoden in `ImageCrawler` moeglich gewesen. Das waere einfacher zu lesen, wuerde aber zu viel Arbeit blockieren, weil dann auch unabhaengige Operationen aufeinander warten muessten. Eine weitere Alternative waere `ReentrantLock`, was mehr Kontrolle bietet, hier aber keinen echten Vorteil gegenueber `AtomicInteger` und einem kleinen `synchronized`-Block bringt.

Man koennte die Parallelitaet auch mit `Semaphore` begrenzen oder eigene Worker mit `BlockingQueue` implementieren. Fuer diese Aufgabe ist `ExecutorService` jedoch direkter und weniger fehleranfaellig, weil Einreihen, Worker-Verwaltung und Begrenzung der Threadanzahl bereits sauber geloest sind. Die gewaehlte Loesung vermeidet manuelles Thread-Handling und haelt die Synchronisierung auf die Stellen beschraenkt, an denen wirklich gemeinsamer Zustand veraendert wird.

Eine `ConcurrentHashMap` waere fuer die Dateinamenverwaltung ebenfalls denkbar. In dieser Implementierung ist die Logik fuer Basisname, Suffix und Zielordner aber zusammenhaengend. Eine kleine synchronisierte Methode ist dafuer uebersichtlicher und verhindert Zwischenzustaende leichter als mehrere einzelne atomare Map-Operationen.

## 3. Nennen Sie die Objekte und Datenstrukturen, die pro Thread zusaetzlich im Arbeitsspeicher Heap oder Stack vorgehalten werden muessen. JVM-interne Details zum erforderlichen Speicher fuer einen Thread muessen nicht behandelt werden.

Pro Worker-Thread entstehen zunaechst Stack Frames fuer die gerade ausgefuehrten Methoden, zum Beispiel fuer `runWebsiteTask`, `WebsiteAnalyzer.analyze`, `runImageTask` oder `ImageDownloader.download`. Dazu kommen lokale Variablen wie die aktuelle Webseiten-URI, die Bild-URI, das Zielverzeichnis und spaeter der konkrete Dateipfad.

Bei einem Webseiten-Scan werden außerdem lokale HTTP-Objekte benoetigt: der `HttpRequest`, die `HttpResponse<String>` und der HTML-Text. Jsoup erzeugt daraus ein `Document`, aus dem die `img`-Elemente gelesen werden. Die extrahierten Bildadressen werden als lokale Liste von `URI`-Objekten gehalten, bis `ImageCrawler` daraus Download-Aufgaben erzeugt.

Bei einem Bilddownload liegen pro laufendem Task der `HttpRequest`, die `HttpResponse<byte[]>` und die heruntergeladenen Bytes im Speicher. Außerdem werden lokale Variablen fuer den urspruenglichen Dateinamen, den aufgeloesten Zielpfad und gegebenenfalls den um ein Suffix erweiterten Dateinamen verwendet.

Fuer die asynchrone Ausfuehrung werden Task-Objekte beziehungsweise `Runnable`-Instanzen im Heap gehalten, solange sie in einer Executor-Queue warten oder gerade ausgefuehrt werden. Dazu gehoeren auch `ImageReference`-Objekte, die Bild-URI und Zielordner gemeinsam beschreiben.

Wenn Fehler auftreten, koennen temporaere Exception-Objekte entstehen. Diese werden in der Implementierung abgefangen, damit ein defekter Download oder eine fehlerhafte Webseite nicht den gesamten Crawler beendet.
