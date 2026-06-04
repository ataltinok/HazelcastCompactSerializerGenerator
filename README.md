# Compact Serializers Generator

Reflection-based Java Hazelcast Compact Serializer generator.

Generates `CompactSerializer<T>` implementations for a model class and every class it depends on recursively.

## Prerequisites

- JDK 17+
- Maven 3.9+
- The target projects (inputted in --root) must be **compiled** (`mvn -DskipTests install`) so that each module has a populated `target/classes` directory.

## Run with `java`

Build with

```bash
mvn package
```

Produces `target/compact-serializers.jar` (shaded, runnable).

Then run

```bash
java -jar target/compact-serializers.jar generate \
    --root  C:/Users/you/git/my-project \
    --model com.company-name.project-name.model.MyModel \
    [--out   ./out/myModel/serializers] \
    [--package  com.company-name.project-name.serializer] \
    [--pairs ./out/myModel/pairs] \
```

### Flags

| Flag | Required | Purpose |
| ------ | ---------- | --------- |
| `--root` | yes | Directory containing the Maven project(s). Scanned recursively for `pom.xml`. |
| `--model` | yes | Fully-qualified class name of the root model. |
| `--out` | no | Output directory for generated `*Serializer.java` files. Defaults to `out/<ModelName>/serializer`. |
| `--package` | no | Package declaration emitted into every file. Defaults to `TODO_PACKAGE`. |
| `--pairs` | no | Folder for the `ClassName,ProjectName` CSV (written as `pairs.csv`). Defaults to `out/<ModelName>/pairs`. Always emitted. |
| `--anchor-artifact` | no | ArtifactId of the module to load the root model from. Auto-detected when omitted. |

Outputs one `<ClassName>Serializer.java` per discovered class into `--out`.

## Run from IDE

Duplicate `args-example.txt` as `args.txt` and fill in the arguments as described. Don't forget to put '=' after the argument names.

Run the main method in `Application.java`.

## Post-processing generated files

Generated files are intentionally **not** ready-to-compile out of the box. After
copying each file into its target module:

1. Fix the `package` declaration if you didn't pass `--package`.
2. Organise imports (IDE shortcut — the generator emits only the four Hazelcast/
   jakarta imports; you still need an import for `CompactSerializerUtil` and for
   your model class).
3. Reformat if needed.

## Pairs CSV format

Two columns, no header, no quoting:

```text
ClassName,artifactId
MyModel,my-project
AnotherModel,my-project
MyOtherModel,some-parent-project
```

Comment lines start with `#`. Blank lines are skipped.

## Type mapping

| Java Class | How it is handled |
| --- | --- |
| primitive / wrapper / `String` | `reader.readInt32` / `readNullableInt32` / `readString` etc. |
| array of those | `reader.readArrayOfInt32` / `readArrayOfString` etc. |
| `LocalDate`, `LocalDateTime`, `LocalTime`, `OffsetDateTime`, `BigDecimal` | native Hazelcast readers |
| `LocalDate[]` | `reader.readArrayOfDate` / `writer.writeArrayOfDate` |
| `OffsetDateTime[]` | `reader.readArrayOfTimestampWithTimezone` / `writer.writeArrayOfTimestampWithTimezone` |
| `UUID` | `CompactSerializerUtil.readUUID` |
| `Date` | `CompactSerializerUtil.readDate` |
| `Instant` | `CompactSerializerUtil.readInstant` |
| `List<E>` / `Set<E>` | `CompactSerializerUtil.readList` / `readSet` / `writeCollection` |
| `Map<K,V>` | `CompactSerializerUtil.readMap` / `writeMap` |
| `Map<K, List<V>>` | `readMapOfList` / `writeMapOfList` |
| `Map<K, Date>` | `readMapOfDate` / `writeMapOfDate` |
| `Map<UUID, V>` | `readUUIDMap` / `writeUUIDMap` |
| `Map<String, Map<String, V>>` | `readMapOfMap` / `writeMapOfMap` |
| `enum E` | `CompactSerializerUtil.readEnum(reader, KEY, E.class)` / `writeEnum` |
| any other custom class | `reader.readCompact(KEY)` / `writer.writeCompact(KEY, v)` |
| array of custom class | **TODO comment** — use `readArrayOfCompact` / `writeArrayOfCompact` manually |

## Extending the mapping

Add a new row to
`TypeMapper.java`
and a matching unit test in `src/test/java/.../generator/TypeMapperTest.java`.
Fixture fields live in `TypeMapperFixture.java`.

## Limitations

- Field order follows `Class.getDeclaredFields()` — stable on Hotspot/OpenJDK but
  not guaranteed by the JLS.
- Only `@jakarta.validation.Valid` triggers recursion (matches the Python tool).
  Fields without `@Valid` are not followed even if their type is a custom class.
- Arrays of custom compact classes produce a `// TODO` comment rather than a
  ready-to-use call; handle those cases by hand.
- The `mvn dependency:build-classpath` invocation blocks up to 10 minutes per
  module and requires `mvn` on `PATH`.
- Generated files do not import `CompactSerializerUtil` or the model class — your
  IDE's "organise imports" action handles it.
- `IPAddress` (`inet.ipaddr`) and `InetAddress` (`java.net`) fields fall through to
  `readCompact` / `writeCompact` in the generator. Use `CompactSerializerUtil.readIPAddress` /
  `writeIPAddress` and `readInetAddress` / `writeInetAddress` manually after generation.

## Running the unit tests

```bash
mvn test
```

Covers: `NameHelpers`, `TypeClassifier`, `TypeMapper` (every mapping row),
`SerializerEmitter` (golden-file compare), `PairsCsv` (round-trip),
`ProjectLocator` (artifactId parse + ownership resolution), and
`DependencyWalker` (BFS over `@Valid` fields on fixture classes).
