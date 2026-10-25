package com.daniellaera.audittrailservice.domain.model;

import java.util.List;

/** One page of results and the total over all pages. */
public record PageResult<T>(List<T> content, PageQuery page, long totalElements) {
}
