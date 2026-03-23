# Testing mit Jimfs

Jimfs ist ideal fur Unit-Tests, die mit dem Dateisystem arbeiten. Es ist schnell, isoliert und benotigt kein Aufraumen nach dem Test.

## Warum Jimfs fur Tests?

| Vorteil | Beschreibung |
|---------|-------------|
| **Geschwindigkeit** | Kein Disk-I/O, alles im Speicher |
| **Isolation** | Jeder Test bekommt sein eigenes Dateisystem |
| **Kein Cleanup** | Keine temporaren Dateien, die aufgeraumt werden mussen |
| **Plattformunabhangig** | Tests laufen uberall gleich, unabhangig vom OS |
| **Deterministisch** | Keine Race Conditions mit anderen Prozessen |

## Grundmuster

### JUnit 5

```java
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MyFileServiceTest {

    private FileSystem fs;

    @BeforeEach
    void setUp() {
        fs = Jimfs.newFileSystem(Configuration.unix());
    }

    @AfterEach
    void tearDown() throws Exception {
        fs.close();
    }

    @Test
    void shouldCreateFile() throws Exception {
        Path path = fs.getPath("/test.txt");
        Files.writeString(path, "hello");

        assertTrue(Files.exists(path));
        assertEquals("hello", Files.readString(path));
    }
}
```

### JUnit 4

```java
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MyFileServiceTest {

    private FileSystem fs;

    @Before
    public void setUp() {
        fs = Jimfs.newFileSystem(Configuration.unix());
    }

    @After
    public void tearDown() throws Exception {
        fs.close();
    }

    @Test
    public void shouldCreateFile() throws Exception {
        // ...
    }
}
```

## Design-Pattern: Path-Injection

Der Schlussel zu testbarem Code ist, `Path` statt hartcodierter Pfade zu verwenden:

### Vorher (schwer testbar)

```java
public class ConfigLoader {
    public String load() throws IOException {
        return Files.readString(Path.of("/etc/myapp/config.yaml"));
    }
}
```

### Nachher (testbar mit Jimfs)

```java
public class ConfigLoader {
    private final Path configPath;

    public ConfigLoader(Path configPath) {
        this.configPath = configPath;
    }

    public String load() throws IOException {
        return Files.readString(configPath);
    }
}
```

```java
@Test
void shouldLoadConfig() throws Exception {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    Path configPath = fs.getPath("/etc/myapp/config.yaml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "key: value");

    ConfigLoader loader = new ConfigLoader(configPath);
    assertEquals("key: value", loader.load());
}
```

## Design-Pattern: FileSystem-Injection

Fur Klassen, die mehrere Pfade verwenden:

```java
public class DataProcessor {
    private final FileSystem fileSystem;

    public DataProcessor(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public void process() throws IOException {
        Path input = fileSystem.getPath("/data/input.csv");
        Path output = fileSystem.getPath("/data/output.json");
        // ...
    }
}
```

```java
@Test
void shouldProcessData() throws Exception {
    try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
        Files.createDirectories(fs.getPath("/data"));
        Files.writeString(fs.getPath("/data/input.csv"), "a,b,c\n1,2,3");

        DataProcessor processor = new DataProcessor(fs);
        processor.process();

        assertTrue(Files.exists(fs.getPath("/data/output.json")));
    }
}
```

## Plattformspezifische Tests

Teste Verhalten auf verschiedenen Plattformen, unabhangig vom Entwicklungs-OS:

```java
@Test
void shouldHandleWindowsPaths() throws Exception {
    try (FileSystem fs = Jimfs.newFileSystem(Configuration.windows())) {
        Path path = fs.getPath("C:\\Users\\test\\file.txt");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "windows test");

        assertTrue(Files.exists(path));
    }
}

@Test
void shouldHandleCaseInsensitivity() throws Exception {
    try (FileSystem fs = Jimfs.newFileSystem(Configuration.osX())) {
        Path lower = fs.getPath("/test.txt");
        Files.writeString(lower, "content");

        Path upper = fs.getPath("/TEST.TXT");
        assertTrue(Files.exists(upper)); // Case-insensitive!
        assertEquals("content", Files.readString(upper));
    }
}
```

## Symlink-Tests

```java
@Test
void shouldFollowSymlinks() throws Exception {
    try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
        Path target = fs.getPath("/real/file.txt");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "real content");

        Path link = fs.getPath("/link.txt");
        Files.createSymbolicLink(link, target);

        assertEquals("real content", Files.readString(link));
        assertTrue(Files.isSymbolicLink(link));
    }
}
```

## File-Attribute testen

```java
@Test
void shouldSetPosixPermissions() throws Exception {
    Configuration config = Configuration.unix().toBuilder()
        .setAttributeViews("basic", "owner", "posix")
        .build();

    try (FileSystem fs = Jimfs.newFileSystem(config)) {
        Path path = fs.getPath("/secure.txt");
        Files.writeString(path, "secret");

        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(path, perms);

        assertEquals(perms, Files.getPosixFilePermissions(path));
    }
}
```

## WatchService testen

