package com.awesome.testing.dto.sjp;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExtensionDTO {

    String beforeExtensions;
    String word;
    String afterExtensions;

}
