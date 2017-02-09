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
package com.espertech.esper.epl.core;

import com.espertech.esper.epl.variable.VariableReader;

import java.util.LinkedHashMap;
import java.util.Map;

public class MethodPollingViewableMeta {
    private final Class declaringClass;
    private final Map<String, Object> optionalMapType;
    private final LinkedHashMap<String, Object> optionalOaType;
    private final Object invocationTarget;
    private final MethodPollingExecStrategyEnum strategy;
    private final boolean isCollection;
    private final boolean isIterator;
    private final VariableReader variableReader;
    private final String variableName;

    public MethodPollingViewableMeta(Class declaringClass, Map<String, Object> optionalMapType, LinkedHashMap<String, Object> optionalOaType, Object invocationTarget, MethodPollingExecStrategyEnum strategy, boolean isCollection, boolean isIterator, VariableReader variableReader, String variableName) {
        this.declaringClass = declaringClass;
        this.optionalMapType = optionalMapType;
        this.optionalOaType = optionalOaType;
        this.invocationTarget = invocationTarget;
        this.strategy = strategy;
        this.isCollection = isCollection;
        this.isIterator = isIterator;
        this.variableReader = variableReader;
        this.variableName = variableName;
    }

    public Class getDeclaringClass() {
        return declaringClass;
    }

    public Map<String, Object> getOptionalMapType() {
        return optionalMapType;
    }

    public LinkedHashMap<String, Object> getOptionalOaType() {
        return optionalOaType;
    }

    public Object getInvocationTarget() {
        return invocationTarget;
    }

    public MethodPollingExecStrategyEnum getStrategy() {
        return strategy;
    }

    public boolean isCollection() {
        return isCollection;
    }

    public boolean isIterator() {
        return isIterator;
    }

    public VariableReader getVariableReader() {
        return variableReader;
    }

    public String getVariableName() {
        return variableName;
    }
}
