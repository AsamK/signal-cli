package org.asamk.signal.manager.internal;

import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.jobs.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JobExecutor implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(JobExecutor.class);
    private final Context context;
    private final ExecutorService executorService;
    private Job running;
    private final Queue<Job> queue = new ArrayDeque<>();

    public JobExecutor(final Context context) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
    }

    public void enqueueJob(Job job) {
        if (executorService.isShutdown()) {
            logger.debug("Not enqueuing {} job, shutting down", job.getClass().getSimpleName());
            return;
        }

        synchronized (queue) {
            logger.trace("Enqueuing {} job", job.getClass().getSimpleName());
            queue.add(job);
        }

        runNextJob();
    }

    private void runNextJob() {
        Job job;
        synchronized (queue) {
            if (running != null) {
                return;
            }
            job = queue.poll();
            running = job;
        }

        if (job == null) {
            synchronized (this) {
                this.notifyAll();
            }
            return;
        }
        logger.debug("Running {} job", job.getClass().getSimpleName());
        executorService.execute(() -> {
            try {
                job.run(context);
            } catch (Throwable e) {
                logger.warn("Job {} failed", job.getClass().getSimpleName(), e);
            } finally {
                synchronized (queue) {
                    running = null;
                }
                runNextJob();
            }
        });
    }

    @Override
    public void close() {
        final boolean queueEmpty;
        synchronized (queue) {
            queueEmpty = queue.isEmpty();
        }
        if (queueEmpty) {
            executorService.close();
            return;
        }
        synchronized (this) {
            try {
                this.wait();
            } catch (InterruptedException ignored) {
            }
        }
        executorService.close();
    }
}
