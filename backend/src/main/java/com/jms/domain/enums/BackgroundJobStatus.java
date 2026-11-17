package com.jms.domain.enums;

public enum BackgroundJobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    RETRYING,
    DEAD_LETTER
}
