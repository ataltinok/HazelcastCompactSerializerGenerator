package com.ataltinok.compactserializers.generator;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class SerializerEmitter {

	public static final String TODO_PACKAGE = "TODO_PACKAGE";

	private SerializerEmitter() {
	}

	public static String emit(Class<?> modelClass, String packageOverride) {
		List<Field> fields = fieldsToSerialize(modelClass);
		String pkg = packageOverride != null && !packageOverride.isBlank() ? packageOverride : TODO_PACKAGE;
		String simple = modelClass.getSimpleName();
		String serializer = simple + "Serializer";

		StringBuilder sb = new StringBuilder(2048);
		sb.append("package ").append(pkg).append(";\n\n");
		sb.append("import com.hazelcast.nio.serialization.compact.CompactReader;\n");
		sb.append("import com.hazelcast.nio.serialization.compact.CompactSerializer;\n");
		sb.append("import com.hazelcast.nio.serialization.compact.CompactWriter;\n\n");
		sb.append("import jakarta.validation.constraints.NotNull;\n\n");
		sb.append("public class ").append(serializer).append(" implements CompactSerializer<").append(simple).append("> {\n\n");

		for (Field f : fields) {
			sb.append("    private static final String ").append(NameHelpers.fieldConstantName(f.getName())).append(" = \"").append(f.getName())
					.append("\";\n");
		}
		if (!fields.isEmpty())
			sb.append('\n');

		sb.append("    @Override\n");
		sb.append("    public ").append(simple).append(" read(@NotNull CompactReader reader) {\n");
		sb.append("        ").append(simple).append(" obj = new ").append(simple).append("();\n");
		for (Field f : fields) {
			sb.append("        ").append(TypeMapper.mapField(f).readLine()).append('\n');
		}
		sb.append("        return obj;\n");
		sb.append("    }\n\n");

		sb.append("    @Override\n");
		sb.append("    public void write(@NotNull CompactWriter writer, @NotNull ").append(simple).append(" obj) {\n");
		for (Field f : fields) {
			sb.append("        ").append(TypeMapper.mapField(f).writeLine()).append('\n');
		}
		sb.append("    }\n\n");

		sb.append("    @NotNull\n");
		sb.append("    @Override\n");
		sb.append("    public String getTypeName() {\n");
		sb.append("        return \"").append(simple).append("\";\n");
		sb.append("    }\n\n");

		sb.append("    @NotNull\n");
		sb.append("    @Override\n");
		sb.append("    public Class<").append(simple).append("> getCompactClass() {\n");
		sb.append("        return ").append(simple).append(".class;\n");
		sb.append("    }\n");
		sb.append("}\n");
		return sb.toString();
	}

	static List<Field> fieldsToSerialize(Class<?> modelClass) {
		List<Field> fields = new ArrayList<>();
		for (Field f : modelClass.getDeclaredFields()) {
			int mods = f.getModifiers();
			if (Modifier.isStatic(mods))
				continue;
			if (f.isSynthetic())
				continue;
			if (f.getName().equals("serialVersionUID"))
				continue;
			fields.add(f);
		}
		return fields;
	}
}
