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
 *   <li>The Artemis JMS connection factory instantiates {@code Calendar[]} reflectively, and Spring
 *       Boot configures it (and the pooled-JMS wrapper) through bean-property reflection.</li>
 *   <li>Jackson binds DTOs outside controllers (Ollama client and streaming, JMS email payloads,
 *       the seed catalogue), so every type in the DTO and seed packages is registered for binding.
 *       The scan runs at build time during AOT processing.</li>
 *   <li>{@code @PreAuthorize} SpEL calls {@code @userService.exists(...)} and reads
 *       {@code authentication.principal.username} reflectively.</li>
 *   <li>Caffeine selects generated cache and node classes by name and builds them through method
 *       handles and var handles (the rate-limit cache uses maximum size plus expire-after-access).</li>
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
            for (String jmsType : new String[] {
                    "org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory",
                    "org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory",
                    "org.messaginghub.pooled.jms.JmsPoolConnectionFactory"}) {
                hints.reflection().registerType(TypeReference.of(jmsType),
                        MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            }
            hints.reflection().registerType(TypeReference.of("com.awesome.testing.service.UserService"),
                    MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(TypeReference.of("com.awesome.testing.security.CustomPrincipal"),
                    MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS);
            BindingReflectionHintsRegistrar binding = new BindingReflectionHintsRegistrar();
            ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
                @Override
                protected boolean isCandidateComponent(
                        org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                    return true;
                }
            };
            scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
            for (BeanDefinition candidate : scanner.findCandidateComponents("com.github.benmanes.caffeine.cache")) {
                if (candidate.getBeanClassName() == null || candidate.getBeanClassName().endsWith("package-info")) {
                    continue;
                }
                hints.reflection().registerType(TypeReference.of(candidate.getBeanClassName()),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.ACCESS_DECLARED_FIELDS);
            }
            for (String basePackage : new String[] {"com.awesome.testing.dto", "com.awesome.testing.fakedata"}) {
                for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                    if (candidate.getBeanClassName() == null || candidate.getBeanClassName().endsWith("package-info")) {
                        continue;
                    }
                    binding.registerReflectionHints(hints.reflection(),
                            ClassUtils.resolveClassName(candidate.getBeanClassName(), classLoader));
                }
            }
        }
    }
}
