package com.thunder.wildernessodysseyapi.temporalrift.echo;

import java.util.LinkedHashSet;
import java.util.Set;

/** Independent, evidence-gated findings; a later finding never implies an unobserved earlier one. */
public enum EchoDiscoveryStage {
    UNEXPLORED(0, "unexplored", "I have no recorded field observations from Echo Earth yet."),
    TERRAIN_SIMILARITY(1, "terrain_similarity", "Geological similarities detected. The landscape looks familiar, but its history is still unknown."),
    TERRAIN_CORRESPONDENCE(2, "terrain_correspondence", "Repeated terrain correspondence confirmed at matching coordinates. The geological alignment exceeds a single coincidence."),
    INDEPENDENT_HISTORY(4, "independent_history", "Structural divergence detected. Echo Earth has evidence of a history different from our Earth's."),
    MATERIAL_SYNCHRONIZATION(8, "material_synchronization", "Material synchronization detected between realities. A distorted trace of construction from our Earth has appeared here."),
    ALIGNMENT_HYPOTHESIS(16, "alignment_hypothesis", "The fracture may be doing more than connecting these worlds. The material overlap suggests it is forcing them into alignment; that remains a hypothesis.");

    private final int evidenceBit;
    private final String id;
    private final String finding;

    EchoDiscoveryStage(int evidenceBit, String id, String finding) {
        this.evidenceBit = evidenceBit;
        this.id = id;
        this.finding = finding;
    }

    /** Stable persistence bit, independent of enum declaration order. */
    public int evidenceBit() { return evidenceBit; }

    /** Literal authoritative tag passed to A.E.T.H.E.R's existing context transport. */
    public String contextTag() { return "echo:discovery:" + id; }

    /** Player-facing finding containing only evidence available at this discovery. */
    public String finding() { return finding; }

    /** Derives the alignment hypothesis only after both actual transfer and fracture observations. */
    public static int withAlignmentHypothesis(int evidence, boolean fractureObserved) {
        int known = evidence & 31;
        if (fractureObserved && (known & MATERIAL_SYNCHRONIZATION.evidenceBit) != 0) {
            known |= ALIGNMENT_HYPOTHESIS.evidenceBit;
        }
        return known;
    }

    /** Highest earned finding, without filling gaps in the independent evidence set. */
    public static EchoDiscoveryStage fromEvidence(int evidence) {
        EchoDiscoveryStage result = UNEXPLORED;
        for (EchoDiscoveryStage stage : values()) {
            if (stage.evidenceBit != 0 && (evidence & stage.evidenceBit) != 0) {
                result = stage;
            }
        }
        return result;
    }

    /** Returns only explicitly earned findings, keeping missing structural history unknown. */
    public static Set<String> contextTags(int evidence) {
        Set<String> tags = new LinkedHashSet<>();
        for (EchoDiscoveryStage stage : values()) {
            if (stage.evidenceBit != 0 && (evidence & stage.evidenceBit) != 0) {
                tags.add(stage.contextTag());
            }
        }
        return Set.copyOf(tags);
    }

    /** Resolves the highest literal discovery; a dimension tag alone does not unlock lore. */
    public static EchoDiscoveryStage fromContext(Set<String> tags) {
        int evidence = 0;
        for (EchoDiscoveryStage stage : values()) {
            if (tags.contains(stage.contextTag())) {
                evidence |= stage.evidenceBit;
            }
        }
        return fromEvidence(evidence);
    }
}
