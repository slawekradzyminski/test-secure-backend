package com.awesome.testing.service.email;

import com.awesome.testing.dto.email.EmailDeliveryStatus;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailEventDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.EmailEventEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.EmailEventRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailEventService {

    private final EmailEventRepository emailEventRepository;

    @Transactional
    public Integer recordQueued(UserEntity user, EmailDto emailDto) {
        Instant now = Instant.now();
        EmailEventEntity event = EmailEventEntity.builder()
                .user(user)
                .type(resolveType(emailDto))
                .status(EmailDeliveryStatus.QUEUED)
                .recipientEmail(emailDto.getTo())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return emailEventRepository.save(event).getId();
    }

    @Transactional
    public void markSentToSink(Integer eventId) {
        emailEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(EmailDeliveryStatus.SENT_TO_SMTP_SINK);
            event.setFailureReason(null);
            event.setUpdatedAt(Instant.now());
            emailEventRepository.save(event);
        });
    }

    @Transactional
    public void markFailed(Integer eventId, String failureReason) {
        emailEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(EmailDeliveryStatus.FAILED);
            event.setFailureReason(truncate(failureReason));
            event.setUpdatedAt(Instant.now());
            emailEventRepository.save(event);
        });
    }

    @Transactional(readOnly = true)
    public List<EmailEventDto> getLatestEventsFor(UserEntity user) {
        return emailEventRepository.findTop20ByUserOrderByCreatedAtDesc(user).stream()
                .map(EmailEventDto::from)
                .toList();
    }

    private static EmailTemplate resolveType(EmailDto emailDto) {
        return emailDto.getTemplate() == null ? EmailTemplate.GENERIC : emailDto.getTemplate();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }
}
