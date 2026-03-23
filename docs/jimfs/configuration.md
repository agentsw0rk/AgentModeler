# Konfiguration

Die `Configuration`-Klasse steuert das Verhalten eines Jimfs-Dateisystems. Konfigurationen sind **immutable** und werden uber den Builder erstellt.

## Vorkonfigurierte Profile

| Methode | Root | Separator | Case-Sensitive | Working Dir |
|---------|------|-----------|----------------|-------------|
| `Configuration.unix()` | `/` | `/` | Ja | `/work` |
| `Configuration.osX()` | `/` | `/` | Nein (ASCII) | `/work` |
| `Configuration.windows()` | `C:\` | `\` | Nein (ASCII) | `C:\work` |
| `Configuration.forCurrentPlatform()` | *automatisch* | *automatisch* | *automatisch* | *automatisch* |

## Konfiguration anpassen

Jede Konfiguration kann uber `toBuilder()` modifiziert werden:

```java
Configuration config = Configuration.unix().toBuilder()
    .setAttributeViews("basic", "owner", "posix", "unix")
    .setWorkingDirectory("/home/user")
    .setBlockSize(4096)
    .build();

FileSystem fs = Jimfs.newFileSystem(config);
```

## Komplett eigene Konfiguration

```java
Configuration config = Configuration.builder(PathType.windows())
    .setRoots("C:\\", "D:\\", "E:\\")
    .setWorkingDirectory("C:\\Users\\test")
    .setAttributeViews("basic", "owner", "dos")
    .setSupportedFeatures(Feature.LINKS, Feature.SYMBOLIC_LINKS, Feature.FILE_CHANNEL)
    .setBlockSize(8192)
    .setMaxSize(512 * 1024 * 1024) // 512 MB
    .build();
```

## Konfigurationsoptionen

### Pfad-Typ (`PathType`)

| Typ | Beschreibung |
|-----|-------------|
| `PathType.unix()` | Unix-Pfade mit `/` als Separator und Root |
| `PathType.windows()` | Windows-Pfade mit `\` als Separator, `/` wird ebenfalls akzeptiert |

### Roots und Working Directory

```java
.setRoots("/")                          // Ein Root
.setRoots("C:\\", "D:\\")              // Mehrere Roots (Windows)
.setWorkingDirectory("/home/user")      // Absoluter Pfad
```

### Dateinamen-Normalisierung

Steuert, wie Dateinamen normalisiert und verglichen werden:

```java
// Display-Normalisierung (fur Path.toString())
.setNameDisplayNormalization(PathNormalization.NFC)

