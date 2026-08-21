package io.akka.coroot.domain;

/**
 * A verdict the auditor already reached. The three carried quantities are used by
 * different checks and are meaningless for the rest; zero means "nothing to show".
 */
public record Check(CheckId id, Status status, float value, long count, long desired, int distinctItems) {}
