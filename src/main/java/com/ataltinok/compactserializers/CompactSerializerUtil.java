package com.ataltinok.compactserializers;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.logging.log4j.util.TriConsumer;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactWriter;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddress.IPVersion;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv6.IPv6Address;
import jakarta.validation.constraints.NotNull;

public enum CompactSerializerUtil {
	;
	private static final Map<Class<?>, TriConsumer<CompactWriter, String, Object>> ARRAY_WRITERS = new HashMap<>();
	private static final Map<Class<?>, BiFunction<CompactReader, String, ?>> ARRAY_READERS = new HashMap<>();
	private static final String IP_VERSION_SUFFIX = "_ipVersion";
	private static final String SECONDS_SUFFIX = "_seconds";
	private static final String NANOSECONDS_SUFFIX = "_nano";
	public static final String FIELD_KEYS = "keys";
	public static final String FIELD_VALS = "vals";

	@SuppressWarnings("unchecked")
	public static <T> List<T> readList(CompactReader reader, String listName, Class<T> listClazz) {
		if (listClazz.isEnum()) {
			// We know listClazz is an enum, so the type cast is safe
			@SuppressWarnings({ "rawtypes" })
			List<T> result = convertStringArrToEnumList(reader.readArrayOfString(listName), (Class<? extends Enum>) listClazz);
			return result;
		} else if (listClazz == UUID.class) {
			return (List<T>) convertStringArrToUUIDList(reader.readArrayOfString(listName));
		} else if (listClazz == Date.class) {
			return (List<T>) convertLongArrToDateList(reader.readArrayOfNullableInt64(listName));
		}

		BiFunction<CompactReader, String, ?> listReader = ARRAY_READERS.get(listClazz);
		if (listReader == null) {
			T[] objectArray = reader.readArrayOfCompact(listName, listClazz);
			if (objectArray != null) {
				return new ArrayList<>(Arrays.asList(objectArray));
			} else {
				return null;
			}
		} else {
			T[] readValue = (T[]) listReader.apply(reader, listName);
			return readValue != null ? new ArrayList<>(Arrays.asList(readValue)) : null;
		}
	}

	private static <T extends Enum<T>> List<T> convertStringArrToEnumList(String[] arr, Class<T> enumClazz) {
		if (arr == null) {
			return null;
		}

		List<T> result = new ArrayList<>();
		for (String s : arr) {
			result.add(Enum.valueOf(enumClazz, s));
		}

		return result;
	}

	private static List<UUID> convertStringArrToUUIDList(String[] arr) {
		if (arr == null) {
			return null;
		}

		List<UUID> result = new ArrayList<>();
		for (String s : arr) {
			result.add(UUID.fromString(s));
		}
		return result;
	}

	private static List<Date> convertLongArrToDateList(Long[] arr) {
		if (arr == null) {
			return null;
		}

		List<Date> result = new ArrayList<>();
		for (Long s : arr) {
			result.add(new Date(s));
		}
		return result;
	}

	public static <K, V> Map<K, V> readMap(CompactReader r, String fieldName, Class<K> clazzKey, Class<V> clazzVal, Consumer<Map<K, V>> c) {
		Map<K, V> map = readMap(r, fieldName, clazzKey, clazzVal);
		if (c != null) {
			c.accept(map);
		}

		return map;
	}

	public static <K, V> Map<K, V> readMap(CompactReader r, String fieldName, Class<K> clazzKey, Class<V> clazzVal) {
		List<K> keys = readList(r, fieldName + FIELD_KEYS, clazzKey);
		List<V> vals = readList(r, fieldName + FIELD_VALS, clazzVal);

		// if the written map was empty, keys and vals would be empty arrays
		// don't return an empty map if keys or vals is null
		if (keys == null || vals == null) {
			return null;
		}

		Map<K, V> map = new HashMap<>(keys.size());
		for (int i = 0; i < keys.size(); i++) {
			map.put(keys.get(i), vals.get(i));
		}

		return map;
	}

