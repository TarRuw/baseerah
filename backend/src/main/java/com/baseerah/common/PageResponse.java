package com.baseerah.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable, serialization-friendly pagination envelope carried inside {@link ApiResponse#data()} for
 * paged endpoints (DESIGN.md §6). Wrapping a Spring {@link Page} in this record avoids serializing
 * {@code PageImpl} directly (whose JSON shape Spring warns is not contract-stable) and gives the Flutter
 * client a fixed set of fields to page against.
 *
 * @param content       the page's items (already mapped to DTOs)
 * @param page          zero-based page index
 * @param size          page size requested
 * @param totalElements total items across all pages
 * @param totalPages    total number of pages
 * @param <T>           the item type
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /** Project a Spring {@link Page} (of already-mapped DTOs) into the stable envelope shape. */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
