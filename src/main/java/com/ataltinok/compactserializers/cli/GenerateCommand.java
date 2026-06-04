package com.ataltinok.compactserializers.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ataltinok.compactserializers.classpath.MavenClasspathResolver;
import com.ataltinok.compactserializers.classpath.ProjectClassLoaderFactory;
import com.ataltinok.compactserializers.classpath.ProjectLocator;
import com.ataltinok.compactserializers.discovery.DependencyWalker;
import com.ataltinok.compactserializers.generator.SerializerEmitter;
import com.ataltinok.compactserializers.pairs.PairsCsv;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "generate", description = "Generate Hazelcast CompactSerializer<T> sources for a model class and its private-field dependencies.", mixinStandardHelpOptions = true)
public class GenerateCommand implements Callable<Integer> {

	private static final Logger log = LoggerFactory.getLogger(GenerateCommand.class);

	@Option(names = "--root", required = true, arity = "1..*", description = "Root directory to scan for Maven modules. Repeat the flag for multiple roots.")
	List<Path> roots;

	@Option(names = "--model", required = true, description = "Fully-qualified class name of the root model.")
	String modelFqn;

	@Option(names = "--out", description = "Output directory for generated Serializer sources. Default: out/<ModelName>/serializer")
	Path outDir;

	@Option(names = "--package", description = "Package declaration for emitted files. Default: TODO_PACKAGE placeholder.")
	String packageOverride;

	@Option(names = "--pairs", description = "Folder for ClassName,ProjectName pairs CSV. Default: out/<ModelName>/pairs")
	Path pairsDir;

	@Option(names = "--anchor-artifact", description = "ArtifactId of the module to load the root model from. Auto-detected when omitted.")
	String anchorArtifact;

	@Override
	public Integer call() throws Exception {
		long t0 = System.currentTimeMillis();
		String modelSimpleName = modelFqn.contains(".") ? modelFqn.substring(modelFqn.lastIndexOf('.') + 1) : modelFqn;
		if (outDir == null) {
			outDir = Path.of("out", modelSimpleName, "serializer");
		}
		if (pairsDir == null) {
			pairsDir = Path.of("out", modelSimpleName, "pairs");
		}
		log.info("=== Compact Serializers Generator ===");
		log.info("roots={} model={} out={} pairs={}", roots, modelFqn, outDir, pairsDir);

		for (Path root : roots) {
			if (!Files.isDirectory(root)) {
				log.error("root is not a directory: {}", root);
				return 1;
			}
		}
		Files.createDirectories(outDir);

		ProjectLocator locator = new ProjectLocator(roots);
		if (locator.modules().isEmpty()) {
			log.error("No pom.xml found under {}", roots);
			return 2;
		}
		MavenClasspathResolver resolver = new MavenClasspathResolver();

		try (ProjectClassLoaderFactory factory = new ProjectClassLoaderFactory(locator, resolver)) {

			log.info("Resolving anchor ClassLoader for {}", modelFqn);
			ClassLoader anchorCl = resolveAnchor(locator, factory);
			log.info("Loading root class {}", modelFqn);
			Class<?> rootClass = Class.forName(modelFqn, false, anchorCl);
			log.info("Loaded {}", rootClass.getName());

			List<Class<?>> discovered = DependencyWalker.walk(rootClass);
			if (discovered.isEmpty()) {
				log.error("Nothing discovered from {}", modelFqn);
				return 3;
			}
			log.info("Discovered {} classes total", discovered.size());

			Map<String, String> classToProject = new LinkedHashMap<>();
			for (Class<?> c : discovered) {
				String artifact = artifactOf(c, locator).orElse("UNKNOWN");
				classToProject.put(c.getSimpleName(), artifact);
				log.debug("  {} -> {}", c.getSimpleName(), artifact);
			}

			Path pairsFile = pairsDir.resolve("pairs.csv");
			PairsCsv.write(pairsFile, classToProject);
			log.info("Wrote pairs CSV: {}", pairsFile);

			for (Class<?> c : discovered) {
				log.info("Emitting serializer for {}", c.getSimpleName());
				String src = SerializerEmitter.emit(c, packageOverride);
				Path file = outDir.resolve(c.getSimpleName() + "Serializer.java");
				Files.writeString(file, src, StandardCharsets.UTF_8);
				log.info("Wrote {}", file);
			}
		}

		log.info("Done in {} ms", System.currentTimeMillis() - t0);
		return 0;
	}

	private ClassLoader resolveAnchor(ProjectLocator locator, ProjectClassLoaderFactory factory) throws IOException, InterruptedException {
		if (anchorArtifact != null) {
			log.info("Using anchor-artifact={}", anchorArtifact);
			return factory.forArtifact(anchorArtifact);
		}
		log.info("Auto-detecting anchor module by trying {} candidates ...", locator.modules().size());
		Exception last = null;
		for (ProjectLocator.Module m : locator.modules()) {
			log.debug("Trying module {} ...", m.artifactId());
			try {
				ClassLoader cl = factory.forArtifact(m.artifactId());
				Class.forName(modelFqn, false, cl);
				log.info("Anchor resolved: {}", m.artifactId());
				return cl;
			} catch (ClassNotFoundException | NoClassDefFoundError e) {
				log.debug("  Not found in {} ({})", m.artifactId(), e.getMessage());
			} catch (InterruptedException e) {
				log.warn("  Error probing {}: {}", m.artifactId(), e.getMessage());
				last = e;
				Thread.currentThread().interrupt();
			}
		}
		throw new IllegalStateException(
				"Could not load " + modelFqn + " from any module under " + roots + (last != null ? " (last error: " + last.getMessage() + ")" : ""));
	}

	static Optional<String> artifactOf(Class<?> c, ProjectLocator locator) {
		String relPath = c.getName().replace('.', '/') + ".class";
		for (ProjectLocator.Module m : locator.modules()) {
			if (Files.exists(m.targetClasses().resolve(relPath))) {
				return Optional.of(m.artifactId());
			}
		}
		return Optional.empty();
	}
}
