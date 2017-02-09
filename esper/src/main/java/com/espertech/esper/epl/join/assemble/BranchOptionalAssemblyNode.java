/*
 ***************************************************************************************
 *  Copyright (C) 2006 EsperTech, Inc. All rights reserved.                            *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 ***************************************************************************************
 */
package com.espertech.esper.epl.join.assemble;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.epl.join.rep.Node;
import com.espertech.esper.util.IndentWriter;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Assembly node for an event stream that is a branch with a single optional child node below it.
 */
public class BranchOptionalAssemblyNode extends BaseAssemblyNode {
    private List<Node> resultsForStream;

    // For tracking when we only have a single event for this stream as a result
    private Node singleResultNode;
    private EventBean singleResultEvent;
    private boolean haveChildResults;

    // For tracking when we have multiple events for this stream
    private Set<EventBean> completedEvents;

    /**
     * Ctor.
     *
     * @param streamNum  - is the stream number
     * @param numStreams - is the number of streams
     */
    public BranchOptionalAssemblyNode(int streamNum, int numStreams) {
        super(streamNum, numStreams);
    }

    public void init(List<Node>[] result) {
        resultsForStream = result[streamNum];
        singleResultNode = null;
        singleResultEvent = null;
        haveChildResults = false;

        if (resultsForStream != null) {
            int numNodes = resultsForStream.size();
            if (numNodes == 1) {
                Node node = resultsForStream.get(0);
                Set<EventBean> nodeEvents = node.getEvents();

                // If there is a single result event (typical case)
                if (nodeEvents.size() == 1) {
                    singleResultNode = node;
                    singleResultEvent = nodeEvents.iterator().next();
                }
            }

            if (singleResultNode == null) {
                completedEvents = new HashSet<EventBean>();
            }
        }
    }

    public void process(List<Node>[] result, Collection<EventBean[]> resultFinalRows, EventBean resultRootEvent) {
        // there cannot be child nodes to compute a cartesian product if this node had no results
        if (resultsForStream == null) {
            return;
        }

        // If this node's result set consisted of a single event
        if (singleResultNode != null) {
            // If there are no child results, post a row
            if (!haveChildResults) {
                EventBean[] row = new EventBean[numStreams];
                row[streamNum] = singleResultEvent;
                parentNode.result(row, streamNum, singleResultNode.getParentEvent(), singleResultNode, resultFinalRows, resultRootEvent);
            }

            // if there were child results we are done since they have already been posted to the parent
            return;
        }

        // We have multiple events for this node, generate an event row for each event not yet received from
        // event rows generated by the child node.
        for (Node node : resultsForStream) {
            Set<EventBean> events = node.getEvents();
            for (EventBean theEvent : events) {
                if (completedEvents.contains(theEvent)) {
                    continue;
                }
                processEvent(theEvent, node, resultFinalRows, resultRootEvent);
            }
        }
    }

    public void result(EventBean[] row, int fromStreamNum, EventBean myEvent, Node myNode, Collection<EventBean[]> resultFinalRows, EventBean resultRootEvent) {
        row[streamNum] = myEvent;
        Node parentResultNode = myNode.getParent();
        parentNode.result(row, streamNum, myNode.getParentEvent(), parentResultNode, resultFinalRows, resultRootEvent);

        // record the fact that an event that was generated by a child
        haveChildResults = true;

        // If we had more then on result event for this stream, we need to track all the different events
        // generated by the child node
        if (singleResultNode == null) {
            completedEvents.add(myEvent);
        }
    }

    public void print(IndentWriter indentWriter) {
        indentWriter.println("BranchOptionalAssemblyNode streamNum=" + streamNum);
    }

    private void processEvent(EventBean theEvent, Node currentNode, Collection<EventBean[]> resultFinalRows, EventBean resultRootEvent) {
        EventBean[] row = new EventBean[numStreams];
        row[streamNum] = theEvent;
        parentNode.result(row, streamNum, currentNode.getParentEvent(), currentNode.getParent(), resultFinalRows, resultRootEvent);
    }
}
