package esusdata.indicator.reconciliation;

/** Where an official SIAPS reference comes from (spec §6.5). */
enum SourceKind {
    OFFICIAL_TEAM_EXPORT_CSV,
    PUBLIC_AGGREGATE,
    PUBLIC_AGGREGATE_WITH_PERIOD_UNIVERSE
}
