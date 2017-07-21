/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.graph;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetel.graph.runtime.CloverWorker;

/**
 * This class should be able to provide org.jetel.graph.Node or org.jetel.graph.TransformationGraph
 * corresponding to the current thread. Both are provided via static methods - {@link #getNode()}
 * and {@link #getGraph()}.
 *
 * This functionality can work only when all threads working with graph elements are registered 
 * in this class.
 *
 * For this purpose it is recommended to use CloverWorker class instead Runnable every time 
 * you want to create separate thread inside a component.
 *
 * @see CloverWorker
 * @author Martin Zatopek (martin.zatopek@javlinconsulting.cz)
 *         (c) Javlin Consulting (www.javlinconsulting.cz)
 *
 * @created 24.9.2009
 */
public class ContextProvider {

    private static final Log logger = LogFactory.getLog(ContextProvider.class);

	private static final Map<Thread, Node> nodesCache = new HashMap<Thread, Node>(); 

	private static final Map<Thread, TransformationGraph> graphsCache = new HashMap<Thread, TransformationGraph>(); 
    
	public static synchronized TransformationGraph getGraph() {
    	Node node = nodesCache.get(Thread.currentThread());
    	if (node != null) {
        	return node.getGraph();
    	} else {
	    	return graphsCache.get(Thread.currentThread());
    	}
    }

	public static synchronized Node getNode() {
    	return nodesCache.get(Thread.currentThread());
    }

	public static synchronized void registerNode(Node node) {
		Thread currentThread = Thread.currentThread();
		if (nodesCache.containsKey(currentThread) && nodesCache.get(currentThread) != node) {
			logger.warn(String.format("Current thread '%s' is already registered with different node (%s).", currentThread.getName(), nodesCache.get(currentThread).toString()));
		}
		if (graphsCache.containsKey(currentThread) && node.getGraph() != graphsCache.get(currentThread)) {
			logger.warn(String.format("Current thread '%s' is already registered with different graph (%s).", currentThread.getName(), graphsCache.get(currentThread).toString()));
		}
		nodesCache.put(currentThread, node);
	}

	public static synchronized void registerGraph(TransformationGraph graph) {
		Thread currentThread = Thread.currentThread();
		if (graphsCache.containsKey(currentThread) && graph != graphsCache.get(currentThread)) {
			logger.warn(String.format("Current thread '%s' is already registered with different graph (%s).", currentThread.getName(), graphsCache.get(currentThread).toString()));
		}
		graphsCache.put(currentThread, graph);
	}

	public static synchronized void unregister() {
		if (nodesCache.containsKey(Thread.currentThread())) {
			nodesCache.remove(Thread.currentThread());
		} else {
			if (graphsCache.containsKey(Thread.currentThread())) {
				graphsCache.remove(Thread.currentThread());
			} else {
				logger.warn("Attempt to unregister non-registered thread in the ContextProvider.");
			}
		}
	}
	
}