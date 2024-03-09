package com.awesome.testing.swagger;

import org.springdoc.core.utils.Constants;

import org.springframework.test.web.servlet.MvcResult;

import com.awesome.testing.AbstractMockMvcTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class AbstractSpringDocTest extends AbstractMockMvcTest {

    protected void checkJS() throws Exception {
        String className = getClass().getSimpleName();
        String testNumber = className.replaceAll("[^0-9]", "");
        checkJS("results/app" + testNumber, Constants.SWAGGER_INITIALIZER_URL);
    }

    private void checkJS(String fileName, String uri) throws Exception {
        MvcResult mvcResult = mockMvc.perform(get(uri)).andExpect(status().isOk()).andReturn();
        String transformedIndex = mvcResult.getResponse().getContentAsString();
        assertTrue(transformedIndex.contains("window.ui"));
        assertEquals("no-store", mvcResult.getResponse().getHeader("Cache-Control"));
        assertEquals(this.getContent(fileName), transformedIndex.replace("\r", ""));
    }

    private String getContent(String fileName) {
        try {
            Path path = Paths.get(AbstractMockMvcTest.class.getClassLoader().getResource(fileName).toURI());
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to read file: " + fileName, e);
        }
    }

}
