package esusdata.indicator.sensitivity;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalPerson;
import java.util.List;

/** Copies of a canonical dataset with one kind of record replaced, for the readings that re-tag data. */
public final class Datasets {

    private Datasets() {}

    /** {@code data} with its person records replaced by {@code persons}; every other record and window is kept. */
    public static CanonicalDataset replacingPersons(CanonicalDataset data, List<CanonicalPerson> persons) {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        data.windows().forEach(builder::window);
        data.encounters().forEach(builder::encounter);
        persons.forEach(builder::add);
        data.registrations().forEach(builder::add);
        data.teams().forEach(builder::add);
        data.careEvents().forEach(builder::add);
        data.procedureEvents().forEach(builder::add);
        data.homeVisits().forEach(builder::add);
        data.immunizations().forEach(builder::add);
        data.conditions().forEach(builder::add);
        data.measurements().forEach(builder::add);
        data.pregnancyOutcomes().forEach(builder::add);
        return builder.build();
    }
}
