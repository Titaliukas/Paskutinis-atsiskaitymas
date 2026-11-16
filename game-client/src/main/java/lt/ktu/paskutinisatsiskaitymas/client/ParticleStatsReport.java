package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** One finished measurement run as an ordered list of named columns, rendered as a console table or a CSV row. */
record ParticleStatsReport(List<Map.Entry<String, String>> columns) {
    ParticleStatsReport {
        columns = List.copyOf(columns);
    }

    String value(String column) {
        return columns.stream().filter(entry -> entry.getKey().equals(column))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    String toConsoleTable() {
        int width = columns.stream().mapToInt(entry -> entry.getKey().length()).max().orElse(0);
        StringBuilder text = new StringBuilder("=== Particle statistics ===\n");
        for (Map.Entry<String, String> entry : columns) {
            text.append(String.format("  %-" + width + "s  %s%n", entry.getKey(), entry.getValue()));
        }
        return text.toString();
    }

    String csvHeader() {
        return columns.stream().map(entry -> escape(entry.getKey())).collect(Collectors.joining(","));
    }

    String toCsvRow() {
        return columns.stream().map(entry -> escape(entry.getValue())).collect(Collectors.joining(","));
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
