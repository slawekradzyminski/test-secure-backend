package com.awesome.testing.config;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.fakedata.ProductSeedDefinition;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the native-image metadata that Spring cannot infer; each hint fixed a native failure. */
class NativeRuntimeHintsTest {

    private static final RuntimeHints HINTS = new RuntimeHints();

    @BeforeAll
    static void registerHints() {
        new NativeRuntimeHints.Registrar().registerHints(HINTS, NativeRuntimeHintsTest.class.getClassLoader());
    }

    @Test
    void registersResourcesLoadedOutsideSpringDefaults() {
        assertThat(RuntimeHintsPredicates.resource().forResource("graphql-preview/commerce.graphqls")).accepts(HINTS);
        assertThat(RuntimeHintsPredicates.resource().forResource("bootstrap/products.json")).accepts(HINTS);
    }

    @Test
    void registersJacksonBindingForDtoAndSeedTypes() {
        assertThat(RuntimeHintsPredicates.reflection().onType(EmailDto.class)).accepts(HINTS);
        assertThat(RuntimeHintsPredicates.reflection().onType(ProductSeedDefinition.class)).accepts(HINTS);
    }

    @Test
    void registersSecurityExpressionTargets() {
        assertThat(RuntimeHintsPredicates.reflection().onType(UserService.class)
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)).accepts(HINTS);
        assertThat(RuntimeHintsPredicates.reflection().onType(CustomPrincipal.class)
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)).accepts(HINTS);
    }

    @Test
    void registersLibrariesThatLoadClassesByName() {
        assertThat(RuntimeHintsPredicates.reflection()
                .onType(TypeReference.of("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)).accepts(HINTS);
        assertThat(RuntimeHintsPredicates.reflection()
                .onType(TypeReference.of("org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle_impl"))
                .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)).accepts(HINTS);
        assertThat(HINTS.reflection().typeHints()
                .anyMatch(hint -> hint.getType().getName().startsWith("com.github.benmanes.caffeine.cache.SS")))
                .isTrue();
        assertThat(HINTS.reflection().typeHints()
                .noneMatch(hint -> hint.getType().getName().endsWith("package-info"))).isTrue();
    }
}
