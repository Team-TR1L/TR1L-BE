package com.tr1l.dispatch.domain.model.vo;

import com.tr1l.dispatch.application.command.DispatchCommand;

import java.util.Collections;
import java.util.List;

public class BatchResult {

    private final List<DispatchCommand> commands;
    private final Long lastUserId;

    public BatchResult(List<DispatchCommand> commands, Long lastUserId) {
        this.commands = commands;
        this.lastUserId = lastUserId;
    }

    public static BatchResult of(
            List<DispatchCommand> commands,
            Long lastUserId
    ) {
        return new BatchResult(commands, lastUserId);
    }

    public static BatchResult empty(Long lastUserId) {
        return new BatchResult(Collections.emptyList(), lastUserId);
    }

    public List<DispatchCommand> commands() {
        return commands;
    }

    public Long lastUserId() {
        return lastUserId;
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }
}