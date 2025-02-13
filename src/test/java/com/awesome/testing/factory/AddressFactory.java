package com.awesome.testing.factory;

import com.awesome.testing.dto.order.AddressDto;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AddressFactory extends FakerFactory {

    public static AddressDto getRandomAddress() {
        return AddressDto.builder()
                .city(FAKER.address().city())
                .country(FAKER.address().country())
                .state(FAKER.address().state())
                .street(FAKER.address().streetName())
                .zipCode(FAKER.address().zipCode())
                .build();
    }

}
