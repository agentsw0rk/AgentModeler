# Haufig gestellte Fragen (FAQ)

## Allgemein

### Was ist Jimfs?

Jimfs (Jim File System) ist ein von Google entwickeltes In-Memory-Dateisystem fur Java, das die `java.nio.file` API implementiert. Es speichert alle Dateien und Verzeichnisse im Heap-Speicher der JVM und benotigt keinen Zugriff auf das reale Dateisystem.

### Wofur wird Jimfs typischerweise eingesetzt?

- **Unit-Tests:** Dateisystem-Operationen testen, ohne echte Dateien anzulegen
- **Plattformunabhangige Tests:** Windows-Pfade auf Linux testen (und umgekehrt)
- **Isolierte Tests:** Kein Aufraumen von temporaren Dateien notig
- **Schnelle Tests:** Kein Disk-I/O, alles im Speicher

### Welche Java-Version wird benotigt?

Java 8 oder hoher.

### Welche Abhangigkeiten hat Jimfs?

- **Pflicht:** [Google Guava](https://github.com/google/guava)
- **Optional:** [ICU4J](https://unicode-org.github.io/icu/userguide/icu4j/) (nur fur `CASE_FOLD_UNICODE`)

---

## Verwendung

### Warum bekomme ich eine `UnsupportedOperationException` bei `toFile()`?

`Path.toFile()` funktioniert nur mit dem Standard-Dateisystem der JVM. Da Jimfs ein eigenes Dateisystem ist, wird `UnsupportedOperationException` geworfen. Verwende stattdessen die NIO-APIs:

```java
// Statt: new FileInputStream(path.toFile())
InputStream is = Files.newInputStream(path);

// Statt: new FileOutputStream(path.toFile())
OutputStream os = Files.newOutputStream(path);
```

Siehe [Limitierungen](limitations.md) fur Details.

### Warum funktioniert `Path.of()` / `Paths.get()` nicht mit Jimfs?

Diese Methoden erstellen Pfade fur das Standard-Dateisystem. Fur Jimfs musst du Pfade uber die FileSystem-Instanz erstellen:

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

// FALSCH:
Path p = Path.of("/foo");        // Default-Dateisystem

// RICHTIG:
Path p = fs.getPath("/foo");     // Jimfs-Dateisystem
```

### Kann ich Jimfs mit Spring Boot verwenden?

Ja. Der empfohlene Ansatz ist Dependency Injection des `FileSystem`:

```java
@Configuration
public class FileSystemConfig {

    @Bean
    @Profile("test")
    public FileSystem testFileSystem() {
        return Jimfs.newFileSystem(Configuration.unix());
    }

    @Bean
    @Profile("!test")
    public FileSystem defaultFileSystem() {
        return FileSystems.getDefault();
    }
}
```

Dann das `FileSystem` in deine Services injizieren:

```java
@Service
public class StorageService {
    private final FileSystem fileSystem;

    public StorageService(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public Path store(String name, byte[] data) throws IOException {
        Path path = fileSystem.getPath("/storage", name);
        Files.createDirectories(path.getParent());
        Files.write(path, data);
        return path;
    }
}
```

### Wie teste ich Code, der `java.io.File` erwartet?

Am besten: Code so refactoren, dass er `Path` statt `File` verwendet. Wenn das nicht moglich ist, bleibt als Alternative die Verwendung von temporaren Dateien uber `@TempDir` (JUnit 5) oder `TemporaryFolder` (JUnit 4).

### Warum erkennt mein WatchService keine Anderungen?

1. **Polling-Intervall zu gross:** Standard ist 5 Sekunden. Fur Tests ein kurzeres Intervall setzen.
2. **Gleiche Millisekunde:** Anderungen innerhalb derselben Millisekunde werden nicht erkannt.
3. **Key nicht zuuckgesetzt:** Nach dem Verarbeiten der Events muss `key.reset()` aufgerufen werden.

```java
Configuration config = Configuration.unix().toBuilder()
    .setWatchServiceConfiguration(
        WatchServiceConfiguration.polling(50, TimeUnit.MILLISECONDS)
    )
    .build();
```

---

## Verhalten

### Werden POSIX-Berechtigungen tatsachlich durchgesetzt?

Nein. Berechtigungen werden gespeichert und konnen gelesen werden, haben aber keinen Einfluss auf das Dateisystem-Verhalten. Eine Datei ohne Leserechte kann trotzdem gelesen werden.

### Ist Jimfs thread-safe?

Ja. Die internen Datenstrukturen (HeapDisk, RegularFile, FileSystemState) sind synchronisiert. Mehrere Threads konnen gleichzeitig auf dasselbe Jimfs-Dateisystem zugreifen.

### Bleibt der Inhalt nach dem Schliessen des Dateisystems erhalten?

Nein. Beim Schliessen (`FileSystem.close()`) werden alle Daten verworfen. Es gibt keine Persistenz.

### Wie viel Speicher verbraucht Jimfs?

Jimfs allokiert Speicher lazy -- nur fur tatsachlich erstellte Dateien. Der Verbrauch hangt von der Datenmenge ab:
- Datei-Inhalte werden in Blocken gespeichert (Standard: 8 KB)
- Zusatzlich: Metadaten, Verzeichnisstrukturen, Pfad-Objekte
- Freigegebene Blocke werden gecacht (konfigurierbar uber `setMaxCacheSize`)

### Kann ich mehrere Jimfs-Dateisysteme gleichzeitig verwenden?

Ja. Jeder Aufruf von `Jimfs.newFileSystem()` erstellt eine unabhangige Instanz.

```java
FileSystem unix = Jimfs.newFileSystem("unix-fs", Configuration.unix());
FileSystem windows = Jimfs.newFileSystem("win-fs", Configuration.windows());
// Beide laufen unabhangig voneinander
```

---

## Fehlerbehebung

### `ProviderNotFoundException` in JBoss/WildFly

Jimfs kann den `SystemJimfsFileSystemProvider` moglicherweise nicht uber den ServiceLoader finden. Die Kernfunktionalitat funktioniert trotzdem -- nur URI-basierte Methoden (`Paths.get(URI)`) sind betroffen.

### `NoSuchFileException` beim Schreiben

Verzeichnisse werden nicht automatisch erstellt. Du musst sie explizit anlegen:

```java
Path file = fs.getPath("/a/b/c/file.txt");
Files.createDirectories(file.getParent());  // Nicht vergessen!
Files.writeString(file, "content");
```

### `FileSystemClosedException`

Das Dateisystem wurde bereits geschlossen. Stelle sicher, dass du es nicht vorzeitig schliesst, z.B. durch try-with-resources in einem `@BeforeEach` statt in der Testmethode.

---

## Weitererfuhrende Ressourcen

- [GitHub Repository](https://github.com/google/jimfs)
- [Baeldung: File System Mocking with Jimfs](https://www.baeldung.com/jimfs-file-system-mocking)
- [The Great API: How to Use Jimfs](https://thegreatapi.com/blog/how-to-use-an-in-memory-file-system-in-your-tests-with-googles-jimfs/)
- [Java NIO and Fake Filesystems](https://www.lapthorn.net/blog/2021/06/14/java-nio-and-fake-filesystems/)
- [Maven Central](https://search.maven.org/artifact/com.google.jimfs/jimfs)
