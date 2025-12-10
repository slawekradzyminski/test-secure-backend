package com.awesome.testing.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryDto {
    private UUID id;
    private String title;
    private ConversationType type;
    private String model;
    private Double temperature;
    private Boolean think;
    private boolean archived;
    private Instant createdAt;
    private Instant updatedAt;
}
