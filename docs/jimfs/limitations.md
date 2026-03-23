# Limitierungen und bekannte Einschrankungen

Jimfs implementiert den Grossteil der `java.nio.file` API, hat aber einige wichtige Einschrankungen, die bei der Verwendung beachtet werden mussen.

## `Path.toFile()` -- die wichtigste Einschrankung

**`JimfsPath.toFile()` wirft eine `UnsupportedOperationException`.**

Die `toFile()`-Methode konvertiert einen NIO-`Path` zu einem Legacy-`java.io.File`. Da `java.io.File` intern fest an das Default-Dateisystem gebunden ist (privates statisches Feld), kann es nicht mit einem In-Memory-Dateisystem arbeiten.

### Konsequenzen

- Code, der `Path.toFile()` aufruft, funktioniert nicht mit Jimfs
- Bibliotheken, die intern `java.io.File` verwenden (z.B. Apache Commons Compress), sind inkompatibel
- `new FileInputStream(path.toFile())` schlagt fehl

### Workaround

```java
// FALSCH -- wirft UnsupportedOperationException
File file = path.toFile();
FileInputStream fis = new FileInputStream(file);

// RICHTIG -- NIO-API verwenden
InputStream is = Files.newInputStream(path);
OutputStream os = Files.newOutputStream(path);
BufferedReader reader = Files.newBufferedReader(path);
BufferedWriter writer = Files.newBufferedWriter(path);
```

### Code testbar machen

```java
// VORHER: Nicht testbar mit Jimfs
public void processFile(String filePath) {
    File file = new File(filePath);          // java.io.File -- nicht mockbar
    try (FileReader reader = new FileReader(file)) {
        // ...
    }
}

// NACHHER: Testbar mit Jimfs
public void processFile(Path path) {
    try (BufferedReader reader = Files.newBufferedReader(path)) {
        // ...
    }
}
```

## Nicht als Default-FileSystem verwendbar

Jimfs kann nicht als Standard-Dateisystem der JVM gesetzt werden (`-Djava.nio.file.spi.DefaultFileSystemProvider`). Die Klasse `JimfsFileSystemProvider` ist `final` und hat nicht den erforderlichen Konstruktor, der einen `FileSystemProvider` akzeptiert.

Das bedeutet:
- `Path.of("/foo")` und `Paths.get("/foo")` verwenden immer das echte Dateisystem
- Pfade mussen immer uber `fs.getPath()` erstellt werden

```java
// Funktioniert NICHT -- verwendet das echte Dateisystem
Path path = Path.of("/foo/bar.txt");

// RICHTIG -- verwendet das Jimfs-Dateisystem
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
Path path = fs.getPath("/foo/bar.txt");
```

## ClassLoader-Probleme

In Umgebungen mit komplexen ClassLoader-Hierarchien (z.B. JBoss, WildFly, OSGi) kann es zu Problemen kommen, wenn `SystemJimfsFileSystemProvider` von einem anderen ClassLoader geladen wird als der Anwendungscode.

**Symptom:** `ProviderNotFoundException` oder `ServiceConfigurationError` beim Erstellen eines Dateisystems.

**Auswirkung:** URI-basierte Methoden wie `Paths.get(URI)` funktionieren moglicherweise nicht. Das Erstellen und Verwenden von Dateisystemen uber `Jimfs.newFileSystem()` funktioniert aber weiterhin.

## WatchService-Prazision

Der `PollingWatchService` vergleicht Datei-Timestamps mit Millisekunden-Prazision:
- Anderungen, die innerhalb derselben Millisekunde stattfinden, werden moglicherweise **nicht erkannt**
- Das Polling-Intervall (Standard: 5 Sekunden) verursacht eine Verzogerung bei der Event-Erkennung
- In Tests: kurzes Polling-Intervall setzen und ausreichend Wartezeit einplanen

```java
// Fur Tests: kurzes Polling-Intervall
Configuration config = Configuration.unix().toBuilder()
    .setWatchServiceConfiguration(
        WatchServiceConfiguration.polling(50, TimeUnit.MILLISECONDS)
    )
    .build();
```

## Dateiattribute sind deklarativ

POSIX-Berechtigungen, DOS-Attribute und andere Dateiattribute werden zwar korrekt **gespeichert und gelesen**, beeinflussen aber **nicht das tatsachliche Verhalten** des Dateisystems.

```java
// Berechtigungen setzen
Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("---------"));

// Datei ist trotzdem lesbar!
String content = Files.readString(path); // Funktioniert, obwohl keine Rechte gesetzt sind
```

## Plattform-Verhalten ist angenahert

Jimfs modelliert Dateisystem-Verhalten **nach dem UNIX-Modell**. Windows- und macOS-Konfigurationen simulieren Pfad-Format und Case-Sensitivity, aber:

- Nicht alle plattformspezifischen Verhaltensweisen sind nachgebildet
- Windows-spezifische Features wie relative Drive-Letter-Pfade (`C:foo`) und rootlose absolute Pfade (`\foo`) werden nicht unterstutzt
- UNC-Pfade (`\\host\share`) werden unterstutzt

## Inkompatible Bibliotheken

Einige Java-Bibliotheken sind nicht mit Jimfs kompatibel, da sie intern `java.io.File` verwenden:

| Bibliothek | Problem |
|-----------|---------|
| Apache Commons IO (teilweise) | Verwendet `File` in einigen Methoden |
| Apache Commons Compress | Basiert auf `java.io.File` |
| AssertJ Path-Assertions (altere Versionen) | Rief intern `toFile()` auf (behoben seit neueren Versionen) |
| Bibliotheken mit `FileInputStream`/`FileOutputStream` | Funktionieren nur mit dem Default-Dateisystem |

### Faustregel

> Wenn eine Bibliothek `java.nio.file.Path` und `java.nio.file.Files` verwendet, ist sie mit Jimfs kompatibel.
> Wenn sie auf `java.io.File`, `FileInputStream` oder `FileOutputStream` basiert, ist sie es nicht.

## Speicher

- Jimfs speichert alles im Java Heap -- grosse Dateisysteme konnen zu `OutOfMemoryError` fuhren
- Die konfigurierte `maxSize` bezieht sich nur auf den Datei-Inhalt, nicht auf Metadaten (Verzeichnisse, Attribute, Pfad-Objekte)
- Es gibt keine Persistenz -- beim Schliessen des Dateisystems gehen alle Daten verloren
