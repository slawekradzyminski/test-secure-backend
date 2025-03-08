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
        List<TokenDto> tokenList = extractTokens(encoding, encodedTokens);
        int tokenCount = encodedTokens.size();
        int wordCount = getWordCount(text);

        return TokenizeResponseDto.builder()
                .tokenMap(tokenList)
                .tokenCount(tokenCount)
                .inputCharsCount(text.length())
                .inputWordsCount(wordCount)
                .tokenToWordRatio(getRatio(wordCount, tokenCount))
                .build();
    }

    private List<TokenDto> extractTokens(Encoding encoding, IntArrayList encodedTokens) {
        return encodedTokens.boxed().stream()
                .map(tokenId -> toTokenDto(encoding, tokenId))
                .filter(Objects::nonNull)
                .toList();
    }

    private TokenDto toTokenDto(Encoding encoding, Integer tokenId) {
        IntArrayList singleTokenList = new IntArrayList();
        singleTokenList.add(tokenId);
        String tokenText = encoding.decode(singleTokenList);

        return tokenText.isEmpty() ? null : TokenDto.builder().token(tokenText).id(tokenId).build();
    }

    private Encoding getEncoding(TokenizeRequestDto requestDto) {
        ModelType modelType = getModelType(requestDto);
        return encodingRegistry.getEncodingForModel(modelType);
    }

    private ModelType getModelType(TokenizeRequestDto requestDto) {
        return Optional.ofNullable(requestDto.getModelType())
                .orElse(ModelType.GPT_4O);
    }

    private int getWordCount(String text) {
        return text.trim().isEmpty()
                ? 0
                : text.trim().split("\\s+").length;
    }

    private double getRatio(int wordCount, double tokenCount) {
        return (wordCount > 0)
                ? (tokenCount / wordCount)
                : 0.0;
    }
} 