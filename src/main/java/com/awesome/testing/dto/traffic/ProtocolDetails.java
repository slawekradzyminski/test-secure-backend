package com.awesome.testing.dto.traffic;

import java.util.List;

/** Server-selected metadata only; never contains client documents, arguments or error messages. */
public record ProtocolDetails(String protocol, String operation, String outcome,
                              List<String> codes, String correlationId) { }
