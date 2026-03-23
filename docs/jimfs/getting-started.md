# Getting Started

## Installation

### Maven

```xml
<dependency>
  <groupId>com.google.jimfs</groupId>
  <artifactId>jimfs</artifactId>
  <version>1.3.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.google.jimfs:jimfs:1.3.0'

// Fur Tests:
testImplementation 'com.google.jimfs:jimfs:1.3.0'
```

## Erstes Dateisystem erstellen

```java
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Dateisystem mit Unix-Pfaden erstellen
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

// Verzeichnis anlegen
Path foo = fs.getPath("/foo");
Files.createDirectory(foo);

// Datei schreiben
Path hello = foo.resolve("hello.txt");
Files.write(hello, List.of("hello world"), StandardCharsets.UTF_8);

// Datei lesen
List<String> lines = Files.readAllLines(hello, StandardCharsets.UTF_8);
System.out.println(lines); // [hello world]
```

## Plattform-Konfigurationen

Jimfs bietet drei vorkonfigurierte Plattform-Profile:

### Unix

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
// Root: /
// Separator: /
// Working Directory: /work
// Case-sensitive
```

### macOS (OS X)

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.osX());
// Root: /
// Separator: /
// Working Directory: /work
// Case-insensitive (ASCII)
// Unicode-Normalisierung (NFC Display, NFD Lookup)
```

### Windows

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.windows());
// Root: C:\
// Separator: \  (/ wird auch akzeptiert)
// Working Directory: C:\work
// Case-insensitive (ASCII)
```

### Automatische Plattform-Erkennung

```java
// Wahlt automatisch basierend auf dem aktuellen Betriebssystem
FileSystem fs = Jimfs.newFileSystem();
```

## Benanntes Dateisystem

Jedes Dateisystem bekommt eine URI. Standardmassig wird eine UUID generiert, aber du kannst einen Namen vergeben:

```java
FileSystem fs = Jimfs.newFileSystem("mein-fs", Configuration.unix());
// URI: jimfs://mein-fs
// Pfad-URI: jimfs://mein-fs/foo/bar
```

## Dateisystem schliessen

Wie bei jedem `FileSystem` sollte es nach Gebrauch geschlossen werden:

```java
try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
    Path path = fs.getPath("/test.txt");
    Files.writeString(path, "test");
    // ...
} // Dateisystem wird automatisch geschlossen
```

## Nachste Schritte

- [Konfiguration](configuration.md) -- Dateisystem-Verhalten anpassen
- [Testing Guide](testing.md) -- Best Practices fur Unit-Tests
- [Beispiele](examples.md) -- Weitere praxisnahe Beispiele
