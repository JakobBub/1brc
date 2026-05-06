## 1. Original User Prompt

sauber, soweit scheint das zu funktionieren. Ich möchte nun, dass wir weiterhin auf geschwindigkeit optimieren. Achte weiterhin auf das regelwerk

# Globale Projekt-Richtlinien (1 Billion Row Challenge)

## 1. Tech-Stack & Architektur
- **Sprache:** Java 21+ (Fokus auf rohe Performance)
- **Frameworks:** KEINE (Kein Spring, kein Quarkus). Nur Standardbibliothek.
- **I/O:** Memory-Mapped Files (`FileChannel`, `MemorySegment`), rohe Byte-Arrays statt `String` Objekte.
- **Architektur:** Monolithisches, extrem optimiertes Skript. Keine Schichten-Architektur (Controller/Service/Repository) nötig.

## 2. Skill-Referenzen (WICHTIG FÜR AGENTEN)
Wenn du spezifische Implementierungen vornimmst, fokussiere dich auf:
- Vermeidung von Garbage Collection (Object Allocation auf ein Minimum reduzieren).
- Effizientes Parsen von Bytes (Custom Hash-Maps statt `java.util.HashMap`).
- Nebenläufigkeit (Parallelisierung der Datenverarbeitung in Chunks).

## 3. Projekt-Struktur & Pfade
- Haupt-Klasse: `src/main/java/dev/morling/onebrc/CalculateAverage.java`
- Test-Daten: Ein Bash/Python Skript generiert die `measurements.txt` (Achtung: wird 13-14 GB groß!).
- Kompilierung: `javac -d target src/main/java/dev/morling/onebrc/CalculateAverage.java`
- Ausführung: `java -cp target dev.morling.onebrc.CalculateAverage`

## 4. Workflow & Automatisierungs-Regeln
- **Shared Memory:** Der Plan liegt in `ARCHITECTURE_PLAN.md`. Die Sektion `## 1. Original User Prompt` steht ganz oben.
- **Performance-Fokus:** Jeder Iterations-Loop zielt darauf ab, die Ausführungszeit der Datei zu verringern.
- **Automatisierter Handshake:** `switch_mode` Tool wird genutzt (Planer -> Dev -> QA).

## 2. Implementierungsplan - One Billion Row Challenge (1BRC) - Phase 2 (Optimierung)

### Phase 1: Analyse und Limitierungen der bisherigen Version
- Bisher werden zur Laufzeit extrem viele `String`- und Objekt-Instanzen erzeugt (während des Parsens einer Zeile und durch die `HashMap`).
- Das Map-Lookup über `String` erzwingt String-Codierung bei JEDER gelesenen Zeile, was immens CPU-Zeit und GC-Zyklen (Garbage Collection) frisst.
- `MappedByteBuffer` ist langsam und durch 2GB Limits (Integer max) bei großen Dateien unhandlich im Code umgesetzt.

### Phase 2: MemorySegment und FFM API (Java 21)
- **I/O Switch:** Wechsel von `MappedByteBuffer` auf `MemorySegment` aus der Java 21 Foreign Function & Memory API (Preview-Feature, aber passend für "rohe Performance").
- Das erlaubt das Lesen und Mappen gigantischer Dateien (weit über 2GB) ohne Chunk-Limits und mit C-ähnlichen Pointer-Zugriffen (extrem schnell).
  
### Phase 3: Zero-Allocation Custom Hash Table
- **Array-basierte Hash Map:** Ersatz von `java.util.HashMap` durch ein flaches Primitiv-Array-Konstrukt. 
- **Linear Probing:** Schnelle Konfliktauflösung in Array-Slots.
- Wir speichern den Key (Stationsname) nicht als String, sondern nur als Hashcode, und vergleichen bei Kollisionen direkt die Bytes am Pointer (MemorySegment offset).

### Phase 4: Integer Parsing
- Kein Double Parsing! Die Temperatur wird byte-weise direkt in einen Integer umgerechnet (`-12.3` -> `-123`). Nur bei der finalen Zusammenführung der Threads formatiert man dies auf Double.

### Phase 5: Aggregation und Threads
- Jede Tabelle (Hash Map) bleibt komplett lokal im jeweiligen Thread, ohne Synchronisierung.
- Am Ende merge aller Arrays in einer Baum-Struktur (TreeMap für die alphabetische Sortierung), wobei die Namen erst GANZ ZUM SCHLUSS in Java `String`s umgeformt werden.