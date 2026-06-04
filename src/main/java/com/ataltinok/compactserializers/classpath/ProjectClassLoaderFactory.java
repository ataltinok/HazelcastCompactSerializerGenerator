package com.ataltinok.compactserializers.classpath;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ProjectClassLoaderFactory implements AutoCloseable {

	private final ProjectLocator locator;
	private final MavenClasspathResolver resolver;
	private final Map<String, URLClassLoader> cache = new HashMap<>();

	public ProjectClassLoaderFactory(ProjectLocator locator, MavenClasspathResolver resolver) {
		this.locator = locator;
		this.resolver = resolver;
	}

	public ClassLoader forArtifact(String artifactId) throws IOException, InterruptedException {
		URLClassLoader cl = cache.get(artifactId);
		if (cl != null)
			return cl;

		var module = locator.byArtifactId(artifactId).orElseThrow(() -> new IllegalArgumentException("No module with artifactId=" + artifactId));

		List<URL> urls = new ArrayList<>();
		urls.add(module.targetClasses().toUri().toURL());
		for (Path p : resolver.resolve(module.pom())) {
			urls.add(p.toUri().toURL());
		}
		cl = new URLClassLoader(artifactId + "-cl", urls.toArray(URL[]::new), getClass().getClassLoader());
		cache.put(artifactId, cl);
		return cl;
	}

	@Override
	public void close() {
		for (URLClassLoader cl : cache.values()) {
			try {
				cl.close();
			} catch (IOException ignore) {
			}
		}
		cache.clear();
	}
}
