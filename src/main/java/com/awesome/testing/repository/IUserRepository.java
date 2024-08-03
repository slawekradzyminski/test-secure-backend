package com.awesome.testing.repository;

import java.util.List;

public interface IUserRepository<T> {
    boolean existsByUsername(String username);
    T findByUsername(String username);
    void deleteByUsername(String username);
    T save(T user);
    List<T> findAll();
}
