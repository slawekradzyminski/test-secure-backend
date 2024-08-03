package com.awesome.testing.repository;

import com.awesome.testing.entities.user.DynamoUserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.List;

@Repository
@Profile("prod")
public class DynamoUserRepository implements IUserRepository<DynamoUserEntity> {

    private final DynamoDbTable<DynamoUserEntity> userTable;

    @Autowired
    public DynamoUserRepository(DynamoDbEnhancedClient enhancedClient) {
        this.userTable = enhancedClient.table("UserEntity", TableSchema.fromBean(DynamoUserEntity.class));
    }

    @Override
    public boolean existsByUsername(String username) {
        Key key = Key.builder()
                     .partitionValue(username)
                     .build();
        DynamoUserEntity result = userTable.getItem(key);
        return result != null;
    }

    @Override
    public DynamoUserEntity findByUsername(String username) {
        Key key = Key.builder()
                     .partitionValue(username)
                     .build();
        return userTable.getItem(key);
    }

    @Override
    public void deleteByUsername(String username) {
        Key key = Key.builder()
                     .partitionValue(username)
                     .build();
        userTable.deleteItem(key);
    }

    @Override
    public DynamoUserEntity save(DynamoUserEntity user) {
        userTable.putItem(user);
        return user;
    }

    @Override
    public List<DynamoUserEntity> findAll() {
        return userTable.scan(ScanEnhancedRequest.builder().build())
                        .items()
                        .stream()
                        .toList();
    }
}