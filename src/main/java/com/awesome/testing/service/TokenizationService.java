package com.awesome.testing.service;

import com.awesome.testing.dto.tokenizer.TokenDto;
import com.awesome.testing.dto.tokenizer.TokenizeRequestDto;
import com.awesome.testing.dto.tokenizer.TokenizeResponseDto;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.knuddels.jtokkit.api.IntArrayList;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TokenizationService {

    private EncodingRegistry encodingRegistry;

    @PostConstruct
    public void initRegistry() {
        encodingRegistry = Encodings.newDefaultEncodingRegistry();
    }

    public TokenizeResponseDto tokenize(TokenizeRequestDto requestDto) {
        Encoding encoding = getEncoding(requestDto);
        String text = requestDto.getText();
        IntArrayList encodedTokens = encoding.encode(text);
        List<String> tokens = extractTokens(encoding, encodedTokens);

        return TokenizeResponseDto.builder()
                .tokens(tokens)
                .modelName(requestDto.getModelName())
                .build();
    }

    private List<String> extractTokens(Encoding encoding, IntArrayList encodedTokens) {
        return encodedTokens.boxed().stream()
                .map(tokenId -> {
                    IntArrayList singleTokenList = new IntArrayList();
                    singleTokenList.add(tokenId);
                    return encoding.decode(singleTokenList);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Encoding getEncoding(TokenizeRequestDto requestDto) {
        return encodingRegistry.getEncodingForModel(ModelType.GPT_4O);
    }
} 