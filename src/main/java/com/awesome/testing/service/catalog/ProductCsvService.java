package com.awesome.testing.service.catalog;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.catalog.ImportReportDto;
import com.awesome.testing.dto.catalog.ImportRowErrorDto;
import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.product.ProductCreateDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.service.InventoryService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductCsvService {

    public static final long MAX_BYTES = 1024 * 1024;
    public static final int MAX_ROWS = 1000;
    static final List<String> COLUMNS =
            List.of("id", "name", "description", "price", "stockQuantity", "category", "imageUrl");
    private static final Set<String> REQUIRED = Set.of("name", "description", "price", "stockQuantity", "category");
    private static final String CSV_REASON = "CSV import";

    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final Validator validator;

    @Transactional(readOnly = true)
    public String export() {
        StringBuilder csv = new StringBuilder(String.join(",", COLUMNS)).append("\r\n");
        for (ProductEntity product : productRepository.findAll(Sort.by("id"))) {
            csv.append(CsvCodec.cell(product.getId())).append(',')
                    .append(CsvCodec.textCell(product.getName())).append(',')
                    .append(CsvCodec.textCell(product.getDescription())).append(',')
                    .append(CsvCodec.cell(product.getPrice().toPlainString())).append(',')
                    .append(CsvCodec.cell(product.getStockQuantity())).append(',')
                    .append(CsvCodec.textCell(product.getCategory())).append(',')
                    .append(CsvCodec.textCell(product.getImageUrl())).append("\r\n");
        }
        return csv.toString();
    }

    /**
     * Validates every row first and only then applies changes, so the import
     * is all-or-nothing. Returns a report; {@code errors} is non-empty when
     * nothing was persisted because of invalid input.
     */
    @Transactional
    public ImportReportDto importCsv(byte[] content, boolean dryRun, String actor) {
        if (content.length == 0) {
            throw new CustomException("CSV file is empty", HttpStatus.BAD_REQUEST);
        }
        if (content.length > MAX_BYTES) {
            throw new CustomException("CSV file must not exceed 1 MB", HttpStatus.CONTENT_TOO_LARGE);
        }
        List<List<String>> records;
        try {
            records = CsvCodec.parse(decode(content));
        } catch (IllegalArgumentException e) {
            throw new CustomException("Malformed CSV: " + e.getMessage(), HttpStatus.BAD_REQUEST, e);
        }
        ImportReportDto report = ImportReportDto.builder().dryRun(dryRun).build();
        if (records.isEmpty()) {
            throw new CustomException("CSV file is empty", HttpStatus.BAD_REQUEST);
        }
        Map<String, Integer> header = readHeader(records.getFirst(), report);
        if (!report.getErrors().isEmpty()) {
            return report;
        }

        List<PlannedRow> plan = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> record = records.get(index);
            if (record.stream().allMatch(String::isBlank)) {
                continue;
            }
            int line = index + 1;
            report.setTotalRows(report.getTotalRows() + 1);
            if (report.getTotalRows() > MAX_ROWS) {
                throw new CustomException("CSV import supports at most " + MAX_ROWS + " rows", HttpStatus.BAD_REQUEST);
            }
            PlannedRow row = planRow(line, record, header, seenIds, report.getErrors());
            if (row != null) {
                plan.add(row);
            }
        }
        for (PlannedRow row : plan) {
            switch (row.action()) {
                case CREATE -> report.setCreated(report.getCreated() + 1);
                case UPDATE -> report.setUpdated(report.getUpdated() + 1);
                case UNCHANGED -> report.setUnchanged(report.getUnchanged() + 1);
            }
        }
        report.getErrors().sort(Comparator.comparingInt(ImportRowErrorDto::getRow));
        if (!report.getErrors().isEmpty() || dryRun) {
            return report;
        }
        for (PlannedRow row : plan) {
            apply(row, actor);
        }
        productRepository.flush();
        report.setApplied(true);
        return report;
    }

    private Map<String, Integer> readHeader(List<String> headerRecord, ImportReportDto report) {
        Map<String, String> canonical = new HashMap<>();
        COLUMNS.forEach(column -> canonical.put(column.toLowerCase(Locale.ROOT), column));
        Map<String, Integer> header = new HashMap<>();
        for (int i = 0; i < headerRecord.size(); i++) {
            String name = headerRecord.get(i).trim();
            String column = canonical.get(name.toLowerCase(Locale.ROOT));
            if (column == null) {
                report.getErrors().add(new ImportRowErrorDto(1, "file", "Unknown column: " + name));
            } else if (header.putIfAbsent(column, i) != null) {
                report.getErrors().add(new ImportRowErrorDto(1, "file", "Duplicate column: " + column));
            }
        }
        REQUIRED.stream().sorted().filter(column -> !header.containsKey(column)).forEach(column ->
                report.getErrors().add(new ImportRowErrorDto(1, "file", "Missing required column: " + column)));
        return header;
    }

    private PlannedRow planRow(
            int line,
            List<String> record,
            Map<String, Integer> header,
            Set<Long> seenIds,
            List<ImportRowErrorDto> errors) {
        int errorsBefore = errors.size();
        if (record.size() != header.size()) {
            errors.add(new ImportRowErrorDto(line, "file",
                    "Expected " + header.size() + " values but found " + record.size()));
            return null;
        }
        ProductCreateDto dto = ProductCreateDto.builder()
                .name(value(record, header, "name"))
                .description(value(record, header, "description"))
                .category(value(record, header, "category"))
                .imageUrl(emptyToNull(value(record, header, "imageUrl")))
                .build();
        String price = value(record, header, "price");
        if (price != null && !price.isBlank()) {
            try {
                dto.setPrice(new BigDecimal(price.trim()));
            } catch (NumberFormatException e) {
                errors.add(new ImportRowErrorDto(line, "price", "Price must be a number"));
            }
        }
        String stock = value(record, header, "stockQuantity");
        if (stock != null && !stock.isBlank()) {
            try {
                dto.setStockQuantity(Integer.parseInt(stock.trim()));
            } catch (NumberFormatException e) {
                errors.add(new ImportRowErrorDto(line, "stockQuantity", "Stock quantity must be a whole number"));
            }
        }
        Set<String> parseFailures = new HashSet<>();
        errors.subList(errorsBefore, errors.size()).forEach(error -> parseFailures.add(error.getField()));
        for (ConstraintViolation<ProductCreateDto> violation : validator.validate(dto)) {
            String field = violation.getPropertyPath().toString();
            if (!parseFailures.contains(field)) {
                errors.add(new ImportRowErrorDto(line, field, violation.getMessage()));
            }
        }

        String rawId = value(record, header, "id");
        ProductEntity existing = null;
        if (rawId != null && !rawId.isBlank()) {
            try {
                long id = Long.parseLong(rawId.trim());
                if (!seenIds.add(id)) {
                    errors.add(new ImportRowErrorDto(line, "id", "Duplicate id in file: " + id));
                }
                existing = productRepository.findById(id).orElse(null);
                if (existing == null) {
                    errors.add(new ImportRowErrorDto(line, "id", "Product not found: " + id));
                }
            } catch (NumberFormatException e) {
                errors.add(new ImportRowErrorDto(line, "id", "Id must be a whole number"));
            }
        }
        if (errors.size() > errorsBefore) {
            return null;
        }
        if (existing == null) {
            return new PlannedRow(Action.CREATE, null, dto);
        }
        return new PlannedRow(sameAs(existing, dto) ? Action.UNCHANGED : Action.UPDATE, existing.getId(), dto);
    }

    private void apply(PlannedRow row, String actor) {
        ProductCreateDto dto = row.data();
        switch (row.action()) {
            case CREATE -> {
                ProductEntity product = productRepository.save(ProductEntity.from(dto));
                inventoryService.initial(product, actor);
            }
            case UPDATE -> {
                ProductEntity product = productRepository.findByIdForUpdate(row.productId())
                        .orElseThrow(() -> new CustomException("Product not found: " + row.productId(), HttpStatus.CONFLICT));
                int delta = dto.getStockQuantity() - product.getStockQuantity();
                product.setName(dto.getName());
                product.setDescription(dto.getDescription());
                product.setPrice(dto.getPrice());
                product.setCategory(dto.getCategory());
                product.setImageUrl(dto.getImageUrl());
                productRepository.save(product);
                if (delta != 0) {
                    inventoryService.adjust(product.getId(), InventoryAdjustmentDto.builder()
                            .delta(delta)
                            .reason(CSV_REASON)
                            .requestId(UUID.randomUUID())
                            .build(), actor);
                }
            }
            case UNCHANGED -> {
                // Nothing to persist.
            }
        }
    }

    private static boolean sameAs(ProductEntity product, ProductCreateDto dto) {
        return Objects.equals(product.getName(), dto.getName())
                && Objects.equals(product.getDescription(), dto.getDescription())
                && product.getPrice().compareTo(dto.getPrice()) == 0
                && Objects.equals(product.getStockQuantity(), dto.getStockQuantity())
                && Objects.equals(product.getCategory(), dto.getCategory())
                && Objects.equals(emptyToNull(product.getImageUrl()), dto.getImageUrl());
    }

    private static String value(List<String> record, Map<String, Integer> header, String column) {
        Integer index = header.get(column);
        if (index == null || index >= record.size()) {
            return null;
        }
        return record.get(index);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new CustomException("CSV file must be UTF-8 encoded", HttpStatus.BAD_REQUEST, e);
        }
    }

    private enum Action { CREATE, UPDATE, UNCHANGED }

    private record PlannedRow(Action action, Long productId, ProductCreateDto data) {
    }
}
