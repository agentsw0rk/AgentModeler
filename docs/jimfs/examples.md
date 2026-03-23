# Beispiele

Praxisnahe Code-Beispiele fur verschiedene Jimfs-Anwendungsfalle.

## Dateien und Verzeichnisse

### Verzeichnisstruktur erstellen

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

// Verschachtelte Verzeichnisse
Files.createDirectories(fs.getPath("/app/config"));
Files.createDirectories(fs.getPath("/app/data/logs"));
Files.createDirectories(fs.getPath("/app/data/cache"));

// Verzeichnisstruktur auflisten
try (Stream<Path> walk = Files.walk(fs.getPath("/app"))) {
    walk.forEach(System.out::println);
}
// /app
// /app/config
// /app/data
// /app/data/logs
// /app/data/cache
```

### Dateien kopieren und verschieben

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

Path source = fs.getPath("/source.txt");
Files.writeString(source, "original");

// Kopieren
Path copy = fs.getPath("/copy.txt");
Files.copy(source, copy);

// Verschieben (Umbenennen)
Path moved = fs.getPath("/moved.txt");
Files.move(source, moved);

// Kopieren mit Optionen
Files.copy(copy, fs.getPath("/backup.txt"),
    StandardCopyOption.REPLACE_EXISTING,
    StandardCopyOption.COPY_ATTRIBUTES);
```

### Datei-Inhalte streamen

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

Path file = fs.getPath("/data.txt");
Files.write(file, List.of("eins", "zwei", "drei", "vier", "funf"));

// Zeilen filtern
try (Stream<String> lines = Files.lines(file)) {
    List<String> filtered = lines
        .filter(line -> line.length() > 3)
        .collect(Collectors.toList());
    // [zwei, drei, vier, funf]
}
```

## Binardaten

### Bytes lesen und schreiben

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
Path file = fs.getPath("/binary.dat");

// Schreiben
byte[] data = {0x48, 0x65, 0x6C, 0x6C, 0x6F};
Files.write(file, data);

// Lesen
byte[] read = Files.readAllBytes(file);
```

### FileChannel verwenden

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
Path file = fs.getPath("/channel.dat");

// Schreiben uber FileChannel
try (FileChannel ch = FileChannel.open(file,
        StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
    ByteBuffer buf = ByteBuffer.wrap("Hello FileChannel".getBytes());
    ch.write(buf);
}

// Lesen uber FileChannel
try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
    ByteBuffer buf = ByteBuffer.allocate((int) ch.size());
    ch.read(buf);
    buf.flip();
    String content = StandardCharsets.UTF_8.decode(buf).toString();
}
```

## Links

### Harte Links

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

Path original = fs.getPath("/original.txt");
Files.writeString(original, "shared content");

Path hardLink = fs.getPath("/hardlink.txt");
Files.createLink(hardLink, original);

// Beide zeigen auf denselben Inhalt
assertEquals(Files.readString(original), Files.readString(hardLink));

// Anderung uber einen Link ist uber den anderen sichtbar
Files.writeString(original, "updated");
assertEquals("updated", Files.readString(hardLink));
```

### Symbolische Links

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

Path target = fs.getPath("/data/config.yaml");
Files.createDirectories(target.getParent());
Files.writeString(target, "db: localhost");

Path symlink = fs.getPath("/config.yaml");
Files.createSymbolicLink(symlink, target);

// Lesen uber Symlink
assertEquals("db: localhost", Files.readString(symlink));

// Symlink-Ziel auslesen
Path resolvedTarget = Files.readSymbolicLink(symlink);
assertEquals(target, resolvedTarget);
```

## Verzeichnis uberwachen (WatchService)

```java
Configuration config = Configuration.unix().toBuilder()
    .setWatchServiceConfiguration(
        WatchServiceConfiguration.polling(100, TimeUnit.MILLISECONDS)
    )
    .build();

FileSystem fs = Jimfs.newFileSystem(config);
Path dir = fs.getPath("/watched");
Files.createDirectory(dir);

WatchService watchService = fs.newWatchService();
dir.register(watchService,
    StandardWatchEventKinds.ENTRY_CREATE,
    StandardWatchEventKinds.ENTRY_DELETE,
    StandardWatchEventKinds.ENTRY_MODIFY);

// In einem anderen Thread: Datei erstellen
Files.writeString(dir.resolve("new.txt"), "content");

// Events abholen
WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
if (key != null) {
    for (WatchEvent<?> event : key.pollEvents()) {
        System.out.println(event.kind() + ": " + event.context());
    }
    key.reset();
}
```

## Glob und PathMatcher

```java
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

// Testdateien erstellen
for (String name : List.of("app.java", "test.java", "readme.md", "data.json")) {
    Files.writeString(fs.getPath("/src/" + name), "content");
}
Files.createDirectories(fs.getPath("/src"));

// Glob-Pattern verwenden
PathMatcher matcher = fs.getPathMatcher("glob:*.java");

try (DirectoryStream<Path> stream = Files.newDirectoryStream(fs.getPath("/src"), "*.java")) {
    for (Path entry : stream) {
        System.out.println(entry.getFileName()); // app.java, test.java
    }
}

// Rekursiv mit Files.walk
try (Stream<Path> walk = Files.walk(fs.getPath("/src"))) {
    List<Path> javaFiles = walk
        .filter(p -> p.toString().endsWith(".java"))
        .collect(Collectors.toList());
}
```

## Windows-Dateisystem simulieren

```java
Configuration config = Configuration.windows().toBuilder()
    .setRoots("C:\\", "D:\\")
    .setWorkingDirectory("C:\\Users\\test")
    .setAttributeViews("basic", "dos")
    .build();

FileSystem fs = Jimfs.newFileSystem(config);

// Windows-Pfade verwenden
Path file = fs.getPath("C:\\Users\\test\\document.txt");
Files.createDirectories(file.getParent());
Files.writeString(file, "Windows content");

// DOS-Attribute setzen
Files.setAttribute(file, "dos:hidden", true);
Files.setAttribute(file, "dos:readonly", true);

// Lesen
boolean hidden = (boolean) Files.getAttribute(file, "dos:hidden");
assertTrue(hidden);

// Laufwerk wechseln
Path dDrive = fs.getPath("D:\\backup");
Files.createDirectories(dDrive);
```

## POSIX-Berechtigungen

```java
Configuration config = Configuration.unix().toBuilder()
    .setAttributeViews("basic", "owner", "posix", "unix")
    .setDefaultAttributeValue("owner:owner", "testuser")
    .setDefaultAttributeValue("posix:group", "testgroup")
    .build();

FileSystem fs = Jimfs.newFileSystem(config);

Path script = fs.getPath("/bin/run.sh");
Files.createDirectories(script.getParent());
Files.writeString(script, "#!/bin/bash\necho hello");

// Berechtigungen setzen
Files.setPosixFilePermissions(script,
    PosixFilePermissions.fromString("rwxr-xr-x"));

// Berechtigungen lesen
PosixFileAttributes attrs = Files.readAttributes(script, PosixFileAttributes.class);
System.out.println(PosixFilePermissions.toString(attrs.permissions())); // rwxr-xr-x
System.out.println(attrs.owner().getName());  // testuser
System.out.println(attrs.group().getName());  // testgroup
```

## Begrenzter Speicherplatz

```java
Configuration config = Configuration.unix().toBuilder()
    .setMaxSize(1024 * 1024)  // 1 MB Limit
    .setBlockSize(1024)       // 1 KB Blocke
    .build();

FileSystem fs = Jimfs.newFileSystem(config);

Path file = fs.getPath("/large.bin");

// Versuch, mehr als 1 MB zu schreiben -> IOException
byte[] data = new byte[2 * 1024 * 1024]; // 2 MB
assertThrows(IOException.class, () -> Files.write(file, data));
```

## Kontrollierbare Zeit (fur Tests)

```java
AtomicLong clock = new AtomicLong(0);

Configuration config = Configuration.unix().toBuilder()
    .setFileTimeSource(() -> FileTime.fromMillis(clock.get()))
    .build();

FileSystem fs = Jimfs.newFileSystem(config);

clock.set(1000);
Path file = fs.getPath("/test.txt");
Files.writeString(file, "v1");

FileTime created = Files.getLastModifiedTime(file);
assertEquals(1000, created.toMillis());

clock.set(5000);
Files.writeString(file, "v2");

FileTime modified = Files.getLastModifiedTime(file);
assertEquals(5000, modified.toMillis());
```

## Spring-Integration

```java
@Configuration
public class FileSystemConfig {

    @Bean
    @Profile("test")
    public FileSystem inMemoryFileSystem() {
        return Jimfs.newFileSystem(
            com.google.common.jimfs.Configuration.unix());
    }

    @Bean
    @Profile("!test")
    public FileSystem defaultFileSystem() {
        return FileSystems.getDefault();
    }
}

@Service
public class StorageService {

    private final FileSystem fileSystem;

    public StorageService(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public void store(String name, byte[] data) throws IOException {
        Path path = fileSystem.getPath("/storage", name);
        Files.createDirectories(path.getParent());
        Files.write(path, data);
    }
}
```
