package org.ntlab.deltaExtractor.analyzerProvider;

import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayCreate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

/**
 * Alias of (reference to) an object
 * @author Isitani
 *
 */
public class Alias {
	private String objectId;
	private TracePoint occurrencePoint; // TracePoint where the object is referred.
	private AliasType aliasType;
	private int index;
	
	public enum AliasType {
		// Enter to a method execution.
		FORMAL_PARAMETER, 
		THIS, 
		METHOD_INVOCATION, 
		CONSTRACTOR_INVOCATION, 
		
		// Change of the tracking object.
		FIELD, 
		CONTAINER, 
		ARRAY_ELEMENT, 
		ARRAY, 
		ARRAY_CREATE, 
 
		// Exit from a method execution.
		ACTUAL_ARGUMENT, 
		RECEIVER, 
		RETURN_VALUE
	}
	
	public Alias(AliasType aliasType, int index, String objectId, TracePoint occurrencePoint) {
		this.aliasType = aliasType;
		this.index = index;
		this.objectId = objectId;
		this.occurrencePoint = occurrencePoint;
	}
	
	public AliasType getAliasType() {
		return aliasType;
	}
	
	public int getIndex() {
		return index;
	}
	
	public String getObjectId() {
		return objectId;
	}
	
	public TracePoint getOccurrencePoint() {
		return occurrencePoint;
	}
	
	public MethodExecution getMethodExecution() {
		return occurrencePoint.getMethodExecution();
	}
	
	public String getMethodSignature() {
		String signature = occurrencePoint.getMethodExecution().getCallerSideSignature();
		if (signature != null) return signature;
		return occurrencePoint.getMethodExecution().getSignature();
//		return occurrencePoint.getMethodExecution().getCallerSideSignature();
	}

	/**
	 * Get time stamp of statement.
	 * @return
	 */
	public long getTimeStamp() {
		if (!occurrencePoint.isValid()) {
			return occurrencePoint.getMethodExecution().getEntryTime();
		}
		long stTimeStamp = occurrencePoint.getStatement().getTimeStamp();
		Statement st = occurrencePoint.getStatement();
		if (aliasType == AliasType.RETURN_VALUE) {
			stTimeStamp = occurrencePoint.getMethodExecution().getExitTime();
		} else if (aliasType == AliasType.METHOD_INVOCATION && st instanceof MethodInvocation) {
			stTimeStamp = ((MethodInvocation) st).getCalledMethodExecution().getExitTime();
		} else if (aliasType == AliasType.FORMAL_PARAMETER) {
			stTimeStamp = occurrencePoint.getMethodExecution().getEntryTime();			
		}
		return stTimeStamp;
	}
	
	public int getLineNo() {
		try {
			Statement statement = occurrencePoint.getStatement();
			return statement.getLineNo();
		} catch (Exception e) {
			return -1;
		}
	}
	
	public void setIndex(int index) {
		this.index = index;
	}
	
	public String getObjectType() {
		TracePoint tmpTp;
		Statement callerStatement;
		Statement statement = occurrencePoint.getStatement();
		switch (aliasType) {
		// Enter to a method execution.
		case FORMAL_PARAMETER:
			tmpTp = occurrencePoint.duplicate();
			tmpTp.stepBackOver();
			callerStatement = tmpTp.getStatement();
			if (callerStatement instanceof MethodInvocation) {
				MethodExecution me = ((MethodInvocation)callerStatement).getCalledMethodExecution();
				return me.getArguments().get(index).getActualType();
			}
		case THIS:
			if (statement instanceof FieldAccess) {
				return ((FieldAccess)statement).getContainerClassName();
			}
		case METHOD_INVOCATION:
		case CONSTRACTOR_INVOCATION:
			if (statement instanceof MethodInvocation) {
				MethodExecution me = ((MethodInvocation)statement).getCalledMethodExecution();
				return me.getReturnValue().getActualType();
			}
		// Change of the tracking object.
		case FIELD:
			if (statement instanceof FieldAccess) {
				return ((FieldAccess)statement).getValueClassName();
			}
		case CONTAINER:
			if (statement instanceof FieldAccess) {
				return ((FieldAccess)statement).getContainerClassName();
			}
		case ARRAY_ELEMENT:
			if (statement instanceof ArrayAccess) {
				return ((ArrayAccess)statement).getValueClassName();
			}
		case ARRAY:
			if (statement instanceof ArrayAccess) {
				return ((ArrayAccess)statement).getArrayClassName();
			}
		case ARRAY_CREATE:
			if (statement instanceof ArrayCreate) {
				return ((ArrayCreate)statement).getArrayClassName();
			}

		// Exit from a method execution.
		case ACTUAL_ARGUMENT:
			if (statement instanceof MethodInvocation) {
				MethodExecution me = ((MethodInvocation)statement).getCalledMethodExecution();
				return me.getArguments().get(index).getActualType();
			}
		case RECEIVER:
			if (statement instanceof MethodInvocation) {
				MethodExecution me = ((MethodInvocation)statement).getCalledMethodExecution();
				return me.getThisClassName();
			}
		case RETURN_VALUE:
			tmpTp = occurrencePoint.duplicate();
			tmpTp.stepOver();
			callerStatement = tmpTp.getStatement();
			if (callerStatement instanceof MethodInvocation) {
				MethodExecution me = ((MethodInvocation)callerStatement).getCalledMethodExecution();
				return me.getReturnValue().getActualType();
			}
		}
		return "";
	}
}