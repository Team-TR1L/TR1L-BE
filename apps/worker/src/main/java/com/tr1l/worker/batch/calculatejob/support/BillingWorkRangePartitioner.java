package com.tr1l.worker.batch.calculatejob.support;

import com.tr1l.billing.application.port.out.WorkDocRangePort;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Range-based partitioning
 */
public class BillingWorkRangePartitioner implements Partitioner {

    private final WorkDocRangePort rangePort;
    private final String workerIdPrefix;
    private final YearMonth billingMonth;
    private final int gridSize;

    public BillingWorkRangePartitioner(
            WorkDocRangePort rangePort,
            String workerIdPrefix,
            YearMonth billingMonth,
            int gridSize
    ) {
        this.rangePort = rangePort;
        this.workerIdPrefix = workerIdPrefix;
        this.billingMonth = billingMonth;
        this.gridSize = gridSize;
    }

    @Override
    public Map<String, ExecutionContext> partition(int ignoredGridSize) {
        int effectiveGridSize = gridSize > 0 ? gridSize : ignoredGridSize;
        WorkDocRangePort.UserIdRange range = rangePort.findUserIdRange(billingMonth);
        if (range == null || effectiveGridSize <= 0) {
            return Map.of();
        }

        long min = range.minUserId();
        long max = range.maxUserId();
        if (min > max) {
            return Map.of();
        }

        long total = max - min + 1;
        long chunk = (total + effectiveGridSize - 1) / effectiveGridSize;

        Map<String, ExecutionContext> result = new HashMap<>();
        for (int i = 0; i < effectiveGridSize; i++) {
            long start = min + (chunk * i);
            if (start > max) {
                break;
            }
            long end = Math.min(max, start + chunk - 1);

            ExecutionContext ctx = new ExecutionContext();
            ctx.putString("workerId", workerIdPrefix + "-" + i);
            ctx.putLong("userIdStart", start);
            ctx.putLong("userIdEnd", end);
            result.put("partition-" + i, ctx);
        }

        return result;
    }
}
