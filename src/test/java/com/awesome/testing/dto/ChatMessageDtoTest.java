package com.awesome.testing.dto;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageDtoTest {

    private Validator validator;

    @SuppressWarnings("all")
    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void shouldValidateMessageWithContent() {
        // given
        ChatMessageDto message = ChatMessageDto.builder()
                .role("user")
                .content("Hello world")
                .build();

        // when
        Set<ConstraintViolation<ChatMessageDto>> violations = validator.validate(message);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldValidateMessageWithThinking() {
        // given
        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .content("")
                .thinking("Let me think about this...")
                .build();

        // when
        Set<ConstraintViolation<ChatMessageDto>> violations = validator.validate(message);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldValidateMessageWithBothContentAndThinking() {
        // given
        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .content("Here's my response")
                .thinking("I thought about this carefully")
                .build();

        // when
        Set<ConstraintViolation<ChatMessageDto>> violations = validator.validate(message);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFailValidationWhenNeitherContentNorThinkingPresent() {
        // given
        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .content("")
                .thinking("")
                .build();

        // when
        Set<ConstraintViolation<ChatMessageDto>> violations = validator.validate(message);

        // then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Either content, thinking, or tool calls must be present");
    }

    @Test
    void shouldFailValidationWhenNeitherContentNorThinkingProvided() {
        // given
        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .build();

        // when
        Set<ConstraintViolation<ChatMessageDto>> violations = validator.validate(message);

        // then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Either content, thinking, or tool calls must be present");
    }

    @Test
    void shouldFailValidationWhenRoleIsInvalid() {
        // given
        ChatMessageDto message = ChatMessageDto.builder()
                .role("invalid")
                .content("Hello world")
                .build();

        // when
        Set<ConstraintViolation<ChatMessageDto>> violations = validator.validate(message);

        // then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Role must be either 'system', 'user', 'assistant' or 'tool'");
    }

    @Test
    void shouldFailValidationWhenRoleIsBlank() {
        // given
        ChatMessageDto message = ChatMessageDto.builder()
                .role("")
                .content("Hello world")
                .build();

        // when
        Set<ConstraintViolation<ChatMessageDto>> violations = validator.validate(message);

        // then
        assertThat(violations).hasSize(2); // @NotBlank and @Pattern
    }

    @Test
    void shouldValidateWithWhitespaceOnlyContent() {
        // given
        ChatMessageDto message = ChatMessageDto.builder()
                .role("user")
                .content("   ")
                .thinking("I need to think")
                .build();

        // when
        Set<ConstraintViolation<ChatMessageDto>> violations = validator.validate(message);

        // then
        assertThat(violations).isEmpty(); // thinking is present, so validation passes
    }

    @Test
    void shouldFailWithWhitespaceOnlyContentAndThinking() {
        // given
        ChatMessageDto message = ChatMessageDto.builder()
                .role("assistant")
                .content("   ")
                .thinking("   ")
                .build();

        // when
        Set<ConstraintViolation<ChatMessageDto>> violations = validator.validate(message);

        // then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Either content, thinking, or tool calls must be present");
    }

    @Test
    void shouldValidateToolMessageWhenToolNamePresent() {
        ChatMessageDto message = ChatMessageDto.builder()
                .role("tool")
                .toolName("demo_tool")
                .content("{\"foo\":\"bar\"}")
                .build();

        Set<ConstraintViolation<ChatMessageDto>> violations = validator.validate(message);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFailWhenToolMessageMissingToolName() {
        ChatMessageDto message = ChatMessageDto.builder()
                .role("tool")
                .content("{}")
                .build();

        Set<ConstraintViolation<ChatMessageDto>> violations = validator.validate(message);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Tool messages must include tool_name");
    }
}
