package com.ataltinok.compactserializers.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import com.ataltinok.compactserializers.generator.TypeMapper;

class TypeMapperTest {

	private Field f(String name) throws NoSuchFieldException {
		return TypeMapperFixture.class.getDeclaredField(name);
	}

	@Test
	void primitiveInt() throws Exception {
		var s = TypeMapper.mapField(f("primInt"));
		assertThat(s.readLine()).isEqualTo("obj.setPrimInt(reader.readInt32(FIELD_PRIM_INT));");
		assertThat(s.writeLine()).isEqualTo("writer.writeInt32(FIELD_PRIM_INT, obj.getPrimInt());");
	}

	@Test
	void boxedIntNullable() throws Exception {
		var s = TypeMapper.mapField(f("boxedInt"));
		assertThat(s.readLine()).contains("readNullableInt32");
		assertThat(s.writeLine()).contains("writeNullableInt32");
	}

	@Test
	void stringAndStringArray() throws Exception {
		assertThat(TypeMapper.mapField(f("text")).readLine()).contains("reader.readString(FIELD_TEXT)");
		assertThat(TypeMapper.mapField(f("textArr")).readLine()).contains("readArrayOfString");
	}

	@Test
	void primitiveArray() throws Exception {
		assertThat(TypeMapper.mapField(f("intArr")).readLine()).contains("readArrayOfInt32");
	}

	@Test
	void localDateUsesNativeReader() throws Exception {
		var s = TypeMapper.mapField(f("localDate"));
		assertThat(s.readLine()).contains("reader.readDate(FIELD_LOCAL_DATE)");
		assertThat(s.writeLine()).contains("writer.writeDate");
	}

	@Test
	void offsetDateTime() throws Exception {
		assertThat(TypeMapper.mapField(f("odt")).readLine()).contains("reader.readTimestampWithTimezone");
	}

	@Test
	void uuidUsesUtil() throws Exception {
		var s = TypeMapper.mapField(f("uuid"));
		assertThat(s.readLine()).contains("CompactSerializerUtil.readUUID(reader, FIELD_UUID)");
		assertThat(s.writeLine()).contains("CompactSerializerUtil.writeUUID(writer, FIELD_UUID");
	}

	@Test
	void dateUsesUtil() throws Exception {
		var s = TypeMapper.mapField(f("date"));
		assertThat(s.readLine()).contains("CompactSerializerUtil.readDate");
	}

	@Test
	void enumUsesUtil() throws Exception {
		var s = TypeMapper.mapField(f("color"));
		assertThat(s.readLine()).contains("CompactSerializerUtil.readEnum(reader, FIELD_COLOR, Color.class)");
		assertThat(s.writeLine()).contains("CompactSerializerUtil.writeEnum(writer, FIELD_COLOR");
	}

	@Test
	void listAndSet() throws Exception {
		assertThat(TypeMapper.mapField(f("strList")).readLine())
				.isEqualTo("obj.setStrList(CompactSerializerUtil.readList(reader, FIELD_STR_LIST, String.class));");
		assertThat(TypeMapper.mapField(f("uuidSet")).readLine())
				.isEqualTo("obj.setUuidSet(CompactSerializerUtil.readSet(reader, FIELD_UUID_SET, UUID.class));");
	}

	@Test
	void collectionWrite() throws Exception {
		assertThat(TypeMapper.mapField(f("strList")).writeLine())
				.isEqualTo("CompactSerializerUtil.writeCollection(writer, FIELD_STR_LIST, obj.getStrList(), String.class);");
	}

	@Test
	void plainMap() throws Exception {
		assertThat(TypeMapper.mapField(f("plainMap")).readLine())
				.isEqualTo("obj.setPlainMap(CompactSerializerUtil.readMap(reader, FIELD_PLAIN_MAP, String.class, Nested.class));");
	}

	@Test
	void mapOfList() throws Exception {
		assertThat(TypeMapper.mapField(f("mapOfList")).readLine()).contains("readMapOfList(reader, FIELD_MAP_OF_LIST, String.class, Nested.class)");
	}

	@Test
	void mapOfDate() throws Exception {
		assertThat(TypeMapper.mapField(f("mapOfDate")).readLine()).contains("readMapOfDate(reader, FIELD_MAP_OF_DATE, String.class)");
	}

	@Test
	void uuidMap() throws Exception {
		assertThat(TypeMapper.mapField(f("uuidMap")).readLine()).contains("readUUIDMap(reader, FIELD_UUID_MAP, Nested.class)");
	}

	@Test
	void mapOfMap() throws Exception {
		assertThat(TypeMapper.mapField(f("mapOfMap")).readLine()).contains("readMapOfMap(reader, FIELD_MAP_OF_MAP, Nested.class)");
	}

	@Test
	void customFallbackReadsCompact() throws Exception {
		var s = TypeMapper.mapField(f("nested"));
		assertThat(s.readLine()).isEqualTo("obj.setNested(reader.readCompact(FIELD_NESTED));");
		assertThat(s.writeLine()).isEqualTo("writer.writeCompact(FIELD_NESTED, obj.getNested());");
	}

	@Test
	void customArrayEmitsTodo() throws Exception {
		var s = TypeMapper.mapField(f("nestedArr"));
		assertThat(s.readLine()).startsWith("// TODO array of Nested");
	}
}
