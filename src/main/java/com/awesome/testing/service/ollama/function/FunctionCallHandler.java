package com.awesome.testing.service.ollama.function;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ToolCallDto;

public interface FunctionCallHandler {

    /**
     * @return the tool/function name advertised to Ollama.
     */
    String getName();

    /**
     * Executes the function for the supplied tool call and returns a message payload
     * that will be fed back to the model (role=tool, matching tool_name).
     */
    ChatMessageDto handle(ToolCallDto toolCall);
}