```java
@Test
void shouldDetectFileCreation() throws Exception {
    Configuration config = Configuration.unix().toBuilder()
        .setWatchServiceConfiguration(
            WatchServiceConfiguration.polling(10, TimeUnit.MILLISECONDS)
        )
        .build();

    try (FileSystem fs = Jimfs.newFileSystem(config)) {
        Path dir = fs.getPath("/watched");
        Files.createDirectory(dir);

        try (WatchService ws = fs.newWatchService()) {
            dir.register(ws, StandardWatchEventKinds.ENTRY_CREATE);

            Files.writeString(dir.resolve("new.txt"), "content");

            WatchKey key = ws.poll(1, TimeUnit.SECONDS);
            assertNotNull(key);
        }
    }
}
```

## Die `toFile()`-Falle

Die wichtigste Einschrankung: **`Path.toFile()` wirft eine `UnsupportedOperationException`** bei Jimfs-Pfaden. Jeder Code, der `java.io.File` verwendet, ist nicht mit Jimfs testbar.

### Problemerkennung

```java
// Diese Methoden funktionieren NICHT mit Jimfs:
path.toFile()
new FileInputStream(path.toFile())
new FileOutputStream(path.toFile())
new FileReader(path.toFile())
```

### Losung: NIO-API verwenden

```java
// Statt FileInputStream/FileOutputStream:
InputStream is = Files.newInputStream(path);
OutputStream os = Files.newOutputStream(path);

// Statt FileReader/FileWriter:
BufferedReader reader = Files.newBufferedReader(path);
BufferedWriter writer = Files.newBufferedWriter(path);

// Statt new File(path):
// Path direkt verwenden -- kein File-Objekt notig
```

Siehe [Limitierungen](limitations.md) fur eine vollstandige Ubersicht inkompatibler Bibliotheken.

## Anti-Patterns

### Statische Pfade verwenden

```java
// SCHLECHT -- nicht testbar
public class Logger {
    private static final Path LOG_FILE = Path.of("/var/log/app.log");

    public void log(String message) throws IOException {
        Files.writeString(LOG_FILE, message, StandardOpenOption.APPEND);
    }
}

// GUT -- testbar mit Jimfs
public class Logger {
    private final Path logFile;

    public Logger(Path logFile) {
        this.logFile = logFile;
    }

    public void log(String message) throws IOException {
        Files.writeString(logFile, message,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
```

### Verzeichnisse nicht vorbereiten

```java
// FEHLER -- NoSuchFileException!
Path file = fs.getPath("/a/b/c/file.txt");
Files.writeString(file, "content");

// RICHTIG -- Verzeichnisse zuerst erstellen
Path file = fs.getPath("/a/b/c/file.txt");
Files.createDirectories(file.getParent());
Files.writeString(file, "content");
```

### FileSystem in try-with-resources im Setup

```java
// FEHLER -- FileSystem wird nach setUp() geschlossen!
@BeforeEach
void setUp() throws Exception {
    try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
        this.testPath = fs.getPath("/test");
    } // fs.close() hier -- zu fruh!
}

// RICHTIG -- Lifecycle manuell verwalten
@BeforeEach
void setUp() {
    fs = Jimfs.newFileSystem(Configuration.unix());
}

@AfterEach
void tearDown() throws Exception {
    fs.close();
}
```

## Jimfs vs. `@TempDir`

| Kriterium | Jimfs | `@TempDir` (JUnit 5) |
|-----------|-------|---------------------|
| **Speicher** | In-Memory (Heap) | Echtes Dateisystem |
| **Geschwindigkeit** | Sehr schnell | I/O-gebunden |
| **`toFile()` kompatibel** | Nein | Ja |
| **Plattform-Simulation** | Ja (Unix/Win/macOS) | Nur aktuelle Plattform |
| **Legacy-Bibliotheken** | Nur NIO-kompatible | Alle |
| **Cleanup** | Automatisch (GC) | Automatisch (JUnit) |

**Empfehlung:**
- Verwende **Jimfs** wenn dein Code `java.nio.file` APIs nutzt
- Verwende **`@TempDir`** wenn du `java.io.File` oder Legacy-Bibliotheken testen musst

## Tipps

- Verwende `Configuration.unix()` als Standard fur Tests -- es sei denn, du testest plattformspezifisches Verhalten
- Schliesse das `FileSystem` immer nach dem Test (try-with-resources oder `@AfterEach`)
- Jimfs-Attribute sind deklarativ -- `posix:permissions` werden gespeichert, beeinflussen aber nicht das Zugriffsverhalten
- Fur zeitabhangige Tests: verwende `setFileTimeSource()` mit einer kontrollierbaren Zeitquelle
- Erstelle Verzeichnisse immer mit `Files.createDirectories()` vor dem Schreiben von Dateien
- Nutze die FileSystem-Injection fur Services, die mehrere Dateipfade verwenden

## Weiterfuhrende Ressourcen

- [Baeldung: File System Mocking with Jimfs](https://www.baeldung.com/jimfs-file-system-mocking)
- [The Great API: How to Use Jimfs in Tests](https://thegreatapi.com/blog/how-to-use-an-in-memory-file-system-in-your-tests-with-googles-jimfs/)
- [Java NIO and Fake Filesystems](https://www.lapthorn.net/blog/2021/06/14/java-nio-and-fake-filesystems/)
