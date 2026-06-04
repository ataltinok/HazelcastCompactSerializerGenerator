package com.ataltinok.compactserializers.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DependencyWalkerTest {

	@Test
	void walkCollectsTransitivePrivateFields() {
		List<Class<?>> order = DependencyWalker.walk(WalkerFixtures.Root.class);

		assertThat(order).containsExactlyInAnyOrder(WalkerFixtures.Root.class, WalkerFixtures.Branch.class, WalkerFixtures.Leaf.class);
	}

	@Test
	void firstEntryIsAlwaysRoot() {
		List<Class<?>> order = DependencyWalker.walk(WalkerFixtures.Root.class);
		assertThat(order).first().isEqualTo(WalkerFixtures.Root.class);
	}

	@Test
	void enumsAndBuiltinsSkipped() {
		List<Class<?>> order = DependencyWalker.walk(WalkerFixtures.Root.class);
		assertThat(order).doesNotContain(WalkerFixtures.Status.class, String.class);
	}
}
