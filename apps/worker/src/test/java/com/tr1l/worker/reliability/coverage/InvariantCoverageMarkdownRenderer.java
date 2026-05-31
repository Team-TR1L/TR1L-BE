package com.tr1l.worker.reliability.coverage;

// 블로그와 PR에 붙일 coverage matrix 표 생성
public final class InvariantCoverageMarkdownRenderer {
    public String render(InvariantCoverageMatrix matrix) {
        StringBuilder builder = new StringBuilder();

        builder.append("# Job1 Invariant Coverage Matrix\n\n");
        builder.append("- scenario count: ").append(matrix.scenarioCount()).append("\n");
        builder.append("- invariant count: ").append(matrix.invariantCount()).append("\n");
        builder.append("- uncovered invariants: ").append(matrix.uncoveredInvariantIds().size()).append("\n");
        builder.append("- invalid references: ").append(matrix.unknownInvariantReferences().size()).append("\n\n");

        appendTable(builder, matrix);
        appendWarnings(builder, matrix);

        return builder.toString();
    }

    private void appendTable(StringBuilder builder, InvariantCoverageMatrix matrix) {
        builder.append("| invariant | category |");

        for (String scenarioId : matrix.scenarioIds()) {
            builder.append(" ").append(scenarioId).append(" |");
        }

        builder.append(" coverage |\n");
        builder.append("| --- | --- |");

        for (int i = 0; i < matrix.scenarioIds().size(); i++) {
            builder.append(" --- |");
        }

        builder.append(" ---: |\n");

        for (InvariantCoverageRow row : matrix.rows()) {
            builder.append("| ")
                    .append(row.invariantId())
                    .append(" ")
                    .append(row.title())
                    .append(" | ")
                    .append(row.category())
                    .append(" |");

            for (String scenarioId : matrix.scenarioIds()) {
                builder.append(" ")
                        .append(row.coveredBy(scenarioId) ? "O" : "-")
                        .append(" |");
            }

            builder.append(" ")
                    .append(row.coverageCount())
                    .append("/")
                    .append(matrix.scenarioCount())
                    .append(" |\n");
        }
    }

    private void appendWarnings(StringBuilder builder, InvariantCoverageMatrix matrix) {
        if (matrix.uncoveredInvariantIds().isEmpty()
                && matrix.scenariosWithoutInvariants().isEmpty()
                && matrix.unknownInvariantReferences().isEmpty()) {
            return;
        }

        builder.append("\n## Review Required\n\n");

        if (!matrix.uncoveredInvariantIds().isEmpty()) {
            builder.append("- uncovered invariants: ")
                    .append(matrix.uncoveredInvariantIds())
                    .append("\n");
        }

        if (!matrix.scenariosWithoutInvariants().isEmpty()) {
            builder.append("- scenarios without invariants: ")
                    .append(matrix.scenariosWithoutInvariants())
                    .append("\n");
        }

        if (!matrix.unknownInvariantReferences().isEmpty()) {
            builder.append("- unknown invariant references: ")
                    .append(matrix.unknownInvariantReferences())
                    .append("\n");
        }
    }
}
