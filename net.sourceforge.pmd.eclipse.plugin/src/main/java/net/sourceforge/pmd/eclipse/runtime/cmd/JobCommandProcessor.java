/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.runtime.cmd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a particular processor for Eclipse in order to handle long running
 * commands.
 *
 * @author Philippe Herlin
 *
 */
public class JobCommandProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(JobCommandProcessor.class);
    private final Map<AbstractDefaultCommand, Job> jobs = Collections
            .synchronizedMap(new HashMap<AbstractDefaultCommand, Job>());

    private static ConcurrentLinkedQueue<Job> outstanding = new ConcurrentLinkedQueue<Job>();
    private static AtomicInteger count = new AtomicInteger();
    
    private static final JobCommandProcessor INSTANCE = new JobCommandProcessor();

    public static JobCommandProcessor getInstance() {
        return INSTANCE;
    }

    public void processCommand(final AbstractDefaultCommand aCommand) {
        LOG.debug("Beginning job command {}", aCommand.getName());

        if (!aCommand.isReadyToExecute()) {
            throw new IllegalStateException();
        }

        final Job job = new Job(aCommand.getName()) {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    if (aCommand instanceof AbstractDefaultCommand) {
                        ((AbstractDefaultCommand) aCommand).setMonitor(monitor);
                    }
                    long start = System.currentTimeMillis();
                    aCommand.execute();
                    long duration = System.currentTimeMillis() - start;
                    LOG.debug("Command {} executed in {} ms", aCommand.getName(), duration);
                } catch (RuntimeException e) {
                    LOG.error("Error executing command {}: {}", aCommand.getName(), e.toString(), e);
                }

                synchronized (outstanding) {
                    count.decrementAndGet();
                    Job job = outstanding.poll();
                    if (job != null) {
                        job.schedule();
                    }
                }
                return Status.OK_STATUS;
            }
        };

        if (aCommand instanceof AbstractDefaultCommand) {
            job.setUser(((AbstractDefaultCommand) aCommand).isUserInitiated());
        }

        synchronized (outstanding) {
            if (count.incrementAndGet() > 10) {
                // too many already running, put in a queue to run later
                outstanding.add(job);
            } else {
                job.schedule();
            }
        }
        this.addJob(aCommand, job);
        LOG.debug("Ending job command {}", aCommand.getName());
    }

    public void waitCommandToFinish(final AbstractDefaultCommand aCommand) {
        final Job job = this.jobs.get(aCommand);
        if (job != null) {
            try {
                job.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        } else {
            // no specific command given - wait for all jobs to finish
            clearTerminatedJobs();
            Collection<Job> runningJobs = new ArrayList<>(this.jobs.values());
            LOG.debug("Waiting for {} jobs to finish...", runningJobs.size());
            for (Job runningJob : runningJobs) {
                try {
                    runningJob.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            clearTerminatedJobs();
            LOG.debug("All jobs have finished.");
        }

    }

    /**
     * Add a job to the map. Also, clear all finished jobs
     * 
     * @param command
     *            for which to keep the job
     * @param job
     *            a job to keep until it is finished
     */
    private void addJob(final AbstractDefaultCommand command, final Job job) {
        this.jobs.put(command, job);
        clearTerminatedJobs();
    }

    private void clearTerminatedJobs() {
        Set<AbstractDefaultCommand> keySet = this.jobs.keySet();
        synchronized (this.jobs) {
            final Iterator<AbstractDefaultCommand> i = keySet.iterator();
            while (i.hasNext()) {
                final AbstractDefaultCommand aCommand = i.next();
                final Job aJob = this.jobs.get(aCommand);
                if (aJob == null || aJob.getResult() != null) {
                    i.remove();
                }
            }
        }
    }
}
