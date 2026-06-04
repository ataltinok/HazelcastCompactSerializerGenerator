package com.ataltinok.compactserializers.classpath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Resolves the full compile classpath of a Maven module by shelling out to `mvn`. */
public final class MavenClasspathResolver {

	private static final Logger log = LoggerFactory.getLogger(MavenClasspathResolver.class);

	private final Map<Path, List<Path>> cache = new HashMap<>();
	private final String mvnExecutable;

	public MavenClasspathResolver() {
		this(defaultExecutable());
	}

	public MavenClasspathResolver(String mvnExecutable) {
		this.mvnExecutable = mvnExecutable;
	}

	public List<Path> resolve(Path pom) throws IOException, InterruptedException {
		Path norm = pom.toAbsolutePath().normalize();
		List<Path> cached = cache.get(norm);
		if (cached != null) {
			log.debug("Classpath cache hit for {}", norm);
			return cached;
		}

		log.info("Resolving classpath for {} (running mvn dependency:build-classpath) ...", norm);
		long t0 = System.currentTimeMillis();
		Path outFile = Files.createTempFile("cs-cp-", ".txt");
		try {
			ProcessBuilder pb = new ProcessBuilder(mvnExecutable, "-q", "-f", norm.toString(), "-Dmdep.outputFile=" + outFile.toString(),
					"dependency:build-classpath").redirectErrorStream(true);
			Process p = pb.start();
			byte[] buf = p.getInputStream().readAllBytes();
			if (!p.waitFor(10, TimeUnit.MINUTES)) {
				p.destroyForcibly();
				throw new IOException("mvn dependency:build-classpath timed out for " + norm);
			}
			if (p.exitValue() != 0) {
				throw new IOException("mvn exit " + p.exitValue() + " for " + norm + "\n" + new String(buf, StandardCharsets.UTF_8));
			}
			String raw = Files.readString(outFile, StandardCharsets.UTF_8).trim();
			List<Path> parts = raw.isEmpty() ? List.of() : Arrays.stream(raw.split(java.io.File.pathSeparator)).map(Path::of).toList();
			cache.put(norm, parts);
			log.info("Classpath resolved: {} entries in {} ms for {}", parts.size(), System.currentTimeMillis() - t0, norm.getParent().getFileName());
			return parts;
		} finally {
			Files.deleteIfExists(outFile);
		}
	}

	private static String defaultExecutable() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "mvn.cmd" : "mvn";
	}
}
