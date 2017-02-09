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
package com.espertech.esper.epl.enummethod.dot;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.EventType;
import com.espertech.esper.core.service.ExpressionResultCacheEntry;
import com.espertech.esper.core.service.ExpressionResultCacheForEnumerationMethod;
import com.espertech.esper.core.service.ExpressionResultCacheStackEntry;
import com.espertech.esper.epl.core.EngineImportService;
import com.espertech.esper.epl.core.StreamTypeService;
import com.espertech.esper.epl.core.StreamTypeServiceImpl;
import com.espertech.esper.epl.enummethod.eval.EnumEval;
import com.espertech.esper.epl.expression.core.*;
import com.espertech.esper.epl.expression.dot.ExprDotEvalVisitor;
import com.espertech.esper.epl.expression.visitor.ExprNodeIdentifierCollectVisitor;
import com.espertech.esper.epl.methodbase.*;
import com.espertech.esper.epl.rettype.EPType;
import com.espertech.esper.epl.rettype.EPTypeHelper;
import com.espertech.esper.event.EventAdapterService;
import com.espertech.esper.event.EventBeanUtility;
import com.espertech.esper.util.CollectionUtil;
import com.espertech.esper.util.JavaClassHelper;

import java.util.*;

public abstract class ExprDotEvalEnumMethodBase implements ExprDotEvalEnumMethod, ExpressionResultCacheStackEntry {

    private EnumMethodEnum enumMethodEnum;
    private String enumMethodUsedName;
    private int streamCountIncoming;
    private EnumEval enumEval;
    private int enumEvalNumRequiredEvents;
    private EPType typeInfo;

    private boolean cache;
    private long contextNumber = 0;

    protected ExprDotEvalEnumMethodBase() {
    }

    public abstract EventType[] getAddStreamTypes(String enumMethodUsedName, List<String> goesToNames, EventType inputEventType, Class collectionComponentType, List<ExprDotEvalParam> bodiesAndParameters, EventAdapterService eventAdapterService);

    public abstract EnumEval getEnumEval(EngineImportService engineImportService, EventAdapterService eventAdapterService, StreamTypeService streamTypeService, int statementId, String enumMethodUsedName, List<ExprDotEvalParam> bodiesAndParameters, EventType inputEventType, Class collectionComponentType, int numStreamsIncoming, boolean disablePropertyExpressionEventCollCache) throws ExprValidationException;

    public EnumMethodEnum getEnumMethodEnum() {
        return enumMethodEnum;
    }

    public void visit(ExprDotEvalVisitor visitor) {
        visitor.visitEnumeration(enumMethodEnum.getNameCamel());
    }

