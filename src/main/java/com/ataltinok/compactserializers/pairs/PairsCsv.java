package com.ataltinok.compactserializers.pairs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PairsCsv {

	private PairsCsv() {
	}

	public static void write(Path file, Map<String, String> classToProject) throws IOException {
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : classToProject.entrySet()) {
			sb.append(e.getKey()).append(',').append(e.getValue()).append('\n');
		}
		Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
	}

	public static Map<String, String> read(Path file) throws IOException {
		Map<String, String> out = new LinkedHashMap<>();
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		for (String raw : lines) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			int comma = line.indexOf(',');
			if (comma < 0) {
				throw new IOException("Malformed pairs line (no comma): " + raw);
			}
			String name = line.substring(0, comma).strip();
			String artifact = line.substring(comma + 1).strip();
			out.put(name, artifact);
		}
		return out;
	}
}
