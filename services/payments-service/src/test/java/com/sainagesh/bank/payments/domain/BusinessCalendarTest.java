package com.sainagesh.bank.payments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class BusinessCalendarTest {

    private final BusinessCalendar calendar = new BusinessCalendar(LocalTime.of(17, 0));

    private static Instant eastern(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(BusinessCalendar.EASTERN).toInstant();
    }

    @Test
    void weekendsAreNotBusinessDays() {
        assertTrue(calendar.isBusinessDay(LocalDate.of(2026, 10, 2))); // Friday
        assertFalse(calendar.isBusinessDay(LocalDate.of(2026, 10, 3))); // Saturday
        assertFalse(calendar.isBusinessDay(LocalDate.of(2026, 10, 4))); // Sunday
        assertTrue(calendar.isBusinessDay(LocalDate.of(2026, 10, 5))); // Monday
    }

    @Test
    void federalReserveHolidaysAreNotBusinessDays() {
        assertFalse(calendar.isBusinessDay(LocalDate.of(2026, 1, 1))); // New Year's Day
        assertFalse(calendar.isBusinessDay(LocalDate.of(2026, 1, 19))); // Martin Luther King Jr. Day
        assertFalse(calendar.isBusinessDay(LocalDate.of(2026, 5, 25))); // Memorial Day
        assertFalse(calendar.isBusinessDay(LocalDate.of(2026, 10, 12))); // Columbus Day
        assertFalse(calendar.isBusinessDay(LocalDate.of(2026, 11, 26))); // Thanksgiving
        assertFalse(calendar.isBusinessDay(LocalDate.of(2026, 12, 25))); // Christmas Day
    }

    @Test
    void aHolidayOnASundayIsKeptOnTheMonday() {
        // 4 July 2027 is a Sunday.
        assertFalse(calendar.isBusinessDay(LocalDate.of(2027, 7, 5)));
    }

    @Test
    void aHolidayOnASaturdayDoesNotCloseTheFriday() {
        // 4 July 2026 is a Saturday. The Reserve Banks are open on Friday 3 July.
        assertTrue(calendar.isBusinessDay(LocalDate.of(2026, 7, 3)));
    }

    @Test
    void aPaymentBeforeTheCutOffIsProcessedTheSameDay() {
        assertEquals(LocalDate.of(2026, 10, 6), calendar.processingDate(eastern("2026-10-06T16:59:00")));
        assertEquals(LocalDate.of(2026, 10, 6), calendar.processingDate(eastern("2026-10-06T17:00:00")));
    }

    @Test
    void aPaymentAfterTheCutOffWaitsForTheNextBusinessDay() {
        assertEquals(LocalDate.of(2026, 10, 7), calendar.processingDate(eastern("2026-10-06T17:01:00")));
    }

    @Test
    void aPaymentOnFridayEveningIsProcessedOnTuesdayWhenMondayIsAHoliday() {
        // Friday 9 October 2026 after the cut-off. Monday 12 October is Columbus Day.
        assertEquals(LocalDate.of(2026, 10, 13), calendar.processingDate(eastern("2026-10-09T18:00:00")));
        // And it settles one business day after that.
        assertEquals(LocalDate.of(2026, 10, 14), calendar.batchSettlementDate(eastern("2026-10-09T18:00:00")));
    }

    @Test
    void theCutOffIsEasternTimeWhateverTheServerClockSays() {
        // 21:30 UTC on 6 October is 17:30 in New York, which is after the cut-off.
        assertEquals(LocalDate.of(2026, 10, 7), calendar.processingDate(Instant.parse("2026-10-06T21:30:00Z")));
        // 20:30 UTC is 16:30 in New York, which is before it.
        assertEquals(LocalDate.of(2026, 10, 6), calendar.processingDate(Instant.parse("2026-10-06T20:30:00Z")));
    }
}
