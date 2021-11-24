package org.ntlab.sharedStructureExtractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ThreadInstance;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class VariableUpdatePointFinder {
	private static VariableUpdatePointFinder theInstance = new VariableUpdatePointFinder();
	private Trace trace;
	private Map<String, Map<String, List<TracePoint>>> updatePoints = new HashMap<>(); // Map from a container's id and a field name to the field update points.
	private Map<String, Map<String, List<TracePoint>>> definitionInvocationPoints = new HashMap<>(); // Map from a receiver's id and an argument's id to the collection add points.
	private Map<String, TracePoint> gettingIteratorPoints = new HashMap<>(); // Map from an iterator's id to the point where it is obtained.
	private Map<String, List<TracePoint>> changeOtherCollectionPoints = new HashMap<>(); // Map from a collection's id to the point where it is converted from the source collection.

	public void setTrace(Trace trace) {
		this.trace = trace;
		init();
	}

	public static VariableUpdatePointFinder getInstance() {
		return theInstance;
	}

	private void init() {
		reset();
		registerUpdatePoints();
		System.out.println(updatePoints);
		System.out.println(definitionInvocationPoints);
		sort(updatePoints);
		sort(definitionInvocationPoints);
		System.out.println(updatePoints);
		System.out.println(definitionInvocationPoints);
	}

	private void registerUpdatePoints() {
		for (Map.Entry<String, ThreadInstance> entry : trace.getAllThreads().entrySet()) {
			ThreadInstance thread = entry.getValue();
			for (MethodExecution me : thread.getRoot()) {
				TracePoint start = me.getEntryPoint();
				while (start.stepFull()) {
					Statement statement = start.getStatement();
					if (statement instanceof FieldUpdate) {
						registerFieldUpdatePoint(start, (FieldUpdate)statement);
					} else if (statement instanceof ArrayUpdate) {
						registerArrayUpdatePoint(start, (ArrayUpdate)statement);
					} else if (statement instanceof MethodInvocation) {
						MethodInvocation mi = (MethodInvocation)statement;
						MethodExecution calledME = mi.getCalledMethodExecution();
						String methodName = calledME.getSignature();
						List<ObjectReference> args = calledME.getArguments();
						if (methodName.contains(".add(") || methodName.contains(".addElement(")) {
							registerdefinitionInvocationPoint(start, calledME);							
						} else if (methodName.contains(".iterator(") || methodName.contains("Iterator(")) {
							registerIteratorPoint(start, calledME);
						} else if (args.size() == 1 && args.get(0).getActualType().startsWith("java.util.")) {
							ObjectReference returnValue = calledME.getReturnValue();						
							if (calledME.getThisClassName().startsWith("java.util.") && !(calledME.getThisObjId().equals("0"))) {
								String toCollectionId = calledME.getThisObjId();
								registerChangeOtherCollectionPoint(start, toCollectionId);
							} else if (returnValue != null && returnValue.getActualType().startsWith("java.util.")) {
								String toCollectionId = returnValue.getId();
								registerChangeOtherCollectionPoint(start, toCollectionId);
							}
						}							
					}
				}
			}
		}		
	}
	
	private void registerFieldUpdatePoint(TracePoint tp, FieldUpdate fu) {
		String objectId = fu.getContainerObjId();
		String fieldName = fu.getFieldName();
		register(updatePoints, objectId, fieldName, tp);
	}
	
	private void registerArrayUpdatePoint(TracePoint tp, ArrayUpdate au) {
		String objectId = au.getArrayObjectId();
		String index = String.valueOf(au.getIndex());
		register(updatePoints, objectId, index, tp);
	}
	
	private void registerdefinitionInvocationPoint(TracePoint tp, MethodExecution calledME) {
		List<ObjectReference> args = calledME.getArguments();
		if (args.size() == 1) {
			String receiverId = calledME.getThisObjId();
			String argId = args.get(0).getId();
			register(definitionInvocationPoints, receiverId, argId, tp);
		}
	}
	
	private void registerIteratorPoint(TracePoint tp, MethodExecution calledME) {
		ObjectReference returnIteratorValue = calledME.getReturnValue();
		String iteratorId = returnIteratorValue.getId();
		gettingIteratorPoints.put(iteratorId, tp.duplicate());
	}
	
	private void registerChangeOtherCollectionPoint(TracePoint tp, String toCollectionId) {
		List<TracePoint> tracePoints = changeOtherCollectionPoints.get(toCollectionId);
		if (tracePoints == null) {
			tracePoints = new ArrayList<TracePoint>();
			changeOtherCollectionPoints.put(toCollectionId, tracePoints);
		}
		tracePoints.add(tp.duplicate());
	}
	
	private void register(Map<String, Map<String, List<TracePoint>>> map, String key1, String key2, TracePoint tp) {
		Map<String, List<TracePoint>> innerMap = map.get(key1);
		if (innerMap == null) {
			innerMap = new HashMap<>();
			map.put(key1, innerMap);
		}
		List<TracePoint> tracePoints = innerMap.get(key2);
		if (tracePoints == null) {
			tracePoints = new ArrayList<>();
			innerMap.put(key2, tracePoints);
		}
		tracePoints.add(tp.duplicate());
	}

	private void sort(Map<String, Map<String, List<TracePoint>>> map) {
		for (Map<String, List<TracePoint>> innerMap : map.values()) {
			for (List<TracePoint> tracePoints : innerMap.values()) {
				Collections.sort(tracePoints, new Comparator<TracePoint>() {
					@Override
					public int compare(TracePoint arg0, TracePoint arg1) {
						long time0 = arg0.getStatement().getTimeStamp();
						long time1 = arg1.getStatement().getTimeStamp();
						return (time0 < time1) ? -1 : 1;
					}
				});
			}
		}		
	}

	public TracePoint getPoint(String objectId, String fieldName, TracePoint before) {
		return getPoint(updatePoints, objectId, fieldName, before);
	}
	
	public TracePoint getDefinitionInvocationPoint(String receiverId, String argId, TracePoint before) {
		return getPoint(definitionInvocationPoints, receiverId, argId, before);
	}
	
	public TracePoint getIteratorPoint(String iteratorId) {
		return gettingIteratorPoints.get(iteratorId);
	}
	
	public TracePoint getTransferCollectionPoint(String toCollectionId, TracePoint before) {
		List<TracePoint> tracePoints = changeOtherCollectionPoints.get(toCollectionId);
		long beforeTime = before.getStatement().getTimeStamp();
		TracePoint tmp = null;
		for (TracePoint tp : tracePoints) {
			long time = tp.getStatement().getTimeStamp();
			if (time >= beforeTime) return tmp;
			tmp = tp;	
		}
		return tmp;
	}
	
	private TracePoint getPoint(Map<String, Map<String, List<TracePoint>>> map, String key1, String key2, TracePoint before) {
		Map<String, List<TracePoint>> innerMap = map.get(key1);
		if (innerMap == null) return null;
		List<TracePoint> tracePoints = innerMap.get(key2);
		if (tracePoints == null) return null;
		long beforeTime = before.getStatement().getTimeStamp();
		TracePoint tmp = null;
		for (TracePoint tp : tracePoints) {
			long time = tp.getStatement().getTimeStamp();
			if (time >= beforeTime) return tmp;
			tmp = tp;
		}
		return tmp;		
	}
	
	private void reset() {
		updatePoints.clear();
		definitionInvocationPoints.clear();
		gettingIteratorPoints.clear();
		changeOtherCollectionPoints.clear();
	}
}
