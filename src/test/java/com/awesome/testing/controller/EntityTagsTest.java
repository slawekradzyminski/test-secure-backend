package com.awesome.testing.controller;

import com.awesome.testing.controller.exception.CustomException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityTagsTest {

    @Test
    void ifMatchAcceptsStrongAndWeakTagsAndIgnoresWildcards() {
        assertThat(EntityTags.parseVersion("\"7\"")).isEqualTo(7L);
        assertThat(EntityTags.parseVersion("W/\"7\"")).isEqualTo(7L);
        assertThat(EntityTags.parseVersion("*")).isNull();
        assertThat(EntityTags.parseVersion(null)).isNull();
    }

    @Test
    void malformedIfMatchIsABadRequest() {
        assertThatThrownBy(() -> EntityTags.parseVersion("7")).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> EntityTags.parseVersion("\"seven\"")).isInstanceOf(CustomException.class);
    }

    @Test
    void ifNoneMatchMatchesAnyListedTagOrWildcard() {
        assertThat(EntityTags.matches("\"1\", \"7\"", 7L)).isTrue();
        assertThat(EntityTags.matches("*", 7L)).isTrue();
        assertThat(EntityTags.matches("\"6\"", 7L)).isFalse();
        assertThat(EntityTags.matches(null, 7L)).isFalse();
    }
}