	public static <T> Set<T> readSet(CompactReader reader, String setName, Class<T> setClazz) {
		if (setClazz.isEnum()) {
			// We know setClazz is an enum, so the type cast is safe
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Set<T> result = convertStringArrToEnumSet(reader.readArrayOfString(setName), (Class<? extends Enum>) setClazz);
			return result;
		}

		BiFunction<CompactReader, String, ?> setReader = ARRAY_READERS.get(setClazz);
		if (setReader == null) {
			var objectArray = reader.readArrayOfCompact(setName, setClazz);
			if (objectArray != null) {
				return new HashSet<>(Arrays.asList(objectArray));
			} else {
				return null;
			}
		} else {
			Set<T> result = new HashSet<>();
			// setReader's return type must be setClazz[] (i.e. T[]), so the cast to T[] is safe
			@SuppressWarnings("unchecked")
			T[] array = (T[]) setReader.apply(reader, setName);
			if (array != null) {
				result.addAll(Arrays.asList(array));
			}
			return result;
		}
	}

	private static <T extends Enum<T>> Set<T> convertStringArrToEnumSet(String[] arr, Class<T> enumClazz) {
		if (arr == null) {
			return null;
		}

		Set<T> result = new HashSet<>();
		for (String s : arr) {
			result.add(Enum.valueOf(enumClazz, s));
		}

		return result;
	}

	public static IPAddress readIPAddress(CompactReader reader, String fieldName) {
		byte[] ipAddress = reader.readArrayOfInt8(fieldName);
		if (ipAddress == null) {
			return null;
		} else {
			final IPVersion ipVersion = readEnum(reader, fieldName + IP_VERSION_SUFFIX, IPVersion.class);
			if (ipVersion == IPVersion.IPV4) {
				return new IPv4Address(ipAddress);
			} else {
				return new IPv6Address(ipAddress);
			}
		}
	}

	public static URI readURI(CompactReader reader, String fieldName, Consumer<URI> consumer) {
		String stringURI = reader.readString(fieldName);
		URI uri = null;
		try {
			if (stringURI != null) {
				uri = new URI(stringURI);
			}

			if (consumer != null) {
				consumer.accept(uri);
			}
		} catch (URISyntaxException e) {

		}
		return uri;
	}

	public static <K, V> void writeMap(CompactWriter writer, String fieldName, Map<K, V> c, Class<K> clazzKey, Class<V> clazzVal) {
		if (c == null) {
			writeCollection(writer, fieldName + FIELD_KEYS, null, clazzKey);
			writeCollection(writer, fieldName + FIELD_VALS, null, clazzVal);
		} else {
			writeCollection(writer, fieldName + FIELD_KEYS, c.keySet(), clazzKey);
			writeCollection(writer, fieldName + FIELD_VALS, c.values(), clazzVal);
		}
	}

	// Write any collection data type as an array
	@SuppressWarnings("unchecked")
	public static <T> void writeCollection(CompactWriter writer, String fieldName, Collection<T> c, Class<T> clazz) {
		if (clazz.isEnum()) {
			final Collection<Enum<?>> enumCollection = (Collection<Enum<?>>) c;
			String[] arr = c != null ? enumCollection.stream().map(Enum::name).toArray(String[]::new) : null;
			writer.writeArrayOfString(fieldName, arr);
		} else if (clazz == UUID.class) {
			String[] arr = c != null ? c.stream().map(Object::toString).toArray(String[]::new) : null;
			writer.writeArrayOfString(fieldName, arr);
		} else if (clazz == Date.class) {
			Long[] arr = c != null ? ((Collection<Date>) c).stream().map(Date::getTime).toArray(Long[]::new) : null;
			writer.writeArrayOfNullableInt64(fieldName, arr);
		} else {
			TriConsumer<CompactWriter, String, Object> func = ARRAY_WRITERS.get(clazz);
			T[] array = null;
			if (c != null) {
				array = generateArrayFromCollection(c, clazz);
			}

			if (func != null) {
				func.accept(writer, fieldName, array);
			} else {
				writer.writeArrayOfCompact(fieldName, array);
			}
		}
	}

