package com.awesome.testing.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.regex.Pattern;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * GraalVM native-image metadata that Spring Boot cannot infer. Ignored on the JVM.
 * <ul>
 *   <li>The GraphQL schema lives in {@code graphql-preview/}, outside the default {@code graphql/}
 *       location that Spring for GraphQL registers automatically.</li>
 *   <li>{@code bootstrap/products.json} is loaded through a {@code Resource} property.</li>
 *   <li>The Artemis JMS connection factory instantiates {@code Calendar[]} reflectively.</li>
 *   <li>Jackson binds DTOs outside controllers (Ollama client and streaming, JMS email payloads,
 *       the seed catalogue), so every type in the DTO and seed packages is registered for binding.
 *       The scan runs at build time during AOT processing.</li>
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
            BindingReflectionHintsRegistrar binding = new BindingReflectionHintsRegistrar();
            ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
                @Override
                protected boolean isCandidateComponent(
                        org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                    return true;
                }
            };
            scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
            for (String basePackage : new String[] {"com.awesome.testing.dto", "com.awesome.testing.fakedata"}) {
                for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                    binding.registerReflectionHints(hints.reflection(),
                            ClassUtils.resolveClassName(candidate.getBeanClassName(), classLoader));
                }
            }
        }
    }
}