// Kanonische Normalisierung (fur Datei-Lookup)
.setNameCanonicalNormalization(PathNormalization.NFD, PathNormalization.CASE_FOLD_ASCII)
```

**Verfugbare Normalisierungen:**

| Normalisierung | Beschreibung |
|---------------|-------------|
| `NONE` | Keine Normalisierung |
| `NFC` | Unicode NFC (Composed) |
| `NFD` | Unicode NFD (Decomposed) |
| `CASE_FOLD_ASCII` | Case-insensitive fur ASCII-Zeichen |
| `CASE_FOLD_UNICODE` | Case-insensitive fur alle Unicode-Zeichen (benotigt ICU4J) |

### Pfad-Gleichheit

```java
// Pfad-Gleichheit basiert auf kanonischer Form (z.B. case-insensitive)
.setPathEqualityUsesCanonicalForm(true)
```

### Speicher-Konfiguration

```java
.setBlockSize(8192)          // Block-Grosse in Bytes (Standard: 8 KB)
.setMaxSize(4L * 1024 * 1024 * 1024)  // Max. Speicher (Standard: 4 GB)
.setMaxCacheSize(0)          // Cache-Grosse fur freigegebene Blocke (0 = kein Cache)
```

| Parameter | Standard | Beschreibung |
|-----------|----------|-------------|
| `blockSize` | 8192 (8 KB) | Minimale Granularitat fur Dateigrosse |
| `maxSize` | 4 GB | Maximaler Gesamtspeicher des Dateisystems |
| `maxCacheSize` | = maxSize | Wie viel freigegebener Speicher gecacht wird |

> **Hinweis:** Der Speicher wird nicht vorab allokiert. Jimfs belegt nur so viel Heap wie tatsachlich fur Dateien benotigt wird.

### File Attribute Views

```java
.setAttributeViews("basic", "owner", "posix", "unix")
```

| View | Interface | Beschreibung |
|------|-----------|-------------|
| `basic` | `BasicFileAttributeView` | Grosse, Zeiten, Dateityp |
| `owner` | `FileOwnerAttributeView` | Datei-Besitzer |
| `posix` | `PosixFileAttributeView` | POSIX-Berechtigungen, Gruppe |
| `dos` | `DosFileAttributeView` | DOS-Attribute (readonly, hidden, ...) |
| `acl` | `AclFileAttributeView` | Access Control Lists |
| `unix` | -- | Unix-spezifische Attribute (uid, gid, ...) |
| `user` | `UserDefinedFileAttributeView` | Benutzerdefinierte Attribute |

### Standard-Attributwerte

```java
.setDefaultAttributeValue("owner:owner", "testuser")
.setDefaultAttributeValue("posix:group", "testgroup")
.setDefaultAttributeValue("posix:permissions", "rwxr-xr--")
.setDefaultAttributeValue("dos:hidden", true)
```

### Features

```java
.setSupportedFeatures(
    Feature.LINKS,                    // Harte Links
    Feature.SYMBOLIC_LINKS,           // Symbolische Links
    Feature.SECURE_DIRECTORY_STREAM,  // SecureDirectoryStream
    Feature.FILE_CHANNEL              // FileChannel und AsynchronousFileChannel
)
```

| Feature | Beschreibung |
|---------|-------------|
| `LINKS` | `Files.createLink()` -- Harte Links auf regulare Dateien |
| `SYMBOLIC_LINKS` | `Files.createSymbolicLink()`, `Files.readSymbolicLink()` |
| `SECURE_DIRECTORY_STREAM` | `DirectoryStream` implementiert auch `SecureDirectoryStream` |
| `FILE_CHANNEL` | `FileChannel.open()`, `AsynchronousFileChannel.open()` |

### WatchService

```java
import static java.util.concurrent.TimeUnit.SECONDS;

.setWatchServiceConfiguration(
    WatchServiceConfiguration.polling(5, SECONDS)  // Standard: 5 Sekunden
)
```

### FileTimeSource

Erlaubt es, die Zeitquelle fur Datei-Timestamps zu kontrollieren (nutzlich fur Tests):

```java
.setFileTimeSource(myCustomTimeSource)
```

## Vollstandiges Beispiel

```java
Configuration config = Configuration.unix().toBuilder()
    .setWorkingDirectory("/home/testuser")
    .setAttributeViews("basic", "owner", "posix", "unix")
    .setDefaultAttributeValue("owner:owner", "testuser")
    .setDefaultAttributeValue("posix:group", "users")
    .setDefaultAttributeValue("posix:permissions", "rwxr-xr-x")
    .setSupportedFeatures(
        Feature.LINKS,
        Feature.SYMBOLIC_LINKS,
        Feature.SECURE_DIRECTORY_STREAM,
        Feature.FILE_CHANNEL
    )
    .setBlockSize(4096)
    .setMaxSize(256 * 1024 * 1024) // 256 MB
    .setMaxCacheSize(64 * 1024 * 1024) // 64 MB Cache
    .setWatchServiceConfiguration(
        WatchServiceConfiguration.polling(2, TimeUnit.SECONDS)
    )
    .build();

FileSystem fs = Jimfs.newFileSystem("test-fs", config);
```