	public static void writeIPAddress(CompactWriter writer, String fieldName, IPAddress ipAddress) {
		if (ipAddress != null) {
			writer.writeArrayOfInt8(fieldName, ipAddress.getBytes());
			writeEnum(writer, fieldName + IP_VERSION_SUFFIX, ipAddress.getIPVersion());
		} else {
			writer.writeArrayOfInt8(fieldName, null);
			writer.writeString(fieldName + IP_VERSION_SUFFIX, null);
		}
	}

	public static void writeURI(CompactWriter writer, String fieldName, URI uri) {
		if (uri != null) {
			writer.writeString(fieldName, uri.toString());
		} else {
			writer.writeString(fieldName, null);
		}
	}

	@SuppressWarnings("unchecked")
	@NotNull
	private static <T> T[] generateArrayFromCollection(Collection<T> collection, Class<T> clazz) {
		T[] o = (T[]) Array.newInstance(clazz, collection.size());
		return collection.toArray(o);
	}

	public static <E extends Enum<E>> void writeEnum(CompactWriter writer, String fieldName, Enum<E> value) {
		if (value != null) {
			writer.writeString(fieldName, value.name());
		} else {
			writer.writeString(fieldName, null);
		}
	}

	public static <E extends Enum<E>> E readEnum(CompactReader reader, String fieldName, Class<E> enumType) {
		final String value = reader.readString(fieldName);
		if (value == null) {
			return null;
		} else {
			return Enum.valueOf(enumType, value);
		}
	}

	public static void writeInetAddress(CompactWriter writer, String fieldName, InetAddress value) {
		if (value != null) {
			writer.writeArrayOfInt8(fieldName, value.getAddress());
		} else {
			writer.writeArrayOfInt8(fieldName, null);
		}
	}

	public static InetAddress readInetAddress(CompactReader reader, String fieldName) {
		final byte[] addressBytes = reader.readArrayOfInt8(fieldName);
		if (addressBytes == null) {
			return null;
		} else {
			try {
				return InetAddress.getByAddress(addressBytes);
			} catch (UnknownHostException e) {
				// This should never happen because writeInetAddress(..) writes the bytes returned from InetAddress.getAddress()

				return null;
			}
		}
	}

	public static <K, V> BidiMap<K, V> readBidiMap(CompactReader reader, String fieldName, Class<K> clazzKey, Class<V> clazzVal) {
		List<K> keys = readList(reader, fieldName + FIELD_KEYS, clazzKey);
		List<V> vals = readList(reader, fieldName + FIELD_VALS, clazzVal);

		if (keys == null || vals == null) {
			return null;
		}

		BidiMap<K, V> bidiMap = new DualHashBidiMap<>();
		for (int i = 0; i < keys.size(); i++) {
			bidiMap.put(keys.get(i), vals.get(i));
		}

		return bidiMap;
	}

	public static <K, V> void writeBidiMap(CompactWriter writer, String fieldName, BidiMap<K, V> c, Class<K> clazzKey, Class<V> clazzVal) {
		if (c == null) {
			writeCollection(writer, fieldName + FIELD_KEYS, null, clazzKey);
			writeCollection(writer, fieldName + FIELD_VALS, null, clazzVal);
		} else {
			writeCollection(writer, fieldName + FIELD_KEYS, c.keySet(), clazzKey);
			writeCollection(writer, fieldName + FIELD_VALS, c.values(), clazzVal);
		}
	}

	public static Date readDate(CompactReader reader, String fieldName) {
		Long value = reader.readNullableInt64(fieldName);
		return value != null ? new Date(value) : null;
	}

	public static void writeDate(CompactWriter writer, String fieldName, Date value) {
		if (value != null) {
			writer.writeNullableInt64(fieldName, value.getTime());
		} else {
			writer.writeNullableInt64(fieldName, null);
		}
	}

	public static Instant readInstant(CompactReader reader, String fieldName) {
		Long seconds = reader.readNullableInt64(fieldName + SECONDS_SUFFIX);
		int nanoSeconds = reader.readInt32(fieldName + NANOSECONDS_SUFFIX);
		return seconds != null ? Instant.ofEpochSecond(seconds, nanoSeconds) : null;
	}

