package lt.ktu.paskutinisatsiskaitymas.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Destination for a finished particle measurement. */
interface ParticleStatsSink {
    /** Discards reports; used wherever measurements must not touch the console or disk. */
    ParticleStatsSink NONE = report -> { };

    void publish(ParticleStatsReport report);

    /** Prints the table and appends a CSV row to {@code -Dparticles.stats.csv} (default particle-stats.csv). */
    static ParticleStatsSink consoleAndCsv() {
        return consoleAndCsv(Path.of(System.getProperty("particles.stats.csv", "particle-stats.csv")));
    }

    static ParticleStatsSink consoleAndCsv(Path csv) {
        return report -> {
            System.out.println(report.toConsoleTable());
            try {
                appendCsv(csv, report);
                System.out.println("Appended particle statistics to " + csv.toAbsolutePath());
            } catch (IOException exception) {
                System.err.println("Could not write particle statistics to " + csv + ": " + exception.getMessage());
            }
        };
    }

    /** Appends one row, writing the header first when the file is new or empty. */
    static void appendCsv(Path csv, ParticleStatsReport report) throws IOException {
        boolean needsHeader = !Files.exists(csv) || Files.size(csv) == 0;
        String text = (needsHeader ? report.csvHeader() + "\n" : "") + report.toCsvRow() + "\n";
        Files.writeString(csv, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
