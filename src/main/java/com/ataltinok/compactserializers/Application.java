package com.ataltinok.compactserializers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.ataltinok.compactserializers.cli.GenerateCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "compact-serializers", mixinStandardHelpOptions = true, version = "compact-serializers 0.1.0", subcommands = {
		GenerateCommand.class }, description = "Reflection-based generator for Hazelcast CompactSerializer Java sources.")
public final class Application {

	// Edit this to point at whichever args file you want to run.
	private static final Path DEFAULT_ARGS_FILE = Path.of("args.txt");

	private Application() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			if (!Files.exists(DEFAULT_ARGS_FILE)) {
				System.err.println("No CLI args and args file not found: " + DEFAULT_ARGS_FILE.toAbsolutePath());
				System.err.println("Create that file or pass args directly.");
				System.exit(1);
			}
			args = readArgsFile(DEFAULT_ARGS_FILE);
			System.out.println("Reading args from: " + DEFAULT_ARGS_FILE.toAbsolutePath());
		}
		int exit = new CommandLine(new Application()).execute(args);
		System.exit(exit);
	}

	static String[] readArgsFile(Path file) throws IOException {
		List<String> lines = Files.readAllLines(file);
		return lines.stream().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).toArray(String[]::new);
	}
}
