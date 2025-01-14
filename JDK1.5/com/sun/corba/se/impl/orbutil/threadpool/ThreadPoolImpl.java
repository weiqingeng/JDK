/*
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.sun.corba.se.impl.orbutil.threadpool;

import java.security.AccessController;
import java.security.PrivilegedAction;

import com.sun.corba.se.spi.orbutil.threadpool.NoSuchWorkQueueException;
import com.sun.corba.se.spi.orbutil.threadpool.ThreadPool;
import com.sun.corba.se.spi.orbutil.threadpool.Work;
import com.sun.corba.se.spi.orbutil.threadpool.WorkQueue;

import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.threadpool.WorkQueueImpl;

import com.sun.corba.se.spi.monitoring.MonitoringConstants;
import com.sun.corba.se.spi.monitoring.MonitoredObject;
import com.sun.corba.se.spi.monitoring.MonitoringFactories;
import com.sun.corba.se.spi.monitoring.LongMonitoredAttributeBase;

public class ThreadPoolImpl implements ThreadPool
{
    private static int threadCounter = 0; // serial counter useful for debugging

    private WorkQueue workQueue;
    
    // Stores the number of available worker threads
    private int availableWorkerThreads = 0;
    
    // Stores the number of threads in the threadpool currently
    private int currentThreadCount = 0;
    
    // Minimum number of worker threads created at instantiation of the threadpool
    private int minWorkerThreads = 0;
    
    // Maximum number of worker threads in the threadpool
    private int maxWorkerThreads = 0;
    
    // Inactivity timeout value for worker threads to exit and stop running
    private long inactivityTimeout = ORBConstants.DEFAULT_INACTIVITY_TIMEOUT ;
    
    // Indicates if the threadpool is bounded or unbounded
    private boolean boundedThreadPool = false;
    
    // Running count of the work items processed
    // Set the value to 1 so that divide by zero is avoided in 
    // averageWorkCompletionTime()
    private long processedCount = 1;
    
    // Running aggregate of the time taken in millis to execute work items
    // processed by the threads in the threadpool
    private long totalTimeTaken = 0;

    // Lock for protecting state when required
    private Object lock = new Object();

    // Name of the ThreadPool
    private String name;

    // MonitoredObject for ThreadPool
    private MonitoredObject threadpoolMonitoredObject;
    
    // ThreadGroup in which threads should be created
    private final ThreadGroup threadGroup ;

    /**
     * This constructor is used to create an unbounded threadpool
     */
    public ThreadPoolImpl(ThreadGroup tg, String threadpoolName) {
        maxWorkerThreads = Integer.MAX_VALUE;
        workQueue = new WorkQueueImpl(this);
	threadGroup = tg ;
	name = threadpoolName;
	initializeMonitoring();
    }
 
    /**
     * This constructor is used to create an unbounded threadpool
     * in the ThreadGroup of the current thread
     */
    public ThreadPoolImpl(String threadpoolName) {
	this( Thread.currentThread().getThreadGroup(), threadpoolName ) ; 
    }

    /**
     * This constructor is used to create bounded threadpool
     */
    public ThreadPoolImpl(int minSize, int maxSize, long timeout, 
					    String threadpoolName) 
    {
        inactivityTimeout = timeout;
        minWorkerThreads = minSize;
        maxWorkerThreads = maxSize;
        boundedThreadPool = true;
        workQueue = new WorkQueueImpl(this);
	threadGroup = Thread.currentThread().getThreadGroup() ;
	name = threadpoolName;
        for (int i = 0; i < minWorkerThreads; i++) {
            createWorkerThread();
        }
	initializeMonitoring();
    }

    // Setup monitoring for this threadpool
    private void initializeMonitoring() {
	// Get root monitored object
	MonitoredObject root = MonitoringFactories.getMonitoringManagerFactory().
		createMonitoringManager(MonitoringConstants.DEFAULT_MONITORING_ROOT, null).
		getRootMonitoredObject();

	// Create the threadpool monitoring root
	MonitoredObject threadPoolMonitoringObjectRoot = root.getChild(
		    MonitoringConstants.THREADPOOL_MONITORING_ROOT);
	if (threadPoolMonitoringObjectRoot == null) {
	    threadPoolMonitoringObjectRoot =  MonitoringFactories.
		    getMonitoredObjectFactory().createMonitoredObject(
		    MonitoringConstants.THREADPOOL_MONITORING_ROOT,
		    MonitoringConstants.THREADPOOL_MONITORING_ROOT_DESCRIPTION);
	    root.addChild(threadPoolMonitoringObjectRoot);
	}
	threadpoolMonitoredObject = MonitoringFactories.
		    getMonitoredObjectFactory().
		    createMonitoredObject(name,
		    MonitoringConstants.THREADPOOL_MONITORING_DESCRIPTION);

	threadPoolMonitoringObjectRoot.addChild(threadpoolMonitoredObject);

	LongMonitoredAttributeBase b1 = new 
	    LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_CURRENT_NUMBER_OF_THREADS, 
		    MonitoringConstants.THREADPOOL_CURRENT_NUMBER_OF_THREADS_DESCRIPTION) {
		public Object getValue() {
		    return new Long(ThreadPoolImpl.this.currentNumberOfThreads());
		}
	    };
	threadpoolMonitoredObject.addAttribute(b1);
	LongMonitoredAttributeBase b2 = new 
	    LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_NUMBER_OF_AVAILABLE_THREADS, 
		    MonitoringConstants.THREADPOOL_CURRENT_NUMBER_OF_THREADS_DESCRIPTION) {
		public Object getValue() {
		    return new Long(ThreadPoolImpl.this.numberOfAvailableThreads());
		}
	    };
	threadpoolMonitoredObject.addAttribute(b2);
	LongMonitoredAttributeBase b3 = new 
	    LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_NUMBER_OF_BUSY_THREADS, 
		    MonitoringConstants.THREADPOOL_NUMBER_OF_BUSY_THREADS_DESCRIPTION) {
		public Object getValue() {
		    return new Long(ThreadPoolImpl.this.numberOfBusyThreads());
		}
	    };
	threadpoolMonitoredObject.addAttribute(b3);
	LongMonitoredAttributeBase b4 = new 
	    LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_AVERAGE_WORK_COMPLETION_TIME, 
		    MonitoringConstants.THREADPOOL_AVERAGE_WORK_COMPLETION_TIME_DESCRIPTION) {
		public Object getValue() {
		    return new Long(ThreadPoolImpl.this.averageWorkCompletionTime());
		}
	    };
	threadpoolMonitoredObject.addAttribute(b4);
	LongMonitoredAttributeBase b5 = new 
	    LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_CURRENT_PROCESSED_COUNT, 
		    MonitoringConstants.THREADPOOL_CURRENT_PROCESSED_COUNT_DESCRIPTION) {
		public Object getValue() {
		    return new Long(ThreadPoolImpl.this.currentProcessedCount());
		}
	    };
	threadpoolMonitoredObject.addAttribute(b5);

	// Add the monitored object for the WorkQueue
	
	threadpoolMonitoredObject.addChild(
		((WorkQueueImpl)workQueue).getMonitoredObject());
    }

    // Package private method to get the monitored object for this
    // class
    MonitoredObject getMonitoredObject() {
	return threadpoolMonitoredObject;
    }
    
    public WorkQueue getAnyWorkQueue()
    {
	return workQueue;
    }

    public WorkQueue getWorkQueue(int queueId)
	throws NoSuchWorkQueueException
    {
	if (queueId != 0)
	    throw new NoSuchWorkQueueException();
	return workQueue;
    }

    /**
     * To be called from the workqueue when work is added to the
     * workQueue. This method would create new threads if required
     * or notify waiting threads on the queue for available work
     */
    void notifyForAvailableWork(WorkQueue aWorkQueue) {
	synchronized (lock) {
	    if (availableWorkerThreads == 0) {
		createWorkerThread();
	    } else {
		aWorkQueue.notify();
	    }
	}
    }
    

    /**
     * To be called from the workqueue to create worker threads when none
     * available.
     */
    void createWorkerThread() {
	synchronized (lock) {
	    final String name = getName() ;
	      
	    if (boundedThreadPool) {
		if (currentThreadCount < maxWorkerThreads) {
		    currentThreadCount++;
		} else {
		    // REVIST - Need to create a thread to monitor the
		    // the state for deadlock i.e. all threads waiting for
		    // something which can be got from the item in the 
		    // workqueue, but there is no thread available to
		    // process that work item - DEADLOCK !!
		    return;
		}
	    } else {
		currentThreadCount++;
	    }

	    // If we get here, we need to create a thread.
	    AccessController.doPrivileged( 
		new PrivilegedAction() {
		    public Object run() {
			// Thread creation needs to be in a doPrivileged block
			// for two reasons:
			// 1. The creation of a thread in a specific ThreadGroup
			//    is a privileged operation.  Lack of a doPrivileged
			//    block here causes an AccessControlException 
			//    (see bug 6268145).
			// 2. We want to make sure that the permissions associated 
			//    with this thread do NOT include the permissions of
			//    the current thread that is calling this method.
			//    This leads to problems in the app server where
			//    some threads in the ThreadPool randomly get 
			//    bad permissions, leading to unpredictable 
			//    permission errors.
			WorkerThread thread = new WorkerThread(threadGroup, name);
			    
			// The thread must be set to a daemon thread so the
			// VM can exit if the only threads left are PooledThreads
			// or other daemons.  We don't want to rely on the
			// calling thread always being a daemon.
			// Note that no exception is possible here since we
			// are inside the doPrivileged block.
			thread.setDaemon(true);

			thread.start();
			
			return null ; 
		    }
		} 
	    ) ;
	} 
    }
    
    /** 
    * This method will return the minimum number of threads maintained 
    * by the threadpool. 
    */ 
    public int minimumNumberOfThreads() {
        return minWorkerThreads;
    }
    
    /** 
    * This method will return the maximum number of threads in the 
    * threadpool at any point in time, for the life of the threadpool 
    */ 
    public int maximumNumberOfThreads() {
        return maxWorkerThreads;
    }
    
    /** 
    * This method will return the time in milliseconds when idle 
    * threads in the threadpool are removed. 
    */ 
    public long idleTimeoutForThreads() {
        return inactivityTimeout;
    }
    
    /** 
    * This method will return the total number of threads currently in the 
    * threadpool. This method returns a value which is not synchronized. 
    */ 
    public int currentNumberOfThreads() {
	synchronized (lock) {
	    return currentThreadCount;
	}
    }
    
    /** 
    * This method will return the number of available threads in the 
    * threadpool which are waiting for work. This method returns a 
    * value which is not synchronized. 
    */ 
    public int numberOfAvailableThreads() {
	synchronized (lock) {
	    return availableWorkerThreads;
	}
    }
    
    /** 
    * This method will return the number of busy threads in the threadpool 
    * This method returns a value which is not synchronized. 
    */ 
    public int numberOfBusyThreads() {
	synchronized (lock) {
	    return (currentThreadCount - availableWorkerThreads);
	}
    }
    
    /**
     * This method returns the average elapsed time taken to complete a Work
     * item in milliseconds.
     */
    public long averageWorkCompletionTime() {
	synchronized (lock) {
	    return (totalTimeTaken / processedCount);
	}
    }
    
    /**
     * This method returns the number of Work items processed by the threadpool
     */
    public long currentProcessedCount() {
	synchronized (lock) {
	    return processedCount;
	}
    }

    public String getName() {
        return name;
    }

    /** 
    * This method will return the number of WorkQueues serviced by the threadpool. 
    */ 
    public int numberOfWorkQueues() {
        return 1;
    } 


    private static synchronized int getUniqueThreadId() {
        return ThreadPoolImpl.threadCounter++;
    }


    private class WorkerThread extends Thread
    {
        private Work currentWork;
        private int threadId = 0; // unique id for the thread
        // thread pool this WorkerThread belongs too
        private String threadPoolName;
	// name seen by Thread.getName()
	private StringBuffer workerThreadName = new StringBuffer();

        WorkerThread(ThreadGroup tg, String threadPoolName) {
	    super(tg, "Idle");
	    this.threadId = ThreadPoolImpl.getUniqueThreadId();
            this.threadPoolName = threadPoolName;
	    setName(composeWorkerThreadName(threadPoolName, "Idle"));
        }
        
        public void run() {
            while (true) {
                try {

		    synchronized (lock) {
			availableWorkerThreads++;
		    }
                    
                    // Get some work to do
                    currentWork = ((WorkQueueImpl)workQueue).requestWork(inactivityTimeout);

		    synchronized (lock) {
			availableWorkerThreads--;
			// It is possible in notifyForAvailableWork that the
			// check for availableWorkerThreads = 0 may return
			// false, because the availableWorkerThreads has not been
			// decremented to zero before the producer thread added 
			// work to the queue. This may create a deadlock, if the
			// executing thread needs information which is in the work
			// item queued in the workqueue, but has no thread to work
			// on it since none was created because availableWorkerThreads = 0
			// returned false.
			// The following code will ensure that a thread is always available
			// in those situations
			if  ((availableWorkerThreads == 0) && 
				(workQueue.workItemsInQueue() > 0)) {
			    createWorkerThread();
			}
		    }

                    // Set the thread name for debugging.
	            setName(composeWorkerThreadName(threadPoolName,
				      Integer.toString(this.threadId)));

                    long start = System.currentTimeMillis();
                    
		    try {
			// Do the work
			currentWork.doWork();
		    } catch (Throwable t) {
			// Ignore all errors.
			;
		    }
                    
                    long end = System.currentTimeMillis();
                    

		    synchronized (lock) {
			totalTimeTaken += (end - start);
			processedCount++;
		    }

		    // set currentWork to null so that the work item can be 
		    // garbage collected
		    currentWork = null;

	            setName(composeWorkerThreadName(threadPoolName, "Idle"));

                } catch (TimeoutException e) {
                    // This thread timed out waiting for something to do.

		    synchronized (lock) {
			availableWorkerThreads--;

			// This should for both bounded and unbounded case
			if (currentThreadCount > minWorkerThreads) {
			    currentThreadCount--;
			    // This thread can exit.
			    return;
			} else {
			    // Go back to waiting on workQueue
			    continue;
			}
		    }
                } catch (InterruptedException ie) {
                    // InterruptedExceptions are
                    // caught here.  Thus, threads can be forced out of
                    // requestWork and so they have to reacquire the lock.
                    // Other options include ignoring or
                    // letting this thread die.
                    // Ignoring for now. REVISIT
		    synchronized (lock) {
			availableWorkerThreads--;
		    }

                } catch (Throwable e) {

                    // Ignore any exceptions that currentWork.process
                    // accidently lets through, but let Errors pass.
                    // Add debugging output?  REVISIT
		    synchronized (lock) {
			availableWorkerThreads--;
		    }

                }
            }
        }

	private String composeWorkerThreadName(String poolName, String workerName) {
            workerThreadName.setLength(0);
	    workerThreadName.append("p: ").append(poolName);
	    workerThreadName.append("; w: ").append(workerName);
	    return workerThreadName.toString();
	}
    } // End of WorkerThread class

}

// End of file.
