package com.ataltinok.compactserializers.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ataltinok.compactserializers.discovery.TypeClassifier;

class TypeClassifierTest {

	enum Color {
		RED, GREEN
	}

	static class Custom {
	}

	@Test
	void primitivesAreBuiltin() {
		assertThat(TypeClassifier.classify(int.class)).isEqualTo(TypeClassifier.Kind.BUILTIN);
		assertThat(TypeClassifier.classify(long.class)).isEqualTo(TypeClassifier.Kind.BUILTIN);
	}

	@Test
	void wrappersAreBuiltin() {
		assertThat(TypeClassifier.classify(Integer.class)).isEqualTo(TypeClassifier.Kind.BUILTIN);
		assertThat(TypeClassifier.classify(String.class)).isEqualTo(TypeClassifier.Kind.BUILTIN);
	}

	@Test
	void collectionsClassified() {
		assertThat(TypeClassifier.classify(List.class)).isEqualTo(TypeClassifier.Kind.COLLECTION);
		assertThat(TypeClassifier.classify(Set.class)).isEqualTo(TypeClassifier.Kind.COLLECTION);
		assertThat(TypeClassifier.classify(ArrayList.class)).isEqualTo(TypeClassifier.Kind.COLLECTION);
	}

	@Test
	void mapsClassified() {
		assertThat(TypeClassifier.classify(Map.class)).isEqualTo(TypeClassifier.Kind.MAP);
		assertThat(TypeClassifier.classify(HashMap.class)).isEqualTo(TypeClassifier.Kind.MAP);
	}

	@Test
	void arraysAndEnums() {
		assertThat(TypeClassifier.classify(int[].class)).isEqualTo(TypeClassifier.Kind.ARRAY);
		assertThat(TypeClassifier.classify(Color.class)).isEqualTo(TypeClassifier.Kind.ENUM);
	}

	@Test
	void customFallback() {
		assertThat(TypeClassifier.classify(Custom.class)).isEqualTo(TypeClassifier.Kind.CUSTOM);
	}
}
