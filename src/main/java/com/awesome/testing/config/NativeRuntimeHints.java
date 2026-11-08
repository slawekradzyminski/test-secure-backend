package com.awesome.testing.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * GraalVM native-image metadata that Spring Boot cannot infer. Ignored on the JVM.
 * <ul>
 *   <li>The GraphQL schema lives in {@code graphql-preview/}, outside the default {@code graphql/}
 *       location that Spring for GraphQL registers automatically.</li>
 *   <li>{@code bootstrap/products.json} is loaded through a {@code Resource} property.</li>
 *   <li>The Artemis JMS connection factory instantiates {@code Calendar[]} reflectively.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeRuntimeHints.Registrar.class)
public class NativeRuntimeHints {

    static class Registrar implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern("graphql-preview/*.graphqls");
            hints.resources().registerPattern("bootstrap/products.json");
            hints.reflection().registerType(TypeReference.of("java.util.Calendar[]"),
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        }
    }
}
