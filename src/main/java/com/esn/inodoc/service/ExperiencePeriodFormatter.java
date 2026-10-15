package com.esn.inodoc.service;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ExperiencePeriodFormatter {
    private static final DateTimeFormatter MONTH_YEAR =
            DateTimeFormatter.ofPattern("MM/yyyy", Locale.FRENCH);

    private ExperiencePeriodFormatter() {
    }

    public static String formatDuration(LocalDate start, LocalDate end) {
        return formatDuration(start, end, false);
    }

    public static String formatDuration(LocalDate start, LocalDate end, boolean english) {
        if (start == null) {
            return "";
        }
        LocalDate finish = end == null ? LocalDate.now() : end;
        if (finish.isBefore(start)) {
            return "";
        }

        Period elapsed = Period.between(start, finish);
        int totalMonths = elapsed.getYears() * 12 + elapsed.getMonths();
        if (elapsed.getDays() > 0) {
            totalMonths++;
        }
        totalMonths = Math.max(totalMonths, 1);

        int years = totalMonths / 12;
        int months = totalMonths % 12;
        StringBuilder duration = new StringBuilder();
        if (years > 0) {
            duration.append(years).append(english ? (years == 1 ? " year" : " years")
                    : (years == 1 ? " an" : " ans"));
        }
        if (months > 0) {
            if (!duration.isEmpty()) {
                duration.append(' ');
            }
            duration.append(months).append(english ? (months == 1 ? " month" : " months") : " mois");
        }
        return duration.toString();
    }

    public static String formatPeriod(LocalDate start, LocalDate end) {
        return formatPeriod(start, end, false);
    }

    public static String formatPeriod(LocalDate start, LocalDate end, boolean english) {
        if (start == null && end == null) {
            return "";
        }
        if (start == null) {
            return (english ? "Until " : "Jusqu'à ") + MONTH_YEAR.format(end);
        }
        String startLabel = MONTH_YEAR.format(start);
        return end == null ? (english ? "Since " : "Depuis ") + startLabel
            : startLabel + " - " + MONTH_YEAR.format(end);
    }
}