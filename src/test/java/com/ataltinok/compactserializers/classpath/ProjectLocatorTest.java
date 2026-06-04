package com.ataltinok.compactserializers.classpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ataltinok.compactserializers.classpath.ProjectLocator;

class ProjectLocatorTest {

	@Test
	void findsModulesAndMapsOwnership(@TempDir Path root) throws Exception {
		Path modA = root.resolve("a");
		Path modB = root.resolve("b");
		Files.createDirectories(modA);
		Files.createDirectories(modB);
		Files.writeString(modA.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion><artifactId>alpha</artifactId></project>");
		Files.writeString(modB.resolve("pom.xml"), "<project><parent><artifactId>root</artifactId></parent>" + "<artifactId>beta</artifactId></project>");

		ProjectLocator loc = new ProjectLocator(root);
		assertThat(loc.modules()).hasSize(2);
		assertThat(loc.byArtifactId("alpha")).isPresent();
		assertThat(loc.byArtifactId("beta")).isPresent();

		Path classFile = modA.resolve("target").resolve("classes").resolve("pkg").resolve("X.class");
		Files.createDirectories(classFile.getParent());
		Files.writeString(classFile, "stub");
		assertThat(loc.owningModuleOf(classFile)).isPresent().get().extracting(ProjectLocator.Module::artifactId).isEqualTo("alpha");
	}
}