	public static void writeInstant(CompactWriter writer, String fieldName, Instant value) {
		if (value != null) {
			writer.writeNullableInt64(fieldName + SECONDS_SUFFIX, value.getEpochSecond());
			writer.writeInt32(fieldName + NANOSECONDS_SUFFIX, value.getNano());
		} else {
			writer.writeNullableInt64(fieldName + SECONDS_SUFFIX, null);
			writer.writeInt32(fieldName + NANOSECONDS_SUFFIX, 0);
		}
	}

	public static UUID readUUID(CompactReader reader, String fieldName) {
		String value = reader.readString(fieldName);
		return value != null ? UUID.fromString(value) : null;
	}

	public static void writeUUID(CompactWriter writer, String fieldName, UUID value) {
		if (value != null) {
			writer.writeString(fieldName, value.toString());
		} else {
			writer.writeString(fieldName, null);
		}
	}

	public static <K> Map<K, Date> readMapOfDate(@NotNull CompactReader reader, String fieldName, Class<K> clazzKey) {
		Map<K, Long> map = readMap(reader, fieldName, clazzKey, Long.class);
		// If the map didn't exist before, it must have been null. Don't return an empty map.
		if (map == null) {
			return null;
		}
		HashMap<K, Date> result = new HashMap<>();
		for (var entry : map.entrySet()) {
			result.put(entry.getKey(), new Date(entry.getValue()));
		}
		return result;
	}

	public static <K> void writeMapOfDate(@NotNull CompactWriter writer, String fieldName, Map<K, Date> c, Class<K> clazzKey) {
		if (c == null) {
			writeCollection(writer, fieldName + FIELD_KEYS, null, clazzKey);
			writeCollection(writer, fieldName + FIELD_VALS, null, Long.class);
		} else {
			@NotNull
			List<Long> timeList = c.values().stream().map(Date::getTime).toList();
			writeCollection(writer, fieldName + FIELD_KEYS, c.keySet(), clazzKey);
			writeCollection(writer, fieldName + FIELD_VALS, timeList, Long.class);
		}
	}

	public static <V> Map<UUID, V> readUUIDMap(@NotNull CompactReader reader, String fieldName, Class<V> clazzVal) {
		Map<String, V> map = readMap(reader, fieldName, String.class, clazzVal);
		// If the map didn't exist before, it must have been null. Don't return an empty map.
		if (map == null) {
			return null;
		}
		HashMap<UUID, V> result = new HashMap<>();
		for (var entry : map.entrySet()) {
			result.put(UUID.fromString(entry.getKey()), entry.getValue());
		}
		return result;
	}

	public static <V> void writeUUIDMap(@NotNull CompactWriter writer, String fieldName, Map<UUID, V> c, Class<V> clazzVal) {
		if (c == null) {
			writeCollection(writer, fieldName + FIELD_KEYS, null, String.class);
			writeCollection(writer, fieldName + FIELD_VALS, null, clazzVal);
		} else {
			@NotNull
			List<String> uuidStrList = c.keySet().stream().map(UUID::toString).toList();
			writeCollection(writer, fieldName + FIELD_KEYS, uuidStrList, String.class);
			writeCollection(writer, fieldName + FIELD_VALS, c.values(), clazzVal);
		}
	}

	public static <E extends BaseMapOfMapEntry<O, I, V>, O, I, V> Map<O, Map<I, V>> readMapOfMap(CompactReader reader, String fieldName, Class<E> entryClazz) {
		E[] entryArr = reader.readArrayOfCompact(fieldName, entryClazz);
		if (entryArr == null) {
			return null;
		}

		Map<O, Map<I, V>> result = new HashMap<>();
		for (E entry : entryArr) {
			O outerKey = entry.getOuterKey();
			I innerKey = entry.getInnerKey();
			V value = entry.getValue();
			result.computeIfAbsent(outerKey, k -> new HashMap<>());
			result.get(outerKey).put(innerKey, value);
		}

		return result;
	}

	public static <O, I, V, E extends BaseMapOfMapEntry<O, I, V>> E[] getFlattenedMapOfMapArray(Map<O, Map<I, V>> nestedMap,
			TriFunction<O, I, V, E> constructor, IntFunction<E[]> arrGenerator) {
		if (nestedMap == null) {
			return null;
		}

		List<E> entries = new ArrayList<>();
		for (var outerEntry : nestedMap.entrySet()) {
			O outerKey = outerEntry.getKey();
			Map<I, V> map = outerEntry.getValue();
			for (var innerEntry : map.entrySet()) {
				I innerKey = innerEntry.getKey();
				V value = innerEntry.getValue();
				entries.add(constructor.apply(outerKey, innerKey, value));
			}
		}

		return entries.toArray(arrGenerator);
	}

