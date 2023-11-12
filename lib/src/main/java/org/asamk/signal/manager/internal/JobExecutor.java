package org.asamk.signal.manager.internal;

import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.jobs.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JobExecutor implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(JobExecutor.class);
    private final Context context;
    private final ExecutorService executorService;

    public JobExecutor(final Context context) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
    }

    public void enqueueJob(Job job) {
        logger.debug("Enqueuing {} job", job.getClass().getSimpleName());

        executorService.execute(() -> job.run(context));
    }

    @Override
    public void close() {
        executorService.close();
    }
}
