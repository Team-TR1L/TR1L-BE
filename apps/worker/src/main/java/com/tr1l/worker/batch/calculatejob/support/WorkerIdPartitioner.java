package com.tr1l.worker.batch.calculatejob.support;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

public class WorkerIdPartitioner implements Partitioner {

    private final String workerIdPrefix;
    private final int gridSize;

    public WorkerIdPartitioner(String workerIdPrefix, int gridSize) {
        this.workerIdPrefix = workerIdPrefix;
        this.gridSize = gridSize;
    }

    @Override
    public Map<String, ExecutionContext> partition(int ignored) {
        Map<String, ExecutionContext> result = new HashMap<>();
        for (int i = 0; i < gridSize; i++) {
            ExecutionContext ctx = new ExecutionContext();
            ctx.putString("workerId", workerIdPrefix + "-" + i);
            ctx.putInt("partitionIndex", i);
            ctx.putInt("partitionCount", gridSize);
            result.put("partition-" + i, ctx);
        }
        return result;
    }
}
