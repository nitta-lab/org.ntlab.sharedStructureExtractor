package org.ntlab.deltaExtractor;

import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class CallStackModel {
	private TracePoint tracePoint;
	private boolean isHighlighting;
	
	public CallStackModel(TracePoint tracePoint) {
		this.tracePoint = tracePoint;
	}
	
	public TracePoint getTracePoint() {
		return tracePoint;
	}
	
	public MethodExecution getMethodExecution() {
		return tracePoint.getMethodExecution();
	}

	public int getCallLineNo() {
		return tracePoint.getStatement().getLineNo();
	}
	
	public String getSignature() {
		return tracePoint.getMethodExecution().getSignature();
	}

	public String getCallStackSignature() {
		String signature = "";		
		signature = getSignature();
		MethodExecution methodExecution = tracePoint.getMethodExecution();
		String objectType = methodExecution.getThisClassName();
		objectType = objectType.substring(objectType.lastIndexOf(".") + 1);
		boolean isConstructor = methodExecution.isConstructor();
		String declaringType = Trace.getDeclaringType(signature, isConstructor);

		declaringType = removePackageNameFromSignature(declaringType);
		String methodName = Trace.getMethodName(signature);
		String args = "(";
		String delimiter = "";
		String[] argArray = signature.split("\\(")[1].split(",");
		for (String arg : argArray) {
			args += (delimiter + removePackageNameFromSignature(arg));
			delimiter = ", ";
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append(objectType);
		if (!declaringType.equals(objectType)) {
			sb.append("(" + declaringType + ")");
		}
		sb.append("." + methodName + args);
		signature = sb.toString();
		return signature;
	}
	
	private String removePackageNameFromSignature(String signature) {
		String subSignature = signature;
		while (true) {
			if (!(subSignature.contains("."))) break;
			subSignature = subSignature.substring(subSignature.indexOf(".") + 1);
			if (subSignature.isEmpty()) break;
			if (Character.isUpperCase(subSignature.charAt(0))) break;
		}
		return subSignature;
	}
	
	public boolean isHighlighting() {
		return isHighlighting;
	}
	
	public void setHighlighting(boolean isHighlighting) {
		this.isHighlighting = isHighlighting;
	}	
}
