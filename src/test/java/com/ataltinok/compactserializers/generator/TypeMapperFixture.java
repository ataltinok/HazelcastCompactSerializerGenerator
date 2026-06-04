package com.ataltinok.compactserializers.generator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("unused")
public class TypeMapperFixture {
	public enum Color {
		RED, GREEN
	}

	public static class Nested {
	}

	private int primInt;
	private Integer boxedInt;
	private String text;
	private String[] textArr;
	private int[] intArr;
	private LocalDate localDate;
	private OffsetDateTime odt;
	private UUID uuid;
	private Date date;
	private Color color;
	private List<String> strList;
	private Set<UUID> uuidSet;
	private Map<String, Nested> plainMap;
	private Map<String, List<Nested>> mapOfList;
	private Map<String, Date> mapOfDate;
	private Map<UUID, Nested> uuidMap;
	private Map<String, Map<String, Nested>> mapOfMap;
	private Nested nested;
	private Nested[] nestedArr;
}
