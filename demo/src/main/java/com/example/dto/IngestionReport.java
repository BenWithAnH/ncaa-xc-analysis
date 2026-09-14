package com.example.dto;

import java.time.Instant;
import java.util.List;

/**
 * Data transfer object representing the ETL reconciliation report for a meet.
 * Balances total extracted table rows against saved, existing, and filtered rows.
 */
public record IngestionReport(
        String meetName,
        String meetUrl,
        String meetDate,
        int totalDataRows,
        int savedToDb,
        int alreadyInDb,
        int skippedNoTime,
        int skippedNoLink,
        int discrepancy,
        boolean isBalanced,
        List<String> anomalies
) {

    public static final String CSV_HEADER =
            "timestamp,meet_name,meet_url,meet_date,status,rows_touched,athletes_extracted,athletes_saved,athletes_already_in_db,skipped_no_time,skipped_no_link,discrepancy,is_balanced,errors_and_anomalies";

    /** Rows that produced a usable athlete (time + profile link). */
    public int extracted() {
        return savedToDb + alreadyInDb;
    }

    /**
     * Determines overall status: ERROR if failed or critical error present,
     * WARNING if unbalanced or anomalies exist, SUCCESS if fully reconciled.
     */
    public String status() {
        if (totalDataRows == 0 && anomalies != null && !anomalies.isEmpty()) {
            return "ERROR";
        }
        if (anomalies != null && anomalies.stream().anyMatch(a -> a.toLowerCase().contains("error") || a.toLowerCase().contains("failed") || a.toLowerCase().contains("exception"))) {
            return "ERROR";
        }
        if (!isBalanced || (anomalies != null && !anomalies.isEmpty())) {
            return "WARNING";
        }
        return "SUCCESS";
    }

    public static String csvHeader() {
        return CSV_HEADER;
    }

    public String toCsvRow() {
        return toCsvRow(Instant.now());
    }

    public String toCsvRow(Instant timestamp) {
        return String.join(",",
                csvEscape(timestamp != null ? timestamp.toString() : ""),
                csvEscape(meetName),
                csvEscape(meetUrl),
                csvEscape(meetDate),
                csvEscape(status()),
                Integer.toString(totalDataRows),
                Integer.toString(extracted()),
                Integer.toString(savedToDb),
                Integer.toString(alreadyInDb),
                Integer.toString(skippedNoTime),
                Integer.toString(skippedNoLink),
                Integer.toString(discrepancy),
                Boolean.toString(isBalanced),
                csvEscape(anomalies != null ? String.join("; ", anomalies) : "")
        );
    }

    /**
     * One-line terminal summary logging meet details, touched/exported athletes, and any errors.
     */
    public String toCompactLine() {
        String st = status();
        String name = (meetName != null && !meetName.isEmpty()) ? meetName : "Unknown Meet";
        String date = (meetDate != null && !meetDate.isEmpty()) ? meetDate : "TBD";
        StringBuilder sb = new StringBuilder();
        sb.append("[MEET-LOG] [").append(st).append("] ")
          .append("Meet: \"").append(name).append("\" (").append(date).append(")")
          .append(" | Touched: ").append(totalDataRows)
          .append(" | Extracted: ").append(extracted())
          .append(" | Saved/Exported: ").append(savedToDb)
          .append(" | Already in DB: ").append(alreadyInDb)
          .append(" | Skipped: ").append(skippedNoTime + skippedNoLink)
          .append(" (").append(skippedNoTime).append(" no-time, ").append(skippedNoLink).append(" no-link)");

        if (discrepancy != 0) {
            sb.append(" | Discrepancy: ").append(discrepancy);
        }

        if (anomalies != null && !anomalies.isEmpty()) {
            sb.append(" | Errors/Warnings: ").append(String.join("; ", anomalies));
        } else {
            sb.append(" | Errors: None");
        }

        return sb.toString();
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * Formats this report as a high-visibility banner when deep diagnostics are needed.
     */
    public String toFormattedBanner() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[MEET-LOG-DETAIL] ======================================================================\n");
        sb.append("[MEET-LOG-DETAIL] Meet: ").append(meetName != null && !meetName.isEmpty() ? meetName : "Unknown Meet");
        if (meetDate != null && !meetDate.isEmpty()) {
            sb.append(" (").append(meetDate).append(")");
        }
        if (meetUrl != null && !meetUrl.isEmpty()) {
            sb.append(" | URL: ").append(meetUrl);
        }
        sb.append("\n");

        sb.append("[MEET-LOG-DETAIL] Status: [").append(status()).append("] ");
        if (isBalanced) {
            sb.append("100% of rows accounted for\n");
        } else {
            sb.append(Math.abs(discrepancy)).append(" row(s) unaccounted for!\n");
        }

        sb.append("[MEET-LOG-DETAIL] ----------------------------------------------------------------------\n");
        sb.append(String.format("[MEET-LOG-DETAIL]   Rows Touched          : %d%n", totalDataRows));
        sb.append(String.format("[MEET-LOG-DETAIL]   Athletes Extracted    : %d%n", extracted()));
        sb.append(String.format("[MEET-LOG-DETAIL]     ├── Saved/Exported  : %d%n", savedToDb));
        sb.append(String.format("[MEET-LOG-DETAIL]     ├── Already in DB   : %d%n", alreadyInDb));
        sb.append(String.format("[MEET-LOG-DETAIL]     ├── Skipped No Time : %d (DNF/DNS/DQ/empty)%n", skippedNoTime));
        sb.append(String.format("[MEET-LOG-DETAIL]     └── Skipped No Link : %d (Unattached/Club)%n", skippedNoLink));
        sb.append(String.format("[MEET-LOG-DETAIL]   Discrepancy           : %d row(s)%n", discrepancy));

        if (anomalies != null && !anomalies.isEmpty()) {
            sb.append("[MEET-LOG-DETAIL] ----------------------------------------------------------------------\n");
            sb.append("[MEET-LOG-DETAIL] Errors / Warnings (").append(anomalies.size()).append("):\n");
            for (String anomaly : anomalies) {
                sb.append("[MEET-LOG-DETAIL]   * ").append(anomaly).append("\n");
            }
        }
        sb.append("[MEET-LOG-DETAIL] ======================================================================\n");
        return sb.toString();
    }
}
