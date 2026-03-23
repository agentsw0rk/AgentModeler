# API-Referenz

## Offentliche Klassen

Jimfs exponiert nur wenige Klassen als offentliche API. Die eigentliche Arbeit wird uber die Standard-`java.nio.file`-APIs erledigt.

---

## `Jimfs`

Statische Factory-Klasse zum Erstellen von Dateisystemen.

```java
package com.google.common.jimfs;
```

### Methoden

| Methode | Beschreibung |
|---------|-------------|
| `newFileSystem()` | Erstellt ein Dateisystem mit plattform-spezifischer Konfiguration |
| `newFileSystem(String name)` | Wie oben, mit benannter URI (`jimfs://<name>`) |
| `newFileSystem(Configuration config)` | Erstellt ein Dateisystem mit gegebener Konfiguration |
| `newFileSystem(String name, Configuration config)` | Kombination aus Name und Konfiguration |

### Konstanten

| Konstante | Wert | Beschreibung |
|-----------|------|-------------|
| `URI_SCHEME` | `"jimfs"` | Das URI-Schema fur Jimfs-Dateisysteme |

---

## `Configuration`

Immutable Konfiguration fur ein Jimfs-Dateisystem.

### Statische Methoden

| Methode | Beschreibung |
|---------|-------------|
| `unix()` | Unix-Konfiguration (`/`, case-sensitive) |
| `osX()` | macOS-Konfiguration (`/`, case-insensitive, Unicode-Normalisierung) |
| `windows()` | Windows-Konfiguration (`C:\`, case-insensitive) |
| `forCurrentPlatform()` | Automatische Auswahl basierend auf `os.name` |
| `builder(PathType)` | Neuen Builder von Grund auf erstellen |

### Instanz-Methoden

| Methode | Beschreibung |
|---------|-------------|
| `toBuilder()` | Builder mit aktuellen Einstellungen erstellen |

### `Configuration.Builder`

| Methode | Standard | Beschreibung |
|---------|----------|-------------|
| `setRoots(String, String...)` | -- | Root-Verzeichnisse |
| `setWorkingDirectory(String)` | -- | Arbeitsverzeichnis (absoluter Pfad) |
| `setNameDisplayNormalization(PathNormalization, ...)` | keine | Display-Normalisierung |
| `setNameCanonicalNormalization(PathNormalization, ...)` | keine | Lookup-Normalisierung |
| `setPathEqualityUsesCanonicalForm(boolean)` | `false` | Pfad-Gleichheit |
| `setBlockSize(int)` | `8192` | Block-Grosse in Bytes |
| `setMaxSize(long)` | `4 GB` | Maximaler Speicher |
| `setMaxCacheSize(long)` | = maxSize | Cache-Grosse |
| `setAttributeViews(String, String...)` | -- | Unterstutzte Attribut-Views |
| `addAttributeProvider(AttributeProvider)` | -- | Custom Attribut-Provider |
| `setDefaultAttributeValue(String, Object)` | -- | Standard-Attributwert |
| `setFileTimeSource(FileTimeSource)` | System-Zeit | Zeitquelle |
| `setSupportedFeatures(Feature...)` | -- | Unterstutzte Features |
| `setWatchServiceConfiguration(WatchServiceConfiguration)` | polling(5s) | WatchService-Konfig |
| `build()` | -- | Konfiguration erstellen |

---

## `PathType`

Definiert das Pfad-Format.

| Methode | Beschreibung |
|---------|-------------|
| `PathType.unix()` | Unix-Pfade (`/foo/bar`) |
| `PathType.windows()` | Windows-Pfade (`C:\foo\bar`, `\\host\share\foo`) |

---

## `PathNormalization`

Enum fur Pfad-Normalisierungsoptionen.

| Wert | Beschreibung |
|------|-------------|
| `NONE` | Keine Normalisierung |
| `NFC` | Unicode Normalization Form Composed |
| `NFD` | Unicode Normalization Form Decomposed |
| `CASE_FOLD_ASCII` | Case-insensitive fur ASCII |
| `CASE_FOLD_UNICODE` | Case-insensitive fur Unicode (benotigt ICU4J) |

---

## `Feature`

Enum fur optionale Dateisystem-Features.

| Wert | Beschreibung |
|------|-------------|
| `LINKS` | Harte Links (`Files.createLink()`) |
| `SYMBOLIC_LINKS` | Symbolische Links |
| `SECURE_DIRECTORY_STREAM` | `SecureDirectoryStream`-Unterstutzung |
| `FILE_CHANNEL` | `FileChannel` und `AsynchronousFileChannel` |

---

## `WatchServiceConfiguration`

Konfiguration fur den WatchService.

| Methode | Beschreibung |
|---------|-------------|
| `polling(long interval, TimeUnit unit)` | Polling-Intervall fur Verzeichnisuberwachung |

---

## `FileTimeSource`

Funktionales Interface fur die Zeitquelle des Dateisystems.

```java
@FunctionalInterface
public interface FileTimeSource {
    FileTime now();
}
```

---

## Verwendung mit `java.nio.file`

Nach dem Erstellen eines Dateisystems wird ausschliesslich die Standard-API verwendet:

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

// Alle java.nio.file.Files Methoden funktionieren:
Path path = fs.getPath("/foo/bar.txt");
Files.createDirectories(path.getParent());
Files.writeString(path, "inhalt");
Files.readString(path);
Files.delete(path);
Files.exists(path);
Files.isDirectory(path);
Files.list(fs.getPath("/foo"));
Files.walk(fs.getPath("/"));
Files.copy(source, target);
Files.move(source, target);

// FileChannel
try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
    // ...
}

// WatchService
try (WatchService ws = fs.newWatchService()) {
    fs.getPath("/foo").register(ws, StandardWatchEventKinds.ENTRY_CREATE);
}

// Attribute
Files.setAttribute(path, "posix:permissions", PosixFilePermissions.fromString("rwxr-x---"));
PosixFileAttributes attrs = Files.readAttributes(path, PosixFileAttributes.class);
```
