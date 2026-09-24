package esusdata.web;

/** Uniform JSON error body — never leaks a stack trace or a distinguishing detail (§1.12.7). */
public record ApiError(String code, String message) {}
