/*
 * @(#)ClientNotifForwarder.java	1.39 05/01/04
 * 
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.sun.jmx.remote.internal;

import java.io.IOException;
import java.io.NotSerializableException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.NotificationFilter;
import javax.management.ObjectName;
import javax.management.MBeanServerNotification;
import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;

import javax.management.remote.NotificationResult;
import javax.management.remote.TargetedNotification;

import com.sun.jmx.remote.util.ClassLogger;
import com.sun.jmx.remote.util.EnvHelp;


public abstract class ClientNotifForwarder {
    public ClientNotifForwarder(Map env) {
	this(null, env);
    }

    private static int threadId;

    /* An Executor that allows at most one executing and one pending
       Runnable.  It uses at most one thread -- as soon as there is
       no pending Runnable the thread can exit.  Another thread is
       created as soon as there is a new pending Runnable.  This
       Executor is adapted for use in a situation where each Runnable
       usually schedules up another Runnable.  On return from the
       first one, the second one is immediately executed.  So this
       just becomes a complicated way to write a while loop, but with
       the advantage that you can replace it with another Executor,
       for instance one that you are using to execute a bunch of other
       unrelated work.

       You might expect that a java.util.concurrent.ThreadPoolExecutor 
       with corePoolSize=0 and maximumPoolSize=1 would have the same
       behaviour, but it does not.  A ThreadPoolExecutor only creates
       a new thread when a new task is submitted and the number of
       existing threads is < corePoolSize.  This can never happen when
       corePoolSize=0, so new threads are never created.  Surprising,
       but there you are.
    */
    private static class LinearExecutor implements Executor {
	public synchronized void execute(Runnable command) {
	    if (this.command != null)
		throw new IllegalArgumentException("More than one command");
	    this.command = command;
	    if (thread == null) {
		thread = new Thread() {
		    public void run() {
			while (true) {
			    Runnable r;
			    synchronized (LinearExecutor.this) {
				if (LinearExecutor.this.command == null) {
				    thread = null;
				    return;
				} else {
				    r = LinearExecutor.this.command;
				    LinearExecutor.this.command = null;
				}
			    }
			    r.run();
			}
		    }
		};
		thread.setDaemon(true);
		thread.setName("ClientNotifForwarder-" + ++threadId);
		thread.start();
	    }
	}

	private Runnable command;
	private Thread thread;
    }

    public ClientNotifForwarder(ClassLoader defaultClassLoader, Map env) {
	maxNotifications = EnvHelp.getMaxFetchNotifNumber(env);
	timeout = EnvHelp.getFetchTimeout(env);

        /* You can supply an Executor in which the remote call to
           fetchNotifications will be made.  The Executor's execute
           method reschedules another task, so you must not use
           an Executor that executes tasks in the caller's thread.  */
	Executor ex = (Executor)
	    env.get("jmx.remote.x.fetch.notifications.executor");
	if (ex == null)
            ex = new LinearExecutor();
        else if (logger.traceOn())
            logger.trace("ClientNotifForwarder", "executor is " + ex);

	this.defaultClassLoader = defaultClassLoader;
	this.executor = ex;
    }

    /**
     * Called to to fetch notifications from a server.
     */
    abstract protected NotificationResult fetchNotifs(long clientSequenceNumber,
						      int maxNotifications,
						      long timeout)
	    throws IOException, ClassNotFoundException;

    abstract protected Integer addListenerForMBeanRemovedNotif() 
	throws IOException, InstanceNotFoundException;

    abstract protected void removeListenerForMBeanRemovedNotif(Integer id)
	throws IOException, InstanceNotFoundException, 
	       ListenerNotFoundException;

    /**
     * Used to send out a notification about lost notifs
     */
    abstract protected void lostNotifs(String message, long number);


    public synchronized void addNotificationListener(Integer listenerID,
                                        ObjectName name, 
                                        NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback,
					Subject delegationSubject)
	    throws IOException, InstanceNotFoundException {

	if (logger.traceOn()) {
	    logger.trace("addNotificationListener",
			 "Add the listener "+listener+" at "+name);
	}

	infoList.put(listenerID, 
		     new ClientListenerInfo(listenerID, 
					    name,
					    listener,
					    filter,
					    handback,
					    delegationSubject));
    

	init(false);
    }            

    public synchronized Integer[]
	removeNotificationListener(ObjectName name,
				   NotificationListener listener)
	throws ListenerNotFoundException, IOException {

        beforeRemove();
	    
	if (logger.traceOn()) {
	    logger.trace("removeNotificationListener",
			 "Remove the listener "+listener+" from "+name);
	}
	    
	ArrayList ids = new ArrayList();
	ArrayList values = new ArrayList(infoList.values());
	for (int i=values.size()-1; i>=0; i--) {
	    ClientListenerInfo li = (ClientListenerInfo)values.get(i);

	    if (li.sameAs(name, listener)) {
		ids.add(li.getListenerID());

		infoList.remove(li.getListenerID());
	    }
	}

	if (ids.isEmpty())
	    throw new ListenerNotFoundException("Listener not found");

	return (Integer[])ids.toArray(new Integer[0]);
    }

    public synchronized Integer
	removeNotificationListener(ObjectName name, 
				   NotificationListener listener,
				   NotificationFilter filter,
				   Object handback)
	    throws ListenerNotFoundException, IOException {

	if (logger.traceOn()) {
	    logger.trace("removeNotificationListener",
			 "Remove the listener "+listener+" from "+name);
	}

        beforeRemove();

	Integer id = null;

	ArrayList values = new ArrayList(infoList.values());
	for (int i=values.size()-1; i>=0; i--) {
	    ClientListenerInfo li = (ClientListenerInfo)values.get(i);
	    if (li.sameAs(name, listener, filter, handback)) {
		id=li.getListenerID();

		infoList.remove(id);

		break;		    
	    }
	}

	if (id == null)
	    throw new ListenerNotFoundException("Listener not found");

	return id;	
    }

    public synchronized Integer[] removeNotificationListener(ObjectName name) {
	if (logger.traceOn()) {
	    logger.trace("removeNotificationListener",
			 "Remove all listeners registered at "+name);
	}

	ArrayList ids = new ArrayList();

	ArrayList values = new ArrayList(infoList.values());
	for (int i=values.size()-1; i>=0; i--) {
	    ClientListenerInfo li = (ClientListenerInfo)values.get(i);
	    if (li.sameAs(name)) {
		ids.add(li.getListenerID());
		    
		infoList.remove(li.getListenerID());
	    }
	}

	return (Integer[]) ids.toArray(new Integer[0]);
    }

    public synchronized ListenerInfo[] getListenerInfo() {
	return (ListenerInfo[])infoList.values().toArray(new ListenerInfo[0]);
    }

    /*
     * Called when a connector is doing reconnection. Like <code>postReconnection</code>,
     * this method is intended to be called only by a client connetor:
     * <code>RMIConnector</code/> and <code/>ClientIntermediary</code>.
     * Call this method will set the flag beingReconnection to <code>true</code>,
     * and the thread used to fetch notifis will be stopped, a new thread can be
     * created only after the method <code>postReconnection</code> is called.
     *
     * It is caller's responsiblity to not re-call this method before calling
     * <code>postReconnection.
     */ 
    public synchronized ClientListenerInfo[] preReconnection() throws IOException {
	if (state == TERMINATED || beingReconnected) { // should never
	    throw new IOException("Illegal state.");
	}

	final ClientListenerInfo[] tmp = (ClientListenerInfo[]) 
            infoList.values().toArray(new ClientListenerInfo[0]);


	beingReconnected = true;

	infoList.clear();

	if (currentFetchThread == Thread.currentThread()) {
	    /* we do not need to stop the fetching thread, because this thread is
	       used to do restarting and it will not be used to do fetching during
	       the re-registering the listeners.*/
	    return tmp;
	}

	while (state == STARTING) {
	    try {
		wait();
	    } catch (InterruptedException ire) {
		IOException ioe = new IOException(ire.toString());
		EnvHelp.initCause(ioe, ire);

		throw ioe;
	    }
	}

	if (state == STARTED) {
	    setState(STOPPING);
	}

	return tmp;
    }

    /**
     * Called after reconnection is finished.
     * This method is intended to be called only by a client connetor:
     * <code>RMIConnector</code/> and <code/>ClientIntermediary</code>.
     */
    public synchronized void postReconnection(ClientListenerInfo[] listenerInfos)
	throws IOException {

	if (state == TERMINATED) {
	    return;
	}

	while (state == STOPPING) {
	    try {
		wait();
	    } catch (InterruptedException ire) {
		IOException ioe = new IOException(ire.toString());
		EnvHelp.initCause(ioe, ire);
		throw ioe;
	    }
	}

	final boolean trace = logger.traceOn();
	final int len   = listenerInfos.length;

	for (int i=0; i<len; i++) {
	    if (trace) {
		logger.trace("addNotificationListeners",
			     "Add a listener at "+
			     listenerInfos[i].getListenerID());
	    }

	    infoList.put(listenerInfos[i].getListenerID(), listenerInfos[i]);
	}

	beingReconnected = false;
	notifyAll();

	if (currentFetchThread == Thread.currentThread()) {
	    // no need to init, simply get the id
	    try {
		mbeanRemovedNotifID = addListenerForMBeanRemovedNotif();
	    } catch (Exception e) {
		final String msg =
		    "Failed to register a listener to the mbean " +
		    "server: the client will not do clean when an MBean " +
		    "is unregistered";
		if (logger.traceOn()) {
		    logger.trace("init", msg, e);
		}		 
	    } 
	} else if (listenerInfos.length > 0) { // old listeners re-registered
	    init(true);
	} else if (infoList.size() > 0) {
	    // but new listeners registered during reconnection
	    init(false);
	}
    }

    public synchronized void terminate() {
	if (state == TERMINATED) {
	    return;
	}

	if (logger.traceOn()) {
	    logger.trace("terminate", "Terminating...");
	}

	if (state == STARTED) {
	   infoList.clear();
	}

	setState(TERMINATED);
    }

