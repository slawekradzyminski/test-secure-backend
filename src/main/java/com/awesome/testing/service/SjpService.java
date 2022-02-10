package com.awesome.testing.service;

import com.awesome.testing.model.Sjp;
import com.awesome.testing.repository.SjpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SjpService {

    private final SjpRepository sjpRepository;

    public void addWord(String word) {
        Sjp sjpWord = new Sjp();
        sjpWord.setWord(word);
        log.info("Adding SJP word {}", sjpWord.getWord());
        sjpRepository.save(sjpWord);
    }

    public List<String> getWords(int length) {
        return new ArrayList<>(sjpRepository.getWords(length));
    }

}
