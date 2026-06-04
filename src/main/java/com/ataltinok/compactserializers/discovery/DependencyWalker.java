package com.ataltinok.compactserializers.discovery;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DependencyWalker {

	private static final Logger log = LoggerFactory.getLogger(DependencyWalker.class);

	private DependencyWalker() {
	}

	/**
	 * BFS from root, following all private non-static fields. Returns classes in discovery order, starting with root itself.
	 * Collection/array types are unwrapped; element types are enqueued. Builtins, enums and collection raw types are never
	 * enqueued.
	 */
	public static List<Class<?>> walk(Class<?> root) {
		log.info("Starting dependency walk from {}", root.getName());
		List<Class<?>> ordered = new ArrayList<>();
		Set<Class<?>> visited = new LinkedHashSet<>();
		Deque<Class<?>> queue = new ArrayDeque<>();
		queue.add(root);
		while (!queue.isEmpty()) {
			Class<?> c = queue.poll();
			if (!visited.add(c))
				continue;
			ordered.add(c);
			log.debug("Visiting {}", c.getSimpleName());
			for (Field f : c.getDeclaredFields()) {
				if (!isSerializableField(f))
					continue;
				log.debug("  field: {} {}", f.getGenericType().getTypeName(), f.getName());
				for (Class<?> leaf : leafTypes(f.getGenericType())) {
					if (shouldEnqueue(leaf)) {
						log.debug("    Enqueuing {}", leaf.getSimpleName());
						queue.add(leaf);
					}
				}
			}
		}
		log.info("Walk complete: {} classes discovered", ordered.size());
		return ordered;
	}

	static boolean isSerializableField(Field f) {
		int mods = f.getModifiers();
		return Modifier.isPrivate(mods) && !Modifier.isStatic(mods);
	}

	static boolean shouldEnqueue(Class<?> c) {
		if (c == null)
			return false;
		TypeClassifier.Kind k = TypeClassifier.classify(c);
		return k == TypeClassifier.Kind.CUSTOM;
	}

	static List<Class<?>> leafTypes(Type t) {
		List<Class<?>> out = new ArrayList<>();
		collectLeafTypes(t, out);
		return out;
	}

	private static void collectLeafTypes(Type t, List<Class<?>> out) {
		if (t instanceof Class<?> c) {
			if (c.isArray())
				collectLeafTypes(c.getComponentType(), out);
			else
				out.add(c);
		} else if (t instanceof ParameterizedType pt) {
			for (Type arg : pt.getActualTypeArguments())
				collectLeafTypes(arg, out);
		} else if (t instanceof GenericArrayType gat) {
			collectLeafTypes(gat.getGenericComponentType(), out);
		} else if (t instanceof WildcardType wt) {
			for (Type b : wt.getUpperBounds())
				collectLeafTypes(b, out);
		} else if (t instanceof TypeVariable<?>) {
			// unresolved — skip
		}
	}
}
