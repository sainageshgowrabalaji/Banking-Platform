package com.sainagesh.bank.payments.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

/**
 * Banking days and cut-off times in the United States.
 *
 * <p>The batch networks do not run on weekends or on Federal Reserve holidays, and each day has a
 * cut-off time. A payment that arrives after the cut-off, or on a day the network is closed, goes out
 * on the next business day. This is why a payment sent on Friday evening arrives on Tuesday.
 */
public final class BusinessCalendar {

    /** The time zone the US payment networks keep their clocks in. */
    public static final ZoneId EASTERN = ZoneId.of("America/New_York");

    private final LocalTime cutOff;

    /** @param cutOff the last moment of a business day at which a payment still goes out that day */
    public BusinessCalendar(LocalTime cutOff) {
        this.cutOff = cutOff;
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays(date.getYear()).contains(date);
    }

    public LocalDate nextBusinessDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (!isBusinessDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /** The business day a payment received at this moment is processed on. */
    public LocalDate processingDate(Instant received) {
        ZonedDateTime local = received.atZone(EASTERN);
        LocalDate date = local.toLocalDate();
        if (isBusinessDay(date) && !local.toLocalTime().isAfter(cutOff)) {
            return date;
        }
        return nextBusinessDay(date);
    }

    /** The day a batch payment received at this moment settles. One business day after it is processed. */
    public LocalDate batchSettlementDate(Instant received) {
        return nextBusinessDay(processingDate(received));
    }

    /**
     * The Federal Reserve holidays of a year. A holiday on a Sunday is kept on the Monday after. A
     * holiday on a Saturday is not moved, the Reserve Banks simply stay open on the Friday.
     */
    static Set<LocalDate> holidays(int year) {
        Set<LocalDate> days = new HashSet<>();
        days.add(observed(LocalDate.of(year, Month.JANUARY, 1)));
        days.add(nth(year, Month.JANUARY, DayOfWeek.MONDAY, 3)); // Martin Luther King Jr. Day
        days.add(nth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3)); // Washington's Birthday
        days.add(LocalDate.of(year, Month.MAY, 31).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))); // Memorial Day
        days.add(observed(LocalDate.of(year, Month.JUNE, 19))); // Juneteenth
        days.add(observed(LocalDate.of(year, Month.JULY, 4)));
        days.add(nth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1)); // Labor Day
        days.add(nth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2)); // Columbus Day
        days.add(observed(LocalDate.of(year, Month.NOVEMBER, 11))); // Veterans Day
        days.add(nth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4)); // Thanksgiving
        days.add(observed(LocalDate.of(year, Month.DECEMBER, 25)));
        return days;
    }

    private static LocalDate observed(LocalDate holiday) {
        return holiday.getDayOfWeek() == DayOfWeek.SUNDAY ? holiday.plusDays(1) : holiday;
    }

    private static LocalDate nth(int year, Month month, DayOfWeek day, int n) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, day));
    }
}
