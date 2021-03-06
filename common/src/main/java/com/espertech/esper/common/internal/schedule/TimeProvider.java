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
package com.espertech.esper.common.internal.schedule;

import java.time.format.DateTimeFormatter;

/**
 * Provider of internal system time.
 * <p>
 * Internal system time is controlled either by a timer function or by external time events.
 */
public interface TimeProvider {
    /**
     * Returns the current runtime time.
     *
     * @return time that has last been set
     */
    long getTime();

    DateTimeFormatter getDefaultFormatter();
}
