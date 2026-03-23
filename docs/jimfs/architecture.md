# Architektur

Dieses Dokument beschreibt den internen Aufbau von Jimfs und wie die einzelnen Komponenten zusammenspielen.

## Ubersicht

```
┌─────────────────────────────────────────────────────────┐
│                    Jimfs (Factory)                       │
│              Erstellt FileSystem-Instanzen               │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│                  JimfsFileSystem                         │
│         Zentrale FileSystem-Implementierung              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  FileTree     │  │ FileStore    │  │ PathService  │  │
│  │  (Lookup)     │  │ (Storage)    │  │ (Pfade)      │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────────┘  │
│         │                 │                              │
│         ▼                 ▼                              │
│  ┌──────────────┐  ┌──────────────┐                     │
│  │ FileSystem   │  │  HeapDisk    │                     │
│  │ View         │  │  (Speicher)  │                     │
│  └──────────────┘  └──────────────┘                     │
└─────────────────────────────────────────────────────────┘
```

## Kernklassen

### Einstiegspunkt

| Klasse | Beschreibung |
|--------|-------------|
| `Jimfs` | Statische Factory-Methoden zum Erstellen von Dateisystemen |
| `Configuration` | Immutable Konfigurationsobjekt mit Builder-Pattern |

### Dateisystem

| Klasse | Beschreibung |
|--------|-------------|
| `JimfsFileSystem` | Implementiert `java.nio.file.FileSystem` |
| `JimfsFileSystemProvider` | Implementiert `FileSystemProvider` -- fuhrt die eigentlichen Operationen aus |
| `SystemJimfsFileSystemProvider` | Wird uber `ServiceLoader` geladen, delegiert an `JimfsFileSystemProvider` |
| `JimfsFileSystems` | Internes Factory fur das Zusammenbauen eines Dateisystems |
| `FileSystemState` | Verwaltet den Zustand und offene Ressourcen |

### Dateien und Verzeichnisse

| Klasse | Beschreibung |
|--------|-------------|
| `File` | Basis-Klasse fur alle Datei-Typen (regulare Datei, Verzeichnis, Symlink) |
| `RegularFile` | Block-basierter Speicher fur regulare Dateien |
| `Directory` | Verzeichnis mit `DirectoryEntry`-Eintregen |
| `DirectoryEntry` | Name-zu-File-Zuordnung innerhalb eines Verzeichnisses |
| `SymbolicLink` | Symbolischer Link mit Ziel-Pfad |
| `FileFactory` | Erstellt neue File-Instanzen |

### Pfade

| Klasse | Beschreibung |
|--------|-------------|
| `JimfsPath` | Implementiert `java.nio.file.Path` |
| `PathType` | Abstrakte Klasse fur Pfad-Typen (Unix, Windows) |
| `UnixPathType` | Unix-spezifische Pfadlogik |
| `WindowsPathType` | Windows-spezifische Pfadlogik |
| `PathService` | Erstellt und verwaltet Pfad-Instanzen |
| `PathNormalization` | Unicode-Normalisierung und Case-Folding |
| `Name` | Einzelnes Pfad-Element mit Display- und kanonischer Form |

### Speicher

| Klasse | Beschreibung |
|--------|-------------|
| `HeapDisk` | Pseudo-Disk fur Block-Allokation im Heap |
| `RegularFile` | Speichert Datei-Inhalte in `byte[]`-Blocken |

### I/O-Kanale

| Klasse | Beschreibung |
|--------|-------------|
| `JimfsFileChannel` | Implementiert `FileChannel` |
| `JimfsAsynchronousFileChannel` | Implementiert `AsynchronousFileChannel` |
| `JimfsInputStream` | `InputStream` fur Jimfs-Dateien |
| `JimfsOutputStream` | `OutputStream` fur Jimfs-Dateien |

### Attribute

| Klasse | Beschreibung |
|--------|-------------|
| `AttributeService` | Zentrale Verwaltung aller Attribut-Provider |
| `AttributeProvider` | Abstrakte Basis fur Attribut-Views |
| `BasicAttributeProvider` | `basic` View |
| `PosixAttributeProvider` | `posix` View |
| `DosAttributeProvider` | `dos` View |
| `AclAttributeProvider` | `acl` View |
| `OwnerAttributeProvider` | `owner` View |
| `UnixAttributeProvider` | `unix` View |
| `UserDefinedAttributeProvider` | `user` View |

### WatchService

| Klasse | Beschreibung |
|--------|-------------|
| `AbstractWatchService` | Basis-Implementierung |
| `PollingWatchService` | Polling-basierte Implementierung |
| `WatchServiceConfiguration` | Konfiguration fur den WatchService |

## Speichermodell

Jimfs speichert Datei-Inhalte in `byte[]`-Blocken auf dem Java Heap:

```
HeapDisk
├── blockSize: 8192 bytes (konfigurierbar)
├── maxBlockCount: maxSize / blockSize
├── blockCache: RegularFile (wiederverwendbare Blocke)
└── allocatedBlockCount: aktuelle Anzahl belegter Blocke

RegularFile
├── blocks: byte[][] (Array von Block-Referenzen)
├── blockCount: Anzahl belegter Blocke
└── size: tatsachliche Dateigrosse in Bytes
```

**Wichtige Eigenschaften:**
- Speicher wird **lazy** allokiert -- nur bei Bedarf
- Freigegebene Blocke werden gecacht und wiederverwendet (reduziert GC-Druck)
- Die Cache-Grosse ist konfigurierbar (`setMaxCacheSize`)
- Bei Speichermangel wird eine `IOException("out of disk space")` geworfen

## Datei-Lookup

Der Pfad-Lookup erfolgt uber `FileTree`:

1. **Pfad parsen** -- `PathType` zerlegt den String in Root und Name-Elemente
2. **Normalisierung** -- `PathNormalization` wendet Unicode-Normalisierung und Case-Folding an
3. **Directory-Traversal** -- `FileTree` lost den Pfad Segment fur Segment auf
4. **Symlink-Auflosung** -- Symbolische Links werden transparent aufgelost (mit Loop-Detection)

## URI-Schema

Jimfs verwendet das URI-Schema `jimfs://`:

```
jimfs://<filesystem-name>/<pfad>

Beispiele:
  jimfs://mein-fs/foo/bar.txt
  jimfs://a1b2c3d4-e5f6-7890-abcd-ef1234567890/data
```

## FileSystemProvider-Architektur

Jimfs verwendet zwei Provider, um Kompatibilitat mit dem `ServiceLoader`-Mechanismus sicherzustellen:

```
FileSystems.newFileSystem(uri, env)
        │
        ▼
SystemJimfsFileSystemProvider          ← Wird per ServiceLoader geladen
        │                                (META-INF/services)
        │ delegiert an
        ▼
JimfsFileSystemProvider                ← Fuhrt die eigentlichen Operationen aus
        │
        ▼
JimfsFileSystem                        ← Das In-Memory-Dateisystem
```

Diese Zwei-Provider-Architektur lost ClassLoader-Probleme in Umgebungen wie JBoss/WildFly.

## Thread-Sicherheit

- `HeapDisk` -- Alle Methoden sind `synchronized`
- `RegularFile` -- Lese-/Schreiboperationen sind thread-safe
- `FileSystemState` -- Verwaltet offene Ressourcen thread-safe
- `Directory` -- Zugriff wird uber den umschliessenden `FileSystemView` synchronisiert