	// Assumes values of the lists are written in the order they appear in the lists
	public static <E extends BaseMapOfListEntry<K, I>, K, I> Map<K, List<I>> readMapOfList(CompactReader reader, String fieldName, Class<E> entryClazz) {
		E[] entryArr = reader.readArrayOfCompact(fieldName, entryClazz);
		if (entryArr == null) {
			return null;
		}

		Map<K, List<I>> result = new HashMap<>();
		for (E entry : entryArr) {
			K key = entry.getKey();
			I item = entry.getItem();
			result.computeIfAbsent(key, k -> new ArrayList<>());
			result.get(key).add(item);
		}

		return result;
	}

	public static <K, I, E extends BaseMapOfListEntry<K, I>> E[] getFlattenedMapOfListArray(Map<K, List<I>> nestedMap, BiFunction<K, I, E> constructor,
			IntFunction<E[]> arrGenerator) {
		if (nestedMap == null) {
			return null;
		}

		return nestedMap.entrySet().stream().mapMulti((entry, consumer) -> {
			K key = entry.getKey();
			for (I item : entry.getValue()) {
				consumer.accept(constructor.apply(key, item));
			}
		}).toArray(arrGenerator);
	}

	@FunctionalInterface
	public interface TriFunction<T, U, V, R> {
		R apply(T t, U u, V v);
	}

	public abstract static class BaseMapOfMapEntry<O, I, V> {
		private final O outerKey;
		private final I innerKey;
		private final V value;

		protected static final String FIELD_OUTER_KEY = "outerKey";
		protected static final String FIELD_INNER_KEY = "innerKey";
		protected static final String FIELD_VALUE = "value";

		protected BaseMapOfMapEntry(O outerKey, I innerKey, V value) {
			this.outerKey = outerKey;
			this.innerKey = innerKey;
			this.value = value;
		}

		public O getOuterKey() {
			return outerKey;
		}

		public I getInnerKey() {
			return innerKey;
		}

		public V getValue() {
			return value;
		}
	}

	public abstract static class BaseMapOfListEntry<K, I> {
		private final K key;
		private final I item;

		protected static final String FIELD_KEY = "key";
		protected static final String FIELD_ITEM = "item";

		protected BaseMapOfListEntry(K key, I item) {
			this.key = key;
			this.item = item;
		}

		public K getKey() {
			return key;
		}

		public I getItem() {
			return item;
		}
	}