// -------------------------------------------------
// private classes
// -------------------------------------------------
    //
    private class NotifFetcher implements Runnable {
	public void run() {
            synchronized (ClientNotifForwarder.this) {
		currentFetchThread = Thread.currentThread();

                if (state == STARTING)
                    setState(STARTED);
            }

	    if (defaultClassLoader != null) {
		AccessController.doPrivileged(new PrivilegedAction() {
			public Object run() {
			    Thread.currentThread().
				setContextClassLoader(defaultClassLoader);
			    return null;
			}
		    });
	    }

	    NotificationResult nr = null;
	    if (!shouldStop() && (nr = fetchNotifs()) != null) {
		// nr == null means got exception

		final TargetedNotification[] notifs =
		    nr.getTargetedNotifications();
		final int len = notifs.length;
		final HashMap listeners;
		final Integer myListenerID;

		long missed = 0;

		synchronized(ClientNotifForwarder.this) {
		    // check sequence number.
		    //
		    if (clientSequenceNumber >= 0) {
			missed = nr.getEarliestSequenceNumber() - 
			    clientSequenceNumber;    
		    }

		    clientSequenceNumber = nr.getNextSequenceNumber();

		    final int size = infoList.size();
		    listeners  = new HashMap(((size>len)?len:size));

		    for (int i = 0 ; i < len ; i++) {
			final TargetedNotification tn = notifs[i];
			final Integer listenerID = tn.getListenerID();
			
			// check if an mbean unregistration notif
			if (!listenerID.equals(mbeanRemovedNotifID)) {
			    final ListenerInfo li = 
				(ListenerInfo) infoList.get(listenerID);
			    if (li != null) 
				listeners.put(listenerID,li);
			    continue;
			}
			final Notification notif = tn.getNotification();
			final String unreg =
			    MBeanServerNotification.UNREGISTRATION_NOTIFICATION;
			if (notif instanceof MBeanServerNotification &&
			    notif.getType().equals(unreg)) {
			    
			    MBeanServerNotification mbsn =
				(MBeanServerNotification) notif;
			    ObjectName name = mbsn.getMBeanName();
			    
			    removeNotificationListener(name);
			}
		    }
		    myListenerID = mbeanRemovedNotifID;
		}

		if (missed > 0) {
		    final String msg =
			"May have lost up to " + missed +
			" notification" + (missed == 1 ? "" : "s");
		    lostNotifs(msg, missed);
		    logger.trace("NotifFetcher.run", msg);
		}

		// forward
		for (int i = 0 ; i < len ; i++) {
		    final TargetedNotification tn = notifs[i];
		    dispatchNotification(tn,myListenerID,listeners);
		}
	    }

            synchronized (ClientNotifForwarder.this) {
		currentFetchThread = null;
	    }

	    if (nr == null || shouldStop()) {
		// tell that the thread is REALLY stopped
		setState(STOPPED);
	    } else {
		executor.execute(this);
	    }
	}

	void dispatchNotification(TargetedNotification tn, 
				  Integer myListenerID, Map listeners) {
	    final Notification notif = tn.getNotification();
	    final Integer listenerID = tn.getListenerID();
	    
	    if (listenerID.equals(myListenerID)) return;
	    final ListenerInfo li = (ClientListenerInfo) 
		listeners.get(listenerID);

	    if (li == null) {
		logger.trace("NotifFetcher.dispatch",
			     "Listener ID not in map");
		return;
	    }

	    NotificationListener l = li.getListener();
	    Object h = li.getHandback();
	    try {
		l.handleNotification(notif, h);
	    } catch (RuntimeException e) {
		final String msg =
		    "Failed to forward a notification " +
		    "to a listener";
		logger.trace("NotifFetcher-run", msg, e);
	    }

	}

	private NotificationResult fetchNotifs() {
	    try {
		NotificationResult nr = ClientNotifForwarder.this.
		    fetchNotifs(clientSequenceNumber,maxNotifications,
				timeout);

		if (logger.traceOn()) {
		    logger.trace("NotifFetcher-run",
				 "Got notifications from the server: "+nr);
		}

		return nr;
	    } catch (ClassNotFoundException e) {
		logger.trace("NotifFetcher.fetchNotifs", e);
		return fetchOneNotif();
	    } catch (NotSerializableException e) {
		logger.trace("NotifFetcher.fetchNotifs", e);
		return fetchOneNotif();
	    } catch (IOException ioe) {
		if (!shouldStop()) {
		    logger.error("NotifFetcher-run",
				 "Failed to fetch notification, " +
				 "stopping thread. Error is: " + ioe, ioe);
		    logger.debug("NotifFetcher-run",ioe);
		}

		// no more fetching
		return null;
	    }
	}

	/* Fetch one notification when we suspect that it might be a
	   notification that we can't deserialize (because of a
	   missing class).  First we ask for 0 notifications with 0
	   timeout.  This allows us to skip sequence numbers for
	   notifications that don't match our filters.  Then we ask
	   for one notification.  If that produces a
	   ClassNotFoundException or a NotSerializableException, we
	   increase our sequence number and ask again.  Eventually we
	   will either get a successful notification, or a return with
	   0 notifications.  In either case we can return a
	   NotificationResult.  This algorithm works (albeit less
	   well) even if the server implementation doesn't optimize a
	   request for 0 notifications to skip sequence numbers for
	   notifications that don't match our filters.

	   If we had at least one ClassNotFoundException, then we
	   must emit a JMXConnectionNotification.LOST_NOTIFS.
	*/
	private NotificationResult fetchOneNotif() {
	    ClientNotifForwarder cnf = ClientNotifForwarder.this;

	    long startSequenceNumber = clientSequenceNumber;

	    int notFoundCount = 0;

	    NotificationResult result = null;

	    while (result == null && !shouldStop()) {
		NotificationResult nr;

		try {
		    // 0 notifs to update startSequenceNumber
		    nr = cnf.fetchNotifs(startSequenceNumber, 0, 0L);
		} catch (ClassNotFoundException e) {
		    logger.warning("NotifFetcher.fetchOneNotif",
				   "Impossible exception: " + e);
		    logger.debug("NotifFetcher.fetchOneNotif",e);
		    return null;
		} catch (IOException e) {
		    if (!shouldStop())
			logger.trace("NotifFetcher.fetchOneNotif", e);
		    return null;
		}

		if (shouldStop())
		    return null;

		startSequenceNumber = nr.getNextSequenceNumber();

		try {
		    // 1 notif to skip possible missing class
		    result = cnf.fetchNotifs(startSequenceNumber, 1, 0L);
		} catch (Exception e) {
		    if (e instanceof ClassNotFoundException
			|| e instanceof NotSerializableException) {
			logger.warning("NotifFetcher.fetchOneNotif",
				     "Failed to deserialize a notification: "+e.toString());
			if (logger.traceOn()) {
			    logger.trace("NotifFetcher.fetchOneNotif",
					 "Failed to deserialize a notification.", e);
			}

			notFoundCount++;
			startSequenceNumber++;
		    } else {
			if (!shouldStop())
			    logger.trace("NotifFetcher.fetchOneNotif", e);
			return null;
		    }
		}
	    }

	    if (notFoundCount > 0) {
		final String msg =
		    "Dropped " + notFoundCount + " notification" +
		    (notFoundCount == 1 ? "" : "s") +
		    " because classes were missing locally";
		lostNotifs(msg, notFoundCount);
	    }

	    return result;
	}

	private boolean shouldStop() {
	    synchronized (ClientNotifForwarder.this) {
		if (state != STARTED) {
		    return true;
		} else if (infoList.size() == 0) {
		    // no more listener, stop fetching
		    setState(STOPPING);

		    return true;
		}

		return false;
	    }
	}
    }


