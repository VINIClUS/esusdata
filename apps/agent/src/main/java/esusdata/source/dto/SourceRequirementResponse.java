package esusdata.source.dto;

/** One source requirement: a stable code (labelled by the client) and whether the source meets it. */
public record SourceRequirementResponse(String code, boolean ok) {}
