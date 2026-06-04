package com.ataltinok.compactserializers.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.ataltinok.compactserializers.generator.SerializerEmitter;

class SerializerEmitterTest {

	@Test
	void goldenMatch() throws IOException {
		String actual = SerializerEmitter.emit(SerializerEmitterFixture.class, "com.example.gen");
		String expected = new String(
				Objects.requireNonNull(getClass().getResourceAsStream("/golden/SerializerEmitterFixtureSerializer.java.txt")).readAllBytes(),
				StandardCharsets.UTF_8);
		assertThat(normalize(actual)).isEqualTo(normalize(expected));
	}

	@Test
	void defaultPackageIsTodoPlaceholder() {
		String actual = SerializerEmitter.emit(SerializerEmitterFixture.class, null);
		assertThat(actual).startsWith("package " + SerializerEmitter.TODO_PACKAGE + ";");
	}

	@Test
	void emptyPackageStringAlsoFallsBack() {
		String actual = SerializerEmitter.emit(SerializerEmitterFixture.class, "  ");
		assertThat(actual).startsWith("package " + SerializerEmitter.TODO_PACKAGE + ";");
	}

	private static String normalize(String s) {
		return s.replace("\r\n", "\n").replaceAll("[ \\t]+", " ").replaceAll("\\n+", "\n").trim();
	}
}
