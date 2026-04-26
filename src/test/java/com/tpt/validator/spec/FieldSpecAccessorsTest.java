package com.tpt.validator.spec;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FieldSpecAccessorsTest {

    @Test
    void everyAccessorReturnsConstructorInputs() {
        Map<Profile, Flag> flags = new EnumMap<>(Profile.class);
        flags.put(Profile.SOLVENCY_II, Flag.M);
        flags.put(Profile.IORP_EIOPA_ECB, Flag.C);
        flags.put(Profile.NW_675, Flag.O);

        CodificationDescriptor codif = new CodificationDescriptor(
                CodificationKind.NUMERIC, Optional.of(7),
                List.of(new CodificationDescriptor.ClosedListEntry("1", "yes")), "raw codif");

        FieldSpec spec = new FieldSpec(
                "12_CIC_code", "Position / X", "definition text", "comment text",
                "raw codif text", codif, flags, Set.of("CIC1", "CIC2"), 99);

        assertThat(spec.numData()).isEqualTo("12_CIC_code");
        assertThat(spec.numKey()).isEqualTo("12");
        assertThat(spec.fundXmlPath()).isEqualTo("Position / X");
        assertThat(spec.definition()).isEqualTo("definition text");
        assertThat(spec.comment()).isEqualTo("comment text");
        assertThat(spec.codificationRaw()).isEqualTo("raw codif text");
        assertThat(spec.codification()).isSameAs(codif);
        assertThat(spec.applicableCic()).containsExactlyInAnyOrder("CIC1", "CIC2");
        assertThat(spec.sourceRow()).isEqualTo(99);
        assertThat(spec.flag(Profile.SOLVENCY_II)).isEqualTo(Flag.M);
        assertThat(spec.flag(Profile.IORP_EIOPA_ECB)).isEqualTo(Flag.C);
        assertThat(spec.flag(Profile.NW_675)).isEqualTo(Flag.O);
    }

    @Test
    void closedListEntryAccessorsExposeCodeAndLabel() {
        CodificationDescriptor.ClosedListEntry e =
                new CodificationDescriptor.ClosedListEntry("1", "label");
        assertThat(e.code()).isEqualTo("1");
        assertThat(e.label()).isEqualTo("label");
    }
}
