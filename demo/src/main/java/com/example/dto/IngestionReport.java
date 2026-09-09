package com.example.dto;

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

    /**
     * Formats this report as a high-visibility, search-friendly banner
     * tagged with [ETL-RECONCILE] for easy log filtering.
     */
    public String toFormattedBanner() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[ETL-RECONCILE] ======================================================================\n");
        sb.append("[ETL-RECONCILE] Meet: ").append(meetName != null && !meetName.isEmpty() ? meetName : "Unknown Meet");
        if (meetDate != null && !meetDate.isEmpty()) {
            sb.append(" (").append(meetDate).append(")");
        }
        sb.append("\n");

        if (isBalanced) {
            sb.append("[ETL-RECONCILE] Status: [OK] 100% of rows accounted for\n");
        } else {
            sb.append("[ETL-RECONCILE] Status: [MISMATCH DETECTED] ")
              .append(Math.abs(discrepancy))
              .append(" row(s) unaccounted for!\n");
        }

        sb.append("[ETL-RECONCILE] ----------------------------------------------------------------------\n");
        sb.append(String.format("[ETL-RECONCILE]   Total Data Rows       : %d%n", totalDataRows));
        sb.append(String.format("[ETL-RECONCILE]     \u251c\u2500\u2500 Saved to DB         : %d%n", savedToDb));
        sb.append(String.format("[ETL-RECONCILE]     \u251c\u2500\u2500 Already in DB       : %d%n", alreadyInDb));
        sb.append(String.format("[ETL-RECONCILE]     \u251c\u2500\u2500 Skipped (No Time)   : %d (DNF/DNS/DQ/empty)%n", skippedNoTime));
        sb.append(String.format("[ETL-RECONCILE]     \u2514\u2500\u2500 Skipped (No Link)   : %d (Unattached/Club/No Profile)%n", skippedNoLink));
        sb.append(String.format("[ETL-RECONCILE]   Discrepancy           : %d row(s)%n", discrepancy));

        if (anomalies != null && !anomalies.isEmpty()) {
            sb.append("[ETL-RECONCILE] ----------------------------------------------------------------------\n");
            sb.append("[ETL-RECONCILE] Anomalies / Warnings (").append(anomalies.size()).append("):\n");
            for (String anomaly : anomalies) {
                sb.append("[ETL-RECONCILE]   * ").append(anomaly).append("\n");
            }
        }
        sb.append("[ETL-RECONCILE] ======================================================================\n");
        return sb.toString();
    }
}
