package ru.herplun;

import java.util.UUID;

/** Активная проверка игрока на читы. */
final class Check {
    final UUID target;
    final String targetName;
    final UUID moderator;
    long endTime;

    Check(UUID target, String targetName, UUID moderator, long endTime) {
        this.target = target;
        this.targetName = targetName;
        this.moderator = moderator;
        this.endTime = endTime;
    }
}
