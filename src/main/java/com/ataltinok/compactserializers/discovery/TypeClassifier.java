package com.ataltinok.compactserializers.discovery;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public final class TypeClassifier {

	public enum Kind {
		BUILTIN, COLLECTION, MAP, ARRAY, ENUM, CUSTOM
	}

	private static final Set<Class<?>> BUILTIN = Set.of(String.class, UUID.class, Date.class, LocalDate.class, LocalDateTime.class, LocalTime.class,
			OffsetDateTime.class, Instant.class, BigDecimal.class, BigInteger.class, Object.class, Optional.class, Integer.class, Long.class, Short.class,
			Byte.class, Double.class, Float.class, Boolean.class, Character.class);

	private TypeClassifier() {
	}

	public static Kind classify(Class<?> c) {
		if (c == null)
			return Kind.CUSTOM;
		if (c.isPrimitive())
			return Kind.BUILTIN;
		if (BUILTIN.contains(c))
			return Kind.BUILTIN;
		if (c.isArray())
			return Kind.ARRAY;
		if (c.isEnum())
			return Kind.ENUM;
		if (Map.class.isAssignableFrom(c))
			return Kind.MAP;
		if (Collection.class.isAssignableFrom(c) || Iterable.class.isAssignableFrom(c) || List.class.isAssignableFrom(c) || Set.class.isAssignableFrom(c)
				|| Deque.class.isAssignableFrom(c) || Queue.class.isAssignableFrom(c)) {
			return Kind.COLLECTION;
		}
		return Kind.CUSTOM;
	}
}
