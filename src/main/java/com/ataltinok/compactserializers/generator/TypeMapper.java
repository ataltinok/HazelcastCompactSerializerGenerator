package com.ataltinok.compactserializers.generator;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ataltinok.compactserializers.discovery.TypeClassifier;

public final class TypeMapper {

	public record Snippets(String readLine, String writeLine) {
	}

	private TypeMapper() {
	}

	public static Snippets mapField(Field field) {
		String fieldName = field.getName();
		String key = NameHelpers.fieldConstantName(fieldName);
		String getter = NameHelpers.getterName(fieldName) + "()";
		String setter = NameHelpers.setterName(fieldName);
		Class<?> raw = field.getType();
		Type generic = field.getGenericType();
		return map(raw, generic, key, setter, getter);
	}

	static Snippets map(Class<?> raw, Type generic, String key, String setter, String getter) {
		// primitives & wrappers
		String prim = primitiveReadWrite(raw);
		if (prim != null) {
			String[] rw = prim.split("\\|");
			return new Snippets("obj." + setter + "(reader." + rw[0] + "(" + key + "));", "writer." + rw[1] + "(" + key + ", obj." + getter + ");");
		}
		// arrays
		if (raw.isArray()) {
			Class<?> comp = raw.getComponentType();
			String arr = arrayReadWrite(comp);
			if (arr != null) {
				String[] rw = arr.split("\\|");
				return new Snippets("obj." + setter + "(reader." + rw[0] + "(" + key + "));", "writer." + rw[1] + "(" + key + ", obj." + getter + ");");
			}
			// array of custom / enum — not directly supported by util; leave TODO
			return new Snippets("// TODO array of " + comp.getSimpleName() + "; use reader.readArrayOfCompact(" + key + ", " + comp.getSimpleName() + ".class)",
					"// TODO array of " + comp.getSimpleName() + "; use writer.writeArrayOfCompact(" + key + ", obj." + getter + ")");
		}
		// native date-ish
		if (raw == java.time.LocalDate.class) {
			return new Snippets("obj." + setter + "(reader.readDate(" + key + "));", "writer.writeDate(" + key + ", obj." + getter + ");");
		}
		if (raw == java.time.OffsetDateTime.class) {
			return new Snippets("obj." + setter + "(reader.readTimestampWithTimezone(" + key + "));",
					"writer.writeTimestampWithTimezone(" + key + ", obj." + getter + ");");
		}
		if (raw == java.time.LocalDateTime.class) {
			return new Snippets("obj." + setter + "(reader.readTimestamp(" + key + "));", "writer.writeTimestamp(" + key + ", obj." + getter + ");");
		}
		if (raw == java.time.LocalTime.class) {
			return new Snippets("obj." + setter + "(reader.readTime(" + key + "));", "writer.writeTime(" + key + ", obj." + getter + ");");
		}
		if (raw == java.math.BigDecimal.class) {
			return new Snippets("obj." + setter + "(reader.readDecimal(" + key + "));", "writer.writeDecimal(" + key + ", obj." + getter + ");");
		}
		if (raw == UUID.class) {
			return new Snippets("obj." + setter + "(CompactSerializerUtil.readUUID(reader, " + key + "));",
					"CompactSerializerUtil.writeUUID(writer, " + key + ", obj." + getter + ");");
		}
		if (raw == Date.class) {
			return new Snippets("obj." + setter + "(CompactSerializerUtil.readDate(reader, " + key + "));",
					"CompactSerializerUtil.writeDate(writer, " + key + ", obj." + getter + ");");
		}
		if (raw == java.time.Instant.class) {
			return new Snippets("obj." + setter + "(CompactSerializerUtil.readInstant(reader, " + key + "));",
					"CompactSerializerUtil.writeInstant(writer, " + key + ", obj." + getter + ");");
		}
		if (raw.isEnum()) {
			String en = raw.getSimpleName();
			return new Snippets("obj." + setter + "(CompactSerializerUtil.readEnum(reader, " + key + ", " + en + ".class));",
					"CompactSerializerUtil.writeEnum(writer, " + key + ", obj." + getter + ");");
		}
		TypeClassifier.Kind kind = TypeClassifier.classify(raw);
		if (kind == TypeClassifier.Kind.COLLECTION) {
			Class<?> elem = firstTypeArg(generic);
			String elemName = simpleName(elem);
			String helper = Map.class.isAssignableFrom(raw) ? "readMap" : (Set.class.isAssignableFrom(raw) ? "readSet" : "readList");
			return new Snippets("obj." + setter + "(CompactSerializerUtil." + helper + "(reader, " + key + ", " + elemName + ".class));",
					"CompactSerializerUtil.writeCollection(writer, " + key + ", obj." + getter + ", " + elemName + ".class);");
		}
		if (kind == TypeClassifier.Kind.MAP) {
			return mapMapField(generic, key, setter, getter);
		}
		// custom fallback -> Hazelcast native compact
		return new Snippets("obj." + setter + "(reader.readCompact(" + key + "));", "writer.writeCompact(" + key + ", obj." + getter + ");");
	}