    public void init(Integer streamOfProviderIfApplicable, EnumMethodEnum enumMethodEnum, String enumMethodUsedName, EPType typeInfo, List<ExprNode> parameters, ExprValidationContext validationContext) throws ExprValidationException {

        final EventType eventTypeColl = EPTypeHelper.getEventTypeMultiValued(typeInfo);
        final EventType eventTypeBean = EPTypeHelper.getEventTypeSingleValued(typeInfo);
        final Class collectionComponentType = EPTypeHelper.getClassMultiValued(typeInfo);

        this.enumMethodEnum = enumMethodEnum;
        this.enumMethodUsedName = enumMethodUsedName;
        this.streamCountIncoming = validationContext.getStreamTypeService().getEventTypes().length;

        if (eventTypeColl == null && collectionComponentType == null && eventTypeBean == null) {
            throw new ExprValidationException("Invalid input for built-in enumeration method '" + enumMethodUsedName + "', expecting collection of event-type or scalar values as input, received " + EPTypeHelper.toTypeDescriptive(typeInfo));
        }

        // compile parameter abstract for validation against available footprints
        DotMethodFPProvided footprintProvided = DotMethodUtil.getProvidedFootprint(parameters);

        // validate parameters
        DotMethodInputTypeMatcher inputTypeMatcher = new DotMethodInputTypeMatcher() {
            public boolean matches(DotMethodFP footprint) {
                if (footprint.getInput() == DotMethodFPInputEnum.EVENTCOLL && eventTypeBean == null && eventTypeColl == null) {
                    return false;
                }
                if (footprint.getInput() == DotMethodFPInputEnum.SCALAR_ANY && collectionComponentType == null) {
                    return false;
                }
                return true;
            }
        };
        DotMethodFP footprint = DotMethodUtil.validateParametersDetermineFootprint(enumMethodEnum.getFootprints(), DotMethodTypeEnum.ENUM, enumMethodUsedName, footprintProvided, inputTypeMatcher);

        // validate input criteria met for this footprint
        if (footprint.getInput() != DotMethodFPInputEnum.ANY) {
            String message = "Invalid input for built-in enumeration method '" + enumMethodUsedName + "' and " + footprint.getParameters().length + "-parameter footprint, expecting collection of ";
            String received = " as input, received " + EPTypeHelper.toTypeDescriptive(typeInfo);
            if (footprint.getInput() == DotMethodFPInputEnum.EVENTCOLL && eventTypeColl == null) {
                throw new ExprValidationException(message + "events" + received);
            }
            if (footprint.getInput().isScalar() && collectionComponentType == null) {
                throw new ExprValidationException(message + "values (typically scalar values)" + received);
            }
            if (footprint.getInput() == DotMethodFPInputEnum.SCALAR_NUMERIC && !JavaClassHelper.isNumeric(collectionComponentType)) {
                throw new ExprValidationException(message + "numeric values" + received);
            }
        }

        // manage context of this lambda-expression in regards to outer lambda-expression that may call this one.
        ExpressionResultCacheForEnumerationMethod enumerationMethodCache = validationContext.getExprEvaluatorContext().getExpressionResultCacheService().getAllocateEnumerationMethod();
        enumerationMethodCache.pushStack(this);

        List<ExprDotEvalParam> bodiesAndParameters = new ArrayList<ExprDotEvalParam>();
        int count = 0;
        EventType inputEventType = eventTypeBean == null ? eventTypeColl : eventTypeBean;
        for (ExprNode node : parameters) {
            ExprDotEvalParam bodyAndParameter = getBodyAndParameter(enumMethodUsedName, count++, node, inputEventType, collectionComponentType, validationContext, bodiesAndParameters, footprint);
            bodiesAndParameters.add(bodyAndParameter);
        }

        this.enumEval = getEnumEval(validationContext.getEngineImportService(), validationContext.getEventAdapterService(), validationContext.getStreamTypeService(), validationContext.getStatementId(), enumMethodUsedName, bodiesAndParameters, inputEventType, collectionComponentType, streamCountIncoming, validationContext.isDisablePropertyExpressionEventCollCache());
        this.enumEvalNumRequiredEvents = enumEval.getStreamNumSize();

        // determine the stream ids of event properties asked for in the evaluator(s)
        HashSet<Integer> streamsRequired = new HashSet<Integer>();
        ExprNodeIdentifierCollectVisitor visitor = new ExprNodeIdentifierCollectVisitor();
        for (ExprDotEvalParam desc : bodiesAndParameters) {
            desc.getBody().accept(visitor);
            for (ExprIdentNode ident : visitor.getExprProperties()) {
                streamsRequired.add(ident.getStreamId());
            }
        }
        if (streamOfProviderIfApplicable != null) {
            streamsRequired.add(streamOfProviderIfApplicable);
        }

        // We turn on caching if the stack is not empty (we are an inner lambda) and the dependency does not include the stream.
        boolean isInner = !enumerationMethodCache.popLambda();
        if (isInner) {
            // If none of the properties that the current lambda uses comes from the ultimate parent(s) or subsequent streams, then cache.
            Deque<ExpressionResultCacheStackEntry> parents = enumerationMethodCache.getStack();
            boolean found = false;
            for (int req : streamsRequired) {
                ExprDotEvalEnumMethodBase first = (ExprDotEvalEnumMethodBase) parents.getFirst();
                int parentIncoming = first.streamCountIncoming - 1;
                int selfAdded = streamCountIncoming;    // the one we use ourselfs
                if (req > parentIncoming && req < selfAdded) {
                    found = true;
                }
            }
            cache = !found;
        }
    }

    public void setTypeInfo(EPType typeInfo) {
        this.typeInfo = typeInfo;
    }

    public EPType getTypeInfo() {
        return typeInfo;
    }

    public Object evaluate(Object target, EventBean[] eventsPerStream, boolean isNewData, ExprEvaluatorContext exprEvaluatorContext) {
        if (target instanceof EventBean) {
            target = Collections.singletonList((EventBean) target);
        }
        ExpressionResultCacheForEnumerationMethod enumerationMethodCache = exprEvaluatorContext.getExpressionResultCacheService().getAllocateEnumerationMethod();
        if (cache) {
            ExpressionResultCacheEntry<Long[], Object> cacheValue = enumerationMethodCache.getEnumerationMethodLastValue(this);
            if (cacheValue != null) {
                return cacheValue.getResult();
            }
            Collection coll = (Collection) target;
            if (coll == null) {
                return null;
            }
            EventBean[] eventsLambda = allocateCopyEventLambda(eventsPerStream);
            Object result = enumEval.evaluateEnumMethod(eventsLambda, coll, isNewData, exprEvaluatorContext);
            enumerationMethodCache.saveEnumerationMethodLastValue(this, result);
            return result;
        }

        contextNumber++;
        try {
            enumerationMethodCache.pushContext(contextNumber);
            Collection coll = (Collection) target;
            if (coll == null) {
                return null;
            }
            EventBean[] eventsLambda = allocateCopyEventLambda(eventsPerStream);
            return enumEval.evaluateEnumMethod(eventsLambda, coll, isNewData, exprEvaluatorContext);
        } finally {
            enumerationMethodCache.popContext();
        }
    }

