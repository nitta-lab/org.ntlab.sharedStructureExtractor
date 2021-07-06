package org.ntlab.deltaExtractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.internal.debug.core.breakpoints.JavaLineBreakpoint;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class TraceBreakPoints {
	private Trace trace;
	private Map<String, Map<Integer, TraceBreakPoint>> traceBreakPoints = new HashMap<>();
	private List<TracePoint> histories = new ArrayList<>();
	private TracePoint lastReferencePoint;
	
	public TraceBreakPoints(Trace trace) {
		this.trace = trace;
	}

	public List<TraceBreakPoint> getAllTraceBreakPoints() {
		List<TraceBreakPoint> list = new ArrayList<>();
		for (Map<Integer, TraceBreakPoint> innerMap : traceBreakPoints.values()) {
			list.addAll(innerMap.values());
		}
		Collections.sort(list, new Comparator<TraceBreakPoint>() {
			@Override
			public int compare(TraceBreakPoint arg0, TraceBreakPoint arg1) {
				if (arg0.getMethodSignature().equals(arg1.getMethodSignature())) {
					return (arg0.getLineNo() < arg1.getLineNo()) ? -1 : 1;	
				}
				return arg0.getMethodSignature().compareTo(arg1.getMethodSignature());
			}
		});
		return list;
	}

	public boolean addTraceBreakPoint(String inputSignature, int lineNo) {
		boolean isSuccess = addTraceBreakPoint(inputSignature, lineNo, true);
		return isSuccess;
	}
	
	public boolean addTraceBreakPoint(String inputSignature, int lineNo, boolean isAvailable) {
		String methodSignature = findMethodSignaureOnTrace(inputSignature);
		if (methodSignature == null) return false;
		Map<Integer, TraceBreakPoint> innerMap = traceBreakPoints.get(methodSignature);
		if (innerMap == null) {
			innerMap = new HashMap<>();
			traceBreakPoints.put(methodSignature, innerMap);
		}
		if (innerMap.containsKey(lineNo)) return false;
		try {
			TraceBreakPoint tbp = TraceBreakPoint.createNewTraceBreakPoint(methodSignature, lineNo, isAvailable, inputSignature);
			innerMap.put(lineNo, tbp);
			addHistories(tbp);
			return true;		
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
	
	private void removeTraceBreakPoint(String methodSignature, int lineNo) {
		Map<Integer, TraceBreakPoint> innerMap = traceBreakPoints.get(methodSignature);
		if (innerMap == null) return;
		TraceBreakPoint tbp = innerMap.remove(lineNo);
		if (tbp != null) removeHistories(tbp);
		if (innerMap.isEmpty()) traceBreakPoints.remove(methodSignature);
	}
	
	public void removeTraceBreakPoint(TraceBreakPoint traceBreakPoint) {
		String methodSignature = traceBreakPoint.getMethodSignature();
		int lineNo = traceBreakPoint.getLineNo();
		removeTraceBreakPoint(methodSignature, lineNo);
	}
	
	public void importBreakpointFromEclipse() {
		IBreakpoint[] breakpoints = DebugPlugin.getDefault().getBreakpointManager().getBreakpoints();
		for (IBreakpoint breakpoint : breakpoints) {
			if (!(breakpoint instanceof JavaLineBreakpoint)) continue;
			try {
				IMarker breakpointMarker = breakpoint.getMarker();
				Map<String, Object> attributes = breakpointMarker.getAttributes();
				String type = (String)attributes.get("org.eclipse.jdt.debug.core.typeName");
				type = type.substring(type.lastIndexOf(".") + 1);
				int lineNo = (int)attributes.get(IMarker.LINE_NUMBER);
				boolean available = (boolean)attributes.get(IBreakpoint.ENABLED);
				String message = (String)attributes.get(IMarker.MESSAGE);
				String methodName = message.substring(message.indexOf("-") + 2);
				methodName = methodName.replace(" ", "");
				String signature; 
				if (methodName.startsWith(type + "(")) {
					signature = methodName; // In a constructor
				} else {
					signature = type + "." + methodName;
				}
				signature = signature.replace("$", "."); // '$' of an anonymous or inner class becomes '.' in the method signature.
				signature = signature.replaceAll("<.*>", ""); // The information about generics type is not recorded in a trace.
				addTraceBreakPoint(signature, lineNo, available);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
	}
	
	private String findMethodSignaureOnTrace(String inputSignature) {
		HashSet<String> methodSignatures = trace.getAllMethodSignatures();
		for (String signature : methodSignatures) {
			String signatureFront = signature.substring(0, signature.indexOf("(") + 1);
			String inputSignatureFront = inputSignature.substring(0, inputSignature.indexOf("(") + 1);
			if (!(signatureFront.endsWith(inputSignatureFront))) continue;
			String signatureBack = signature.substring(signature.indexOf("(") + 1);
			String[] signatureArgs = signatureBack.split(",");
			String inputSignatureBack = inputSignature.substring(inputSignature.indexOf("(") + 1);
			String[] inputSignatureArgs = inputSignatureBack.split(",");
			if (signatureArgs.length != inputSignatureArgs.length) continue;
			boolean isMatch = true;
			for (int i = 0; i < signatureArgs.length; i++) {
				if (!(signatureArgs[i].endsWith(inputSignatureArgs[i]))) {
					isMatch = false;
					break;
				}
			}
			if (isMatch) return signature;
		}
		return null;
	}

	private void addHistories(TraceBreakPoint tbp) {
		for (TracePoint tp : tbp.getTracePoints()) {
			histories.add(tp);
		}
		Collections.sort(histories, new Comparator<TracePoint>() {
			@Override
			public int compare(TracePoint o1, TracePoint o2) {
				long o1Time = getTime(o1);
				long o2Time = getTime(o2);
				return (o1Time < o2Time) ? -1 : 1;
			}			
			private long getTime(TracePoint tp) {
				Statement statement = tp.getStatement();
				if (statement instanceof MethodInvocation) {
					return ((MethodInvocation)statement).getCalledMethodExecution().getEntryTime();
				}
				return statement.getTimeStamp();
			}
		});
		confirm();
	}
	
	private void removeHistories(TraceBreakPoint tbp) {
		List<TracePoint> removedPoints = tbp.getTracePoints();
		Iterator<TracePoint> it = histories.iterator();
		while (it.hasNext()) {
			TracePoint tp = it.next();
			if (removedPoints.contains(tp)) it.remove();
		}
		confirm();
	}

	public TracePoint getFirstTracePoint() {
		return getNextTracePoint(0L);
	}

	public TracePoint getNextTracePoint(long time) {
		for (TracePoint tp : histories) {
			if (!(checkAvailable(tp))) continue;
			if (getTime(tp) > time) {
				lastReferencePoint = tp;
				confirm();
				return tp.duplicate();
			}
		}
		lastReferencePoint = null;
		confirm();
		return null;
	}
	
	public TracePoint getPreviousTracePoint(long time) {
		TracePoint tmp = null;
		for (TracePoint tp : histories) {
			if (!(checkAvailable(tp))) continue;
			if (getTime(tp) >= time) {
				lastReferencePoint = tmp;
				confirm();
				return (tmp != null) ? tmp.duplicate() : null;
			}
			tmp = tp;
		}
		lastReferencePoint = tmp;
		confirm();
		return (tmp != null) ? tmp.duplicate() : null;
	}

	private TraceBreakPoint getTraceBreakPoint(TracePoint tp) {
		String signature = tp.getMethodExecution().getSignature();
		int lineNo = tp.getStatement().getLineNo();
		Map<Integer, TraceBreakPoint> innerMap = traceBreakPoints.get(signature);
		return (innerMap != null) ? innerMap.get(lineNo) : null;
	}
	
	private boolean checkAvailable(TracePoint tp) {
		TraceBreakPoint tbp = getTraceBreakPoint(tp);
		return (tbp != null) ? tbp.isAvailable() : false;
	}
	
	public void clear() {
		traceBreakPoints.clear();
	}
	
	private long getTime(TracePoint tp) {
		Statement statement = tp.getStatement();
		if (statement instanceof MethodInvocation) {
			return ((MethodInvocation)statement).getCalledMethodExecution().getEntryTime();
		}
		return statement.getTimeStamp();
	}

	private void confirm() {
		System.out.println();
		if (lastReferencePoint == null) {
			System.out.println("cur: " + "Not Exist");
		} else {
			System.out.println("cur: " + getTime(lastReferencePoint));	
		}
		for (TracePoint tp : histories) {
			String signature = tp.getMethodExecution().getSignature();
			int lineNo = tp.getStatement().getLineNo();
			long time = getTime(tp);
			StringBuilder msg = new StringBuilder();
			msg.append(time + "	" + signature + " line: " + lineNo);
			if (tp.equals(lastReferencePoint)) msg.append(" <-<-<-<-<-");
			System.out.println(msg);
		}
		System.out.println();
	}	
}
