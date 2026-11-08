package com.awesome.testing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** H2 is a unit-test dependency only; no packaged profile may select it. */
class RuntimeProfilesUsePostgresTest {

    @Test
    void noRuntimeProfileConfiguresH2() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("src/main/resources"))) {
            files.filter(file -> file.getFileName().toString().matches("application.*\\.ya?ml"))
                    .forEach(file -> assertThat(read(file))
                            .as(file.toString())
                            .doesNotContainIgnoringCase("jdbc:h2")
                            .doesNotContainIgnoringCase("h2:")
                            .doesNotContain("create-drop"));
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
