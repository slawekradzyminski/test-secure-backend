package com.awesome.testing.repository.pagination;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** A row offset, including offsets that are not multiples of the page size. */
public record OffsetPageRequest(long offset, int size) implements Pageable {

    public OffsetPageRequest {
        if (offset < 0 || size < 1) {
            throw new IllegalArgumentException("Offset must be nonnegative and size must be positive");
        }
    }

    @Override
    public int getPageNumber() {
        return Math.toIntExact(offset / size);
    }

    @Override
    public int getPageSize() {
        return size;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return Sort.by("id").ascending();
    }

    @Override
    public Pageable next() {
        return new OffsetPageRequest(Math.addExact(offset, size), size);
    }

    @Override
    public Pageable previousOrFirst() {
        return new OffsetPageRequest(Math.max(0, offset - size), size);
    }

    @Override
    public Pageable first() {
        return new OffsetPageRequest(0, size);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("Page number must be nonnegative");
        }
        return new OffsetPageRequest((long) pageNumber * size, size);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