// -------------------------------------------------
// private methods
// -------------------------------------------------
    private synchronized void setState(int newState) {
	if (state == TERMINATED) {
	    return;
	}
	
	state = newState;
	this.notifyAll();
    }

    /*
     * Called to decide whether need to start a thread for fetching notifs.
     * <P>The parameter reconnected will decide whether to initilize the clientSequenceNumber,
     * initilaizing the clientSequenceNumber means to ignore all notifications arrived before.
     * If it is reconnected, we will not initialize in order to get all notifications arrived
     * during the reconnection. It may cause the newly registered listeners to receive some
     * notifications arrived before its registray.
     */
    private synchronized void init(boolean reconnected) throws IOException {
	switch (state) {
	case STARTED:
	    return;
	case STARTING:
	    return;
	case TERMINATED:
	    throw new IOException("The ClientNotifForwarder has been terminated.");
	case STOPPING:
	    if (beingReconnected == true) {
		// wait for another thread to do, which is doing reconnection
		return;
	    }

	    while (state == STOPPING) { // make sure only one fetching thread.		
		try {
		    wait();
		} catch (InterruptedException ire) {
		    IOException ioe = new IOException(ire.toString());
		    EnvHelp.initCause(ioe, ire);
		    
		    throw ioe;
		}
	    }
		
	    // re-call this method to check the state again,
	    // the state can be other value like TERMINATED.
	    init(reconnected);

	    return;
	case STOPPED:
	    if (beingReconnected == true) {
		// wait for another thread to do, which is doing reconnection
		return;
	    }

	    if (logger.traceOn()) {
		logger.trace("init", "Initializing...");
	    }

	    // init the clientSequenceNumber if not reconnected.
	    if (!reconnected) {
		try {
		    NotificationResult nr = fetchNotifs(-1, 0, 0);
		    clientSequenceNumber = nr.getNextSequenceNumber();
		} catch (ClassNotFoundException e) {
		    // can't happen
		    logger.warning("init", "Impossible exception: "+ e);
		    logger.debug("init",e);
		}
	    }

	    // for cleaning
	    try {
		mbeanRemovedNotifID = addListenerForMBeanRemovedNotif();
	    } catch (Exception e) {
		final String msg =
		    "Failed to register a listener to the mbean " +
		    "server: the client will not do clean when an MBean " +
		    "is unregistered";
		if (logger.traceOn()) {
		    logger.trace("init", msg, e);
		}		 
	    } 

	    setState(STARTING);

	    // start fetching
	    executor.execute(new NotifFetcher());

	    return;
	default:
	    // should not
	    throw new IOException("Unknown state.");
	}
    }

    /**
     * Import: should not remove a listener dureing reconnection, the reconnection
     * needs to change the listener list and that will possibly make removal fail.
     */
    private synchronized void beforeRemove() throws IOException {
        while (beingReconnected) {
	    if (state == TERMINATED) {
		throw new IOException("Terminated.");
	    }

	    try {
		wait();
	    } catch (InterruptedException ire) {
		IOException ioe = new IOException(ire.toString());
		EnvHelp.initCause(ioe, ire);

		throw ioe;
	    }
	}

	if (state == TERMINATED) {
	    throw new IOException("Terminated.");
	}
    }

