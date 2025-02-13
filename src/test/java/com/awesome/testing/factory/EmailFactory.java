package com.awesome.testing.factory;

import com.awesome.testing.dto.EmailDTO;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EmailFactory extends FakerFactory {

    public static EmailDTO getRandomEmail() {
        return EmailDTO.builder()
                .to(FAKER.internet().emailAddress())
                .subject(generateSubject())
                .message(generateMessage())
                .build();
    }

    private static String generateSubject() {
        return FAKER.commerce().productName() + " - " + FAKER.marketing().buzzwords();
    }

    private static String generateMessage() {
        return String.format("%s\n\n%s\n\nBest regards,\n%s",
                FAKER.lorem().paragraph(),
                FAKER.lorem().paragraph(),
                FAKER.name().fullName());
    }
} 