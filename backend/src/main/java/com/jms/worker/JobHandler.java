package com.jms.worker;

import com.jms.domain.enums.BackgroundJobType;
import com.jms.domain.model.BackgroundJob;

/**
 * Strategy contract implemented by each asynchronous domain worker.
 */
public interface JobHandler {

    BackgroundJobType getType();

    /**
     * Executes the job payload and returns a concise execution summary persisted for observability.
     */
    String handle(BackgroundJob job) throws Exception;
}
