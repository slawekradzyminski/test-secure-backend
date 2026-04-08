package com.awesome.testing.dto.order;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@NoArgsConstructor
public class PageDto<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;

    public static <T> PageDto<T> from(Page<T> page) {
        PageDto<T> dto = new PageDto<>();
        dto.setContent(page.getContent());
        dto.setPageNumber(page.getNumber());
        dto.setPageSize(page.getSize());
        dto.setTotalElements(page.getTotalElements());
        dto.setTotalPages(page.getTotalPages());
        return dto;
    }
} 
