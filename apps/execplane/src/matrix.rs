use serde::Deserialize;

/// The packaged compatibility contract — the single source both Java (`PecCompatibilityMatrix`)
/// and this binary read, per plan §2.2/ADR 0009. Only `objects_used`/`columns_used` matter here:
/// this binary never decides compatibility, it only needs to know what to measure. The verdict
/// (matching `signature_fingerprint`) is Java's alone (ADR 0010).
const MATRIX_JSON: &str = include_str!("../../../contracts/compatibility/pec-adapters.json");

#[derive(Deserialize)]
struct Matrix {
    tested_with: Vec<MatrixEntry>,
}

#[derive(Deserialize)]
struct MatrixEntry {
    pec_versions: Vec<String>,
    adapter_version: String,
    read_model: String,
    installation_role: String,
    capability: String,
    objects_used: Vec<MatrixObject>,
}

#[derive(Deserialize)]
pub struct MatrixObject {
    pub object: String,
    pub columns_used: Vec<String>,
}

/// Finds the packaged entry matching everything the envelope already tells us — deliberately
/// excluding `postgresql_version`, which we only learn once connected. Java's own
/// `PecCompatibilityMatrix.findExact` performs the real, postgres-version-inclusive lookup after
/// receiving our probe; if we pick nothing here (or the wrong thing), Java's comparison fails
/// closed rather than silently accepting an unvalidated read.
pub fn objects_to_probe(
    capability: &str,
    adapter_version: &str,
    pec_version: &str,
    read_model: &str,
    installation_role: &str,
) -> Vec<MatrixObject> {
    let matrix: Matrix =
        serde_json::from_str(MATRIX_JSON).expect("packaged compatibility matrix is malformed");
    matrix
        .tested_with
        .into_iter()
        .find(|entry| {
            entry.capability == capability
                && entry.adapter_version == adapter_version
                && entry
                    .pec_versions
                    .iter()
                    .any(|listed| listed == pec_version)
                && entry.read_model == read_model
                && entry.installation_role == installation_role
        })
        .map(|entry| entry.objects_used)
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn finds_the_real_packaged_entry() {
        let objects = objects_to_probe(
            "individual_encounter_modality",
            "0.1.0",
            "5.4.37",
            "PEC_DW",
            "PRONTUARIO",
        );
        assert_eq!(objects.len(), 7);
        assert!(objects
            .iter()
            .any(|o| o.object == "tb_fat_atendimento_individual"));
    }

    #[test]
    fn finds_the_same_entry_for_every_listed_pec_version() {
        let objects = objects_to_probe(
            "individual_encounter_modality",
            "0.1.0",
            "5.5.28",
            "PEC_DW",
            "PRONTUARIO",
        );
        assert_eq!(objects.len(), 7);
    }

    #[test]
    fn returns_nothing_for_an_unknown_identity_rather_than_guessing() {
        let objects = objects_to_probe(
            "individual_encounter_modality",
            "0.1.0",
            "unknown-pec-version",
            "PEC_DW",
            "PRONTUARIO",
        );
        assert!(objects.is_empty());
    }
}