    private EventBean[] allocateCopyEventLambda(EventBean[] eventsPerStream) {
        EventBean[] eventsLambda = new EventBean[enumEvalNumRequiredEvents];
        EventBeanUtility.safeArrayCopy(eventsPerStream, eventsLambda);
        return eventsLambda;
    }

    private ExprDotEvalParam getBodyAndParameter(String enumMethodUsedName,
                                                 int parameterNum,
                                                 ExprNode parameterNode,
                                                 EventType inputEventType,
                                                 Class collectionComponentType,
                                                 ExprValidationContext validationContext,
                                                 List<ExprDotEvalParam> priorParameters,
                                                 DotMethodFP footprint) throws ExprValidationException {

        // handle an expression that is a constant or other (not =>)
        if (!(parameterNode instanceof ExprLambdaGoesNode)) {

            // no node subtree validation is required here, the chain parameter validation has taken place in ExprDotNode.validate
            // validation of parameter types has taken place in footprint matching
            return new ExprDotEvalParamExpr(parameterNum, parameterNode, parameterNode.getExprEvaluator());
        }

        ExprLambdaGoesNode goesNode = (ExprLambdaGoesNode) parameterNode;

        // Get secondary
        EventType[] additionalTypes = getAddStreamTypes(enumMethodUsedName, goesNode.getGoesToNames(), inputEventType, collectionComponentType, priorParameters, validationContext.getEventAdapterService());
        String[] additionalStreamNames = goesNode.getGoesToNames().toArray(new String[goesNode.getGoesToNames().size()]);

        validateDuplicateStreamNames(validationContext.getStreamTypeService().getStreamNames(), additionalStreamNames);

        // add name and type to list of known types
        EventType[] addTypes = (EventType[]) CollectionUtil.arrayExpandAddElements(validationContext.getStreamTypeService().getEventTypes(), additionalTypes);
        String[] addNames = (String[]) CollectionUtil.arrayExpandAddElements(validationContext.getStreamTypeService().getStreamNames(), additionalStreamNames);

        StreamTypeServiceImpl types = new StreamTypeServiceImpl(addTypes, addNames, new boolean[addTypes.length], null, false);

        // validate expression body
        ExprNode filter = goesNode.getChildNodes()[0];
        try {
            ExprValidationContext filterValidationContext = new ExprValidationContext(types, validationContext);
            filter = ExprNodeUtility.getValidatedSubtree(ExprNodeOrigin.DECLAREDEXPRBODY, filter, filterValidationContext);
        } catch (ExprValidationException ex) {
            throw new ExprValidationException("Error validating enumeration method '" + enumMethodUsedName + "' parameter " + parameterNum + ": " + ex.getMessage(), ex);
        }

        ExprEvaluator filterEvaluator = filter.getExprEvaluator();
        DotMethodFPParamTypeEnum expectedType = footprint.getParameters()[parameterNum].getType();
        // Lambda-methods don't use a specific expected return-type, so passing null for type is fine.
        DotMethodUtil.validateSpecificType(enumMethodUsedName, DotMethodTypeEnum.ENUM, expectedType, null, filterEvaluator.getType(), parameterNum, filter);

        int numStreamsIncoming = validationContext.getStreamTypeService().getEventTypes().length;
        return new ExprDotEvalParamLambda(parameterNum, filter, filterEvaluator,
                numStreamsIncoming, goesNode.getGoesToNames(), additionalTypes);
    }

    private void validateDuplicateStreamNames(String[] streamNames, String[] additionalStreamNames) throws ExprValidationException {
        for (int added = 0; added < additionalStreamNames.length; added++) {
            for (int exist = 0; exist < streamNames.length; exist++) {
                if (streamNames[exist] != null && streamNames[exist].equalsIgnoreCase(additionalStreamNames[added])) {
                    String message = "Error validating enumeration method '" + enumMethodUsedName + "', the lambda-parameter name '" + additionalStreamNames[added] + "' has already been declared in this context";
                    throw new ExprValidationException(message);
                }
            }
        }
    }

    public String toString() {
        return this.getClass().getSimpleName() +
                " lambda=" + enumMethodEnum;
    }
}
