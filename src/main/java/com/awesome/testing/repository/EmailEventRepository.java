package com.awesome.testing.repository;

import com.awesome.testing.entity.EmailEventEntity;
import com.awesome.testing.entity.UserEntity;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailEventRepository extends JpaRepository<EmailEventEntity, Integer> {

    List<EmailEventEntity> findTop20ByUserOrderByCreatedAtDesc(UserEntity user);

    @Transactional
    void deleteAllByUser(UserEntity user);
}
