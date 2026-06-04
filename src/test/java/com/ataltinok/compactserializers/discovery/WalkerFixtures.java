package com.ataltinok.compactserializers.discovery;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class WalkerFixtures {

	public enum Status {
		OK, FAIL
	}

	public static class Leaf {
		private String name;
	}

	public static class Branch {
		private Leaf leaf;
		private Leaf leafAlt;
	}

	public static class Root {
		private Branch branch;
		private List<Leaf> leaves;
		private Map<String, Leaf> map;
		private Status status; // enum — not enqueued as custom
		private String name; // builtin — not enqueued
	}
}
