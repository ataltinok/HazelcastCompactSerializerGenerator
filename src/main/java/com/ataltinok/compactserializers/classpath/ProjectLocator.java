package com.ataltinok.compactserializers.classpath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class ProjectLocator {

	private static final Logger log = LoggerFactory.getLogger(ProjectLocator.class);

	public record Module(String artifactId, Path pom, Path targetClasses) {
	}

	private final Map<String, Module> byArtifact = new HashMap<>();
	private final List<Module> modules = new ArrayList<>();

	public ProjectLocator(List<Path> roots) throws IOException {
		for (Path root : roots) {
			log.info("Scanning for Maven modules under {}", root);
			try (Stream<Path> s = Files.walk(root)) {
				s.filter(p -> p.getFileName().toString().equals("pom.xml")).filter(Files::isRegularFile).forEach(this::parsePom);
			}
		}
		log.info("Found {} Maven modules across {} root(s)", modules.size(), roots.size());
	}

	public ProjectLocator(Path root) throws IOException {
		this(List.of(root));
	}

	private void parsePom(Path pom) {
		try {
			String artifactId = readArtifactId(pom);
			if (artifactId == null)
				return;
			Path target = pom.getParent().resolve("target").resolve("classes");
			Module m = new Module(artifactId, pom, target);
			modules.add(m);
			byArtifact.putIfAbsent(artifactId, m);
			log.debug("Found module: {} -> {}", artifactId, pom.getParent().getFileName());
		} catch (Exception e) {
			log.warn("Skipping malformed pom {}: {}", pom, e.getMessage());
		}
	}

	public Optional<Module> byArtifactId(String artifactId) {
		return Optional.ofNullable(byArtifact.get(artifactId));
	}

	/** Locate the module whose target/classes directory is the nearest ancestor of the given path. */
	public Optional<Module> owningModuleOf(Path classFileOrDir) {
		Path abs = classFileOrDir.toAbsolutePath().normalize();
		Module best = null;
		int bestDepth = -1;
		for (Module m : modules) {
			Path tc = m.targetClasses().toAbsolutePath().normalize();
			if (abs.startsWith(tc)) {
				int d = tc.getNameCount();
				if (d > bestDepth) {
					best = m;
					bestDepth = d;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	public List<Module> modules() {
		return modules;
	}

	static String readArtifactId(Path pom) throws Exception {
		var dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(false);
		Document doc = dbf.newDocumentBuilder().parse(pom.toFile());
		Element root = doc.getDocumentElement();
		// pick the first <artifactId> that is a direct child of <project>
		NodeList kids = root.getChildNodes();
		for (int i = 0; i < kids.getLength(); i++) {
			Node n = kids.item(i);
			if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals("artifactId")) {
				return n.getTextContent().trim();
			}
		}
		return null;
	}
}