// -------------------------------------------------
// private variables
// -------------------------------------------------

    private final ClassLoader defaultClassLoader;
    private final Executor executor;

    private final HashMap infoList = new HashMap();
    // Integer -> ClientListenerInfo

    // notif stuff
    private long clientSequenceNumber = -1;
    private final int maxNotifications;
    private final long timeout;

    private Integer mbeanRemovedNotifID = null;

    private Thread currentFetchThread;

    // admin stuff
    private boolean inited = false;

    // state
    /**
     * This state means that a thread is being created for fetching and forwarding notifications.
     */
    private static final int STARTING = 0;

    /**
     * This state tells that a thread has been started for fetching and forwarding notifications.
     */
    private static final int STARTED = 1;

    /**
     * This state means that the fetching thread is informed to stop.
     */
    private static final int STOPPING = 2;

    /**
     * This state means that the fetching thread is already stopped.
     */
    private static final int STOPPED = 3;

    /**
     * This state means that this object is terminated and no more thread will be created
     * for fetching notifications.
     */ 
    private static final int TERMINATED = 4;

    private int state = STOPPED;

    /**
     * This variable is used to tell whether a connector (RMIConnector or ClientIntermediary)
     * is doing reconnection.
     * This variable will be set to true by the method <code>preReconnection</code>, and set
     * fase by <code>postReconnection</code>.
     * When beingReconnected == true, no thread will be created for fetching notifications.
     */
    private boolean beingReconnected = false;

    private static final ClassLogger logger =
	new ClassLogger("javax.management.remote.misc",
			"ClientNotifForwarder");
}
