package com.awesome.testing.repository;

import com.awesome.testing.model.Sjp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface SjpRepository extends JpaRepository<Sjp, Integer> {

    @Query("select word from Sjp WHERE CHAR_LENGTH(word)=?#{[0]} ORDER BY word ASC")
    Collection<String> getWords(int length);

}
