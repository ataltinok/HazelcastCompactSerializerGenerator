package com.ataltinok.compactserializers.generator;

public final class NameHelpers {

	private NameHelpers() {
	}

	public static String toUpperSnake(String fieldName) {
		if (fieldName == null || fieldName.isEmpty())
			return fieldName;
		StringBuilder out = new StringBuilder(fieldName.length() + 4);
		char[] chars = fieldName.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			char c = chars[i];
			if (Character.isUpperCase(c)) {
				boolean prevLower = i > 0 && Character.isLowerCase(chars[i - 1]);
				boolean nextLower = i + 1 < chars.length && Character.isLowerCase(chars[i + 1]);
				boolean prevDigit = i > 0 && Character.isDigit(chars[i - 1]);
				if (out.length() > 0 && (prevLower || prevDigit || (nextLower && Character.isUpperCase(chars[i - 1])))) {
					out.append('_');
				}
				out.append(c);
			} else {
				out.append(Character.toUpperCase(c));
			}
		}
		return out.toString();
	}

	public static String fieldConstantName(String fieldName) {
		return "FIELD_" + toUpperSnake(fieldName);
	}

	public static String getterName(String fieldName) {
		return "get" + pascal(fieldName);
	}

	public static String setterName(String fieldName) {
		return "set" + pascal(fieldName);
	}

	private static String pascal(String s) {
		if (s == null || s.isEmpty())
			return s;
		if (s.charAt(0) == '_')
			s = s.substring(1);
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
