package com.awesome.testing.utils;

import lombok.experimental.UtilityClass;

import java.util.function.BiConsumer;

@UtilityClass
public class EntityUpdater {

    public static <T, V> void updateIfNotNull(V value, BiConsumer<T, V> setter, T entity) {
        if (value != null) {
            setter.accept(entity, value);
        }
    }

}
