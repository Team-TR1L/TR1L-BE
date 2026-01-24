package com.tr1l.dispatch.domain.model.vo;

import com.tr1l.dispatch.application.command.DispatchCommand;

import java.util.Collections;
import java.util.List;

public record BatchResult(List<DispatchCommand> commands, Long lastUserId) {

    public static BatchResult of(
            List<DispatchCommand> commands,
            Long lastUserId
    ) {
        return new BatchResult(commands, lastUserId);
    }

    public static BatchResult empty(Long lastUserId) {
        return new BatchResult(Collections.emptyList(), lastUserId);
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }
}