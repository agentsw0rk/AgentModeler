# Jimfs -- In-Memory File System for Java

Jimfs ist ein **In-Memory-Dateisystem** fur Java 8+, das die [`java.nio.file`](https://docs.oracle.com/javase/8/docs/api/java/nio/file/package-summary.html) API vollstandig implementiert. Es wurde von Google entwickelt und eignet sich hervorragend fur Unit-Tests, Prototyping und alle Szenarien, in denen ein echtes Dateisystem nicht benotigt oder gewunscht wird.

## Inhaltsverzeichnis

| Dokument | Beschreibung |
|----------|-------------|
| [Getting Started](getting-started.md) | Schnelleinstieg mit Maven/Gradle und erstem Beispiel |
| [Konfiguration](configuration.md) | Alle Konfigurationsoptionen im Detail |
| [Architektur](architecture.md) | Interner Aufbau und Klassenstruktur |
| [API-Referenz](api-reference.md) | Ubersicht der offentlichen API |
| [Testing Guide](testing.md) | Best Practices fur Tests mit Jimfs |
| [Beispiele](examples.md) | Praxisnahe Code-Beispiele |
| [Limitierungen](limitations.md) | Bekannte Einschrankungen und Workarounds |
| [FAQ](faq.md) | Haufig gestellte Fragen |

## Auf einen Blick

```java
// Unix-artiges Dateisystem erstellen
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

// Dateien und Verzeichnisse anlegen
Path dir = fs.getPath("/data");
Files.createDirectories(dir);

Path file = dir.resolve("hello.txt");
Files.writeString(file, "Hallo Welt!");

// Lesen
String content = Files.readString(file);
```

## Features

- Vollstandige `java.nio.file` Implementierung
- Vorkonfigurierte Profile: **Unix**, **macOS**, **Windows**
- Unterstutzung fur symbolische und harte Links
- File Attribute Views (`basic`, `posix`, `dos`, `acl`, `owner`, `unix`, `user`)
- `FileChannel`, `WatchService`, `SecureDirectoryStream`
- Konfigurierbarer Speicher (Block-Grosse, Max-Grosse, Cache)
- Kein Zugriff auf das echte Dateisystem -- perfekt fur isolierte Tests

## Abhangigkeiten

**Maven:**
```xml
<dependency>
  <groupId>com.google.jimfs</groupId>
  <artifactId>jimfs</artifactId>
  <version>1.3.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'com.google.jimfs:jimfs:1.3.0'
```

Jimfs benotigt [Google Guava](https://github.com/google/guava) als Runtime-Abhangigkeit. Optional kann [ICU4J](https://unicode-org.github.io/icu/userguide/icu4j/) fur erweiterte Unicode-Normalisierung eingebunden werden.

## Lizenz

Jimfs steht unter der [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

---

> **Quellcode:** [`libapp/jimfs/`](../../libapp/jimfs/)
> **Upstream:** [github.com/google/jimfs](https://github.com/google/jimfs)
