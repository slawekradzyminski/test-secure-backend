package com.awesome.testing;

import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class FileResourcesUtils {

    public List<String> getAllLines(String fileName) {
        return getStreamOfWords(getFileFromResourceAsStream(fileName));
    }

    private InputStream getFileFromResourceAsStream(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(fileName);

        if (inputStream == null) {
            throw new IllegalArgumentException("file not found! " + fileName);
        } else {
            return inputStream;
        }
    }

    private static List<String> getStreamOfWords(InputStream is) {
        List<String> allWords = Collections.emptyList();

        try (InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(streamReader)) {

            allWords = reader.lines().collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
        }

        return allWords;
    }
}
