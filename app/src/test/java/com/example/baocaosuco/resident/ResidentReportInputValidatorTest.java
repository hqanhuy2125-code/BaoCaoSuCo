package com.example.baocaosuco.resident;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumSet;

public class ResidentReportInputValidatorTest {

    @Test
    public void validInput_hasNoMissingFields() {
        assertTrue(ResidentReportInputValidator.findMissingFields(
                "Hong dien", "Den hanh lang bi tat", "Tang 2", "URGENT").isEmpty());
    }

    @Test
    public void nullAndBlankValues_reportAllMissingFields() {
        assertEquals(EnumSet.allOf(ResidentReportInputValidator.Field.class),
                ResidentReportInputValidator.findMissingFields(null, "", "  ", "\t\n"));
    }

    @Test
    public void eachMissingField_isIdentifiedIndependently() {
        assertEquals(EnumSet.of(ResidentReportInputValidator.Field.DESCRIPTION),
                ResidentReportInputValidator.findMissingFields(
                        "Hong nuoc", "  ", "Phong 301", "NORMAL"));
        assertEquals(EnumSet.of(ResidentReportInputValidator.Field.PRIORITY),
                ResidentReportInputValidator.findMissingFields(
                        "Hong nuoc", "Ro ri", "Phong 301", null));
    }

    @Test
    public void priorityValueIsOnlyCheckedForPresence_pendingContract() {
        assertTrue(ResidentReportInputValidator.findMissingFields(
                "Hong nuoc", "Ro ri", "Phong 301", "TO_BE_APPROVED").isEmpty());
    }

    @Test
    public void nonBreakingAndFullWidthSpacesAreBlank() {
        assertEquals(EnumSet.allOf(ResidentReportInputValidator.Field.class),
                ResidentReportInputValidator.findMissingFields(
                        "\u00A0", "\u2007", "\u202F", "\u3000"));
    }

    @Test
    public void eachCallReturnsIndependentResult() {
        EnumSet<ResidentReportInputValidator.Field> first =
                ResidentReportInputValidator.findMissingFields("", "", "", "");
        first.clear();
        assertEquals(EnumSet.allOf(ResidentReportInputValidator.Field.class),
                ResidentReportInputValidator.findMissingFields("", "", "", ""));
    }
}
