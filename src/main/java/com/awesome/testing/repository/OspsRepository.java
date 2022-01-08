package com.awesome.testing.repository;

import com.awesome.testing.model.Osps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface OspsRepository extends JpaRepository<Osps, Integer> {

}