	// to write a byte[] call w.writeArrayOfInt8(f, (byte[]) v) -> write the value 'v' on the fieldname 'f' using writer 'w'
	static {
		ARRAY_WRITERS.put(Byte.TYPE, (w, f, v) -> w.writeArrayOfInt8(f, (byte[]) v));
		ARRAY_WRITERS.put(Character.TYPE, (w, f, v) -> w.writeArrayOfInt16(f, (short[]) v));
		ARRAY_WRITERS.put(Short.TYPE, (w, f, v) -> w.writeArrayOfInt16(f, (short[]) v));
		ARRAY_WRITERS.put(Integer.TYPE, (w, f, v) -> w.writeArrayOfInt32(f, (int[]) v));
		ARRAY_WRITERS.put(Long.TYPE, (w, f, v) -> w.writeArrayOfInt64(f, (long[]) v));
		ARRAY_WRITERS.put(Float.TYPE, (w, f, v) -> w.writeArrayOfFloat32(f, (float[]) v));
		ARRAY_WRITERS.put(Double.TYPE, (w, f, v) -> w.writeArrayOfFloat64(f, (double[]) v));
		ARRAY_WRITERS.put(Boolean.TYPE, (w, f, v) -> w.writeArrayOfBoolean(f, (boolean[]) v));
		ARRAY_WRITERS.put(String.class, (w, f, v) -> w.writeArrayOfString(f, (String[]) v));
		ARRAY_WRITERS.put(BigDecimal.class, (w, f, v) -> w.writeArrayOfDecimal(f, (BigDecimal[]) v));
		ARRAY_WRITERS.put(LocalTime.class, (w, f, v) -> w.writeArrayOfTime(f, (LocalTime[]) v));
		ARRAY_WRITERS.put(LocalDate.class, (w, f, v) -> w.writeArrayOfDate(f, (LocalDate[]) v));
		ARRAY_WRITERS.put(LocalDateTime.class, (w, f, v) -> w.writeArrayOfTimestamp(f, (LocalDateTime[]) v));
		ARRAY_WRITERS.put(OffsetDateTime.class, (w, f, v) -> w.writeArrayOfTimestampWithTimezone(f, (OffsetDateTime[]) v));
		ARRAY_WRITERS.put(Boolean.class, (w, f, v) -> w.writeArrayOfNullableBoolean(f, (Boolean[]) v));
		ARRAY_WRITERS.put(Byte.class, (w, f, v) -> w.writeArrayOfNullableInt8(f, (Byte[]) v));
		ARRAY_WRITERS.put(Character.class, (w, f, v) -> w.writeArrayOfNullableInt16(f, (Short[]) v));
		ARRAY_WRITERS.put(Short.class, (w, f, v) -> w.writeArrayOfNullableInt16(f, (Short[]) v));
		ARRAY_WRITERS.put(Integer.class, (w, f, v) -> w.writeArrayOfNullableInt32(f, (Integer[]) v));
		ARRAY_WRITERS.put(Long.class, (w, f, v) -> w.writeArrayOfNullableInt64(f, (Long[]) v));
		ARRAY_WRITERS.put(Float.class, (w, f, v) -> w.writeArrayOfNullableFloat32(f, (Float[]) v));
		ARRAY_WRITERS.put(Double.class, (w, f, v) -> w.writeArrayOfNullableFloat64(f, (Double[]) v));

		ARRAY_READERS.put(Byte.TYPE, CompactReader::readArrayOfInt8);
		ARRAY_READERS.put(Character.TYPE, CompactReader::readArrayOfInt16);
		ARRAY_READERS.put(Short.TYPE, CompactReader::readArrayOfInt16);
		ARRAY_READERS.put(Integer.TYPE, CompactReader::readArrayOfInt32);
		ARRAY_READERS.put(Long.TYPE, CompactReader::readArrayOfInt64);
		ARRAY_READERS.put(Float.TYPE, CompactReader::readArrayOfFloat32);
		ARRAY_READERS.put(Double.TYPE, CompactReader::readArrayOfFloat64);
		ARRAY_READERS.put(Boolean.TYPE, CompactReader::readArrayOfBoolean);
		ARRAY_READERS.put(String.class, CompactReader::readArrayOfString);
		ARRAY_READERS.put(BigDecimal.class, CompactReader::readArrayOfDecimal);
		ARRAY_READERS.put(LocalTime.class, CompactReader::readArrayOfTime);
		ARRAY_READERS.put(LocalDate.class, CompactReader::readArrayOfDate);
		ARRAY_READERS.put(LocalDateTime.class, CompactReader::readArrayOfTimestamp);
		ARRAY_READERS.put(OffsetDateTime.class, CompactReader::readArrayOfTimestampWithTimezone);
		ARRAY_READERS.put(Boolean.class, CompactReader::readArrayOfNullableBoolean);
		ARRAY_READERS.put(Byte.class, CompactReader::readArrayOfNullableInt8);
		ARRAY_READERS.put(Character.class, CompactReader::readArrayOfNullableInt16);
		ARRAY_READERS.put(Short.class, CompactReader::readArrayOfNullableInt16);
		ARRAY_READERS.put(Integer.class, CompactReader::readArrayOfNullableInt32);
		ARRAY_READERS.put(Long.class, CompactReader::readArrayOfNullableInt64);
		ARRAY_READERS.put(Float.class, CompactReader::readArrayOfNullableFloat32);
		ARRAY_READERS.put(Double.class, CompactReader::readArrayOfNullableFloat64);
	}
}
