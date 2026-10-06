package esusdata.indicator.pack.c6;

import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.pack.c6.C6Cohort.Subject;
import esusdata.indicator.pack.c6.C6Pack.Assessment;
import esusdata.indicator.pack.c6.C6Practices.Practice;
import esusdata.indicator.pack.c6.C6Practices.Support;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal evidence (§1.8, ENG-36): one row per person considered — eligible or excluded with its
 * reason — one per eligible person and practice with the points, and the supporting events. Only
 * the opaque person key identifies anyone; no name, CPF or CNS.
 */
final class C6Evidence {

    private C6Evidence() {}

    static List<EvidenceItem> of(List<Subject> subjects, List<Assessment> assessed, LocalDate lastDay) {
        Map<String, Assessment> byKey = new HashMap<>();
        assessed.forEach(a -> byKey.put(a.subject().personKey(), a));
        List<EvidenceItem> items = new ArrayList<>();
        String reference = lastDay.toString();
        for (Subject s : subjects) {
            Assessment a = byKey.get(s.personKey());
            if (a == null) {
                items.add(person(s, reference, null, EvidenceDecision.EXCLUDED, s.reasonCode(), null));
            } else {
                items.add(person(s, reference, null, EvidenceDecision.ELIGIBLE, s.reasonCode(), a.points()));
                for (Practice p : Practice.values()) {
                    practice(items, a, p, reference);
                }
            }
        }
        return items;
    }

    private static void practice(List<EvidenceItem> items, Assessment a, Practice p, String reference) {
        boolean observed = a.met(p);
        boolean credited = a.credited(p);
        boolean counted = observed || credited;
        String reason = credited ? C6Pack.C_REASON_CREDITED_EAP : p.reason(observed);
        BigInteger points = counted ? C6Pack.spec(p).weight() : BigInteger.ZERO;
        List<Support> supports = a.practices().get(p);
        String date = observed ? supports.get(supports.size() - 1).date().toString() : reference;
        EvidenceDecision decision = counted ? EvidenceDecision.PRACTICE_MET : EvidenceDecision.PRACTICE_NOT_MET;
        items.add(person(a.subject(), date, p.name(), decision, reason, points));
        for (Support event : supports) {
            items.add(new EvidenceItem(
                    EvidenceSubjectKind.PERSON,
                    a.subject().personKey(),
                    event.sourceRef(),
                    event.date().toString(),
                    p.name(),
                    EvidenceDecision.SUPPORTING_EVENT,
                    reason,
                    null,
                    event.cnes(),
                    event.ine(),
                    event.cbo(),
                    event.model()));
        }
    }

    private static EvidenceItem person(
            Subject s, String date, String component, EvidenceDecision decision, String reason, BigInteger points) {
        return new EvidenceItem(
                EvidenceSubjectKind.PERSON,
                s.personKey(),
                null,
                date,
                component,
                decision,
                reason,
                points,
                s.cnes(),
                s.ine(),
                null,
                null);
    }
}
