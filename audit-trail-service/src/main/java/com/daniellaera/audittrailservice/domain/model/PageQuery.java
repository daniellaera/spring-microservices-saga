package com.daniellaera.audittrailservice.domain.model;

/** A page of results: page number from 0, at most size items. */
public record PageQuery(int page, int size) {
}
