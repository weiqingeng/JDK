/*
 * @(#)file      ThreadService.java
 * @(#)author    Sun Microsystems, Inc.
 * @(#)version   1.8
 * @(#)date      08/05/28
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */

package com.sun.jmx.snmp.tasks;

import java.util.ArrayList;
import com.sun.jmx.snmp.tasks.Task;
import com.sun.jmx.snmp.tasks.TaskServer;

/**
 * This class implements a {@link com.sun.jmx.snmp.tasks.TaskServer} over
 * a thread pool.
 * <p><b>This API is a Sun Microsystems internal API  and is subject 
 * to change without notice.</b></p>
 **/
public class ThreadService implements TaskServer {

    public ThreadService(int threadNumber) {
	if (threadNumber <= 0) {
	    throw new IllegalArgumentException("The thread number should bigger than zero.");
	}

	minThreads = threadNumber;
	threadList = new ExecutorThread[threadNumber];

// 	for (int i=0; i<threadNumber; i++) {
// 	    threadList[i] = new ExecutorThread();
// 	    threadList[i].start();
// 	}

	priority = Thread.currentThread().getPriority();
	cloader = Thread.currentThread().getContextClassLoader();

//System.out.println("---jsl: ThreadService: running threads = "+threadNumber);
    }

// public methods
// --------------

    /**
     * Submit a task to be executed.
     * Once a task is submitted, it is guaranteed that either
     * {@link com.sun.jmx.snmp.tasks.Task#run() task.run()} or 
     * {@link com.sun.jmx.snmp.tasks.Task#cancel() task.cancel()} will be called.
     * This implementation of TaskServer uses a thread pool to execute
     * the submitted tasks.
     * @param task The task to be executed.
     * @exception IllegalArgumentException if the submitted task is null.
     **/
    public void submitTask(Task task) throws IllegalArgumentException {
	submitTask((Runnable)task);
    }

    /**
     * Submit a task to be executed.
     * This implementation of TaskServer uses a thread pool to execute
     * the submitted tasks.
     * @param task The task to be executed.
     * @exception IllegalArgumentException if the submitted task is null.
     **/
    public void submitTask(Runnable task) throws IllegalArgumentException {
	stateCheck();

	if (task == null) {
	    throw new IllegalArgumentException("No task specified.");
	}

	synchronized(jobList) {
	    jobList.add(jobList.size(), task);
//System.out.println("jsl-ThreadService: added job "+addedJobs++);

	    jobList.notify();
	}

	createThread();
    }

    public Runnable removeTask(Runnable task) {
	stateCheck();

	Runnable removed = null;
	synchronized(jobList) {
	    int lg = jobList.indexOf(task);
	    if (lg >= 0) {
		removed = (Runnable)jobList.remove(lg);
	    }
	}
	if (removed != null && removed instanceof Task) 
	    ((Task) removed).cancel();
	return removed;
    }

    public void removeAll() {
	stateCheck();
	
	final Object[] jobs;
	synchronized(jobList) {
	    jobs = jobList.toArray();
	    jobList.clear();
	}
	final int len = jobs.length;
	for (int i=0; i<len ; i++) {
	    final Object o = jobs[i];
	    if (o!= null && o instanceof Task) ((Task)o).cancel();
	}
    }

    // to terminate
    public void terminate() {

	if (terminated == true) {
	    return;
	}

	terminated = true;

	synchronized(jobList) {
	    jobList.notifyAll();
	}

	removeAll();

	for (int i=0; i<currThreds; i++) {
	    try {
		threadList[i].interrupt();
	    } catch (Exception e) {
		// TODO
	    }
	}

	threadList = null;
    }

// private classes
// ---------------

    // A thread used to execute jobs
    //
    private class ExecutorThread extends Thread {
	public ExecutorThread() {
	    super(threadGroup, "ThreadService-"+counter++);
	    setDaemon(true);

	    // init
	    this.setPriority(priority);
	    this.setContextClassLoader(cloader);
 
	    idle++;
	}

	public void run() {

	    while(!terminated) {
		Runnable job = null;

		synchronized(jobList) {
		    if (jobList.size() > 0) {
		        job = (Runnable)jobList.remove(0);
			if (jobList.size() > 0) {
			    jobList.notify();
			}
			
		    } else {
			try {
			    jobList.wait();
			} catch (InterruptedException ie) {
			    // terminated ?
			} finally {
			}
			continue;
		    }
		}
		if (job != null) {
		    try {
			idle--;
			job.run();
//System.out.println("jsl-ThreadService: done job "+doneJobs++);

		    } catch (Exception e) { 
			// TODO
			e.printStackTrace();
		    } finally {
			idle++;
		    }
		}

		// re-init
		this.setPriority(priority);
		this.interrupted();
		this.setContextClassLoader(cloader);
	    }
	}
    }

// private methods
    private void stateCheck() throws IllegalStateException {
	if (terminated) {
	    throw new IllegalStateException("The thread service has been terminated.");
	}
    }

    private void createThread() {
	if (idle < 1) {
	    synchronized(threadList) {
		if (jobList.size() > 0 && currThreds < minThreads) {
		    ExecutorThread et = new ExecutorThread();
		    et.start();
		    threadList[currThreds++] = et;
//System.out.println("jsl-ThreadService: create new thread: "+currThreds);
		}
	    }
	}
    }


// protected or private variables
// ------------------------------
    private ArrayList jobList = new ArrayList(0);

    private ExecutorThread[] threadList;
    private int minThreads = 1;
    private int currThreds = 0;
    private int idle = 0;

    private boolean terminated = false;
    private int priority;
    private ThreadGroup threadGroup = new ThreadGroup("ThreadService");
    private ClassLoader cloader;

    private static long counter = 0;

    private int addedJobs = 1;
    private int doneJobs = 1;
}
