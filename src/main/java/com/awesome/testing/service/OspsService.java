package com.awesome.testing.service;

import com.awesome.testing.model.Osps;
import com.awesome.testing.repository.OspsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OspsService {

    private final OspsRepository ospsRepository;

    public void addWord(String word) {
        Osps ospsWord = new Osps();
        ospsWord.setWord(word);
        log.info("Adding OSPS word {}", ospsWord);
        ospsRepository.save(ospsWord);
    }

}