	private static Snippets mapMapField(Type generic, String key, String setter, String getter) {
		if (!(generic instanceof ParameterizedType pt)) {
			return new Snippets("obj." + setter + "(CompactSerializerUtil.readMap(reader, " + key + ", Object.class, Object.class));",
					"CompactSerializerUtil.writeMap(writer, " + key + ", obj." + getter + ", Object.class, Object.class);");
		}
		Type[] args = pt.getActualTypeArguments();
		Class<?> keyCls = rawOf(args[0]);
		Type valType = args[1];
		Class<?> valCls = rawOf(valType);
		String keyName = simpleName(keyCls);
		String valName = simpleName(valCls);

		// Map<UUID, V>
		if (keyCls == UUID.class) {
			return new Snippets("obj." + setter + "(CompactSerializerUtil.readUUIDMap(reader, " + key + ", " + valName + ".class));",
					"CompactSerializerUtil.writeUUIDMap(writer, " + key + ", obj." + getter + ", " + valName + ".class);");
		}
		// Map<K, Date>
		if (valCls == Date.class) {
			return new Snippets("obj." + setter + "(CompactSerializerUtil.readMapOfDate(reader, " + key + ", " + keyName + ".class));",
					"CompactSerializerUtil.writeMapOfDate(writer, " + key + ", obj." + getter + ", " + keyName + ".class);");
		}
		// Map<K, List<V>>
		if (valType instanceof ParameterizedType vpt && List.class.isAssignableFrom(rawOf(vpt))) {
			Class<?> listElem = rawOf(vpt.getActualTypeArguments()[0]);
			String le = simpleName(listElem);
			return new Snippets("obj." + setter + "(CompactSerializerUtil.readMapOfList(reader, " + key + ", " + keyName + ".class, " + le + ".class));",
					"CompactSerializerUtil.writeMapOfList(writer, " + key + ", obj." + getter + ", " + keyName + ".class, " + le + ".class);");
		}
		// Map<String, Map<String, V>>
		if (valType instanceof ParameterizedType vpt2 && Map.class.isAssignableFrom(rawOf(vpt2))) {
			Type[] inner = vpt2.getActualTypeArguments();
			Class<?> innerVal = rawOf(inner[1]);
			String iv = simpleName(innerVal);
			return new Snippets("obj." + setter + "(CompactSerializerUtil.readMapOfMap(reader, " + key + ", " + iv + ".class));",
					"CompactSerializerUtil.writeMapOfMap(writer, " + key + ", obj." + getter + ", " + iv + ".class);");
		}
		// Map<K,V> plain
		return new Snippets("obj." + setter + "(CompactSerializerUtil.readMap(reader, " + key + ", " + keyName + ".class, " + valName + ".class));",
				"CompactSerializerUtil.writeMap(writer, " + key + ", obj." + getter + ", " + keyName + ".class, " + valName + ".class);");
	}

	private static Class<?> firstTypeArg(Type generic) {
		if (generic instanceof ParameterizedType pt) {
			return rawOf(pt.getActualTypeArguments()[0]);
		}
		return Object.class;
	}

	static Class<?> rawOf(Type t) {
		if (t instanceof Class<?> c)
			return c;
		if (t instanceof ParameterizedType pt)
			return (Class<?>) pt.getRawType();
		return Object.class;
	}

	private static String simpleName(Class<?> c) {
		return c == null ? "Object" : c.getSimpleName();
	}

	private static String primitiveReadWrite(Class<?> c) {
		if (c == byte.class)
			return "readInt8|writeInt8";
		if (c == short.class)
			return "readInt16|writeInt16";
		if (c == int.class)
			return "readInt32|writeInt32";
		if (c == long.class)
			return "readInt64|writeInt64";
		if (c == float.class)
			return "readFloat32|writeFloat32";
		if (c == double.class)
			return "readFloat64|writeFloat64";
		if (c == boolean.class)
			return "readBoolean|writeBoolean";
		if (c == Byte.class)
			return "readNullableInt8|writeNullableInt8";
		if (c == Short.class)
			return "readNullableInt16|writeNullableInt16";
		if (c == Integer.class)
			return "readNullableInt32|writeNullableInt32";
		if (c == Long.class)
			return "readNullableInt64|writeNullableInt64";
		if (c == Float.class)
			return "readNullableFloat32|writeNullableFloat32";
		if (c == Double.class)
			return "readNullableFloat64|writeNullableFloat64";
		if (c == Boolean.class)
			return "readNullableBoolean|writeNullableBoolean";
		if (c == String.class)
			return "readString|writeString";
		return null;
	}

	private static String arrayReadWrite(Class<?> comp) {
		if (comp == byte.class)
			return "readArrayOfInt8|writeArrayOfInt8";
		if (comp == short.class)
			return "readArrayOfInt16|writeArrayOfInt16";
		if (comp == int.class)
			return "readArrayOfInt32|writeArrayOfInt32";
		if (comp == long.class)
			return "readArrayOfInt64|writeArrayOfInt64";
		if (comp == float.class)
			return "readArrayOfFloat32|writeArrayOfFloat32";
		if (comp == double.class)
			return "readArrayOfFloat64|writeArrayOfFloat64";
		if (comp == boolean.class)
			return "readArrayOfBoolean|writeArrayOfBoolean";
		if (comp == Byte.class)
			return "readArrayOfNullableInt8|writeArrayOfNullableInt8";
		if (comp == Short.class)
			return "readArrayOfNullableInt16|writeArrayOfNullableInt16";
		if (comp == Integer.class)
			return "readArrayOfNullableInt32|writeArrayOfNullableInt32";
		if (comp == Long.class)
			return "readArrayOfNullableInt64|writeArrayOfNullableInt64";
		if (comp == Float.class)
			return "readArrayOfNullableFloat32|writeArrayOfNullableFloat32";
		if (comp == Double.class)
			return "readArrayOfNullableFloat64|writeArrayOfNullableFloat64";
		if (comp == Boolean.class)
			return "readArrayOfNullableBoolean|writeArrayOfNullableBoolean";
		if (comp == String.class)
			return "readArrayOfString|writeArrayOfString";
		if (comp == java.time.LocalDate.class)
			return "readArrayOfDate|writeArrayOfDate";
		if (comp == java.time.OffsetDateTime.class)
			return "readArrayOfTimestampWithTimezone|writeArrayOfTimestampWithTimezone";
		return null;
	}
}
