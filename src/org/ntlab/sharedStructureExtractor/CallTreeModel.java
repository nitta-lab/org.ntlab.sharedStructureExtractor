package org.ntlab.sharedStructureExtractor;

import java.util.ArrayList;
import java.util.List;

import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;

public class CallTreeModel {
	private MethodExecution methodExecution;
	private boolean isHighlighting;
	private CallTreeModel parent;
	private List<CallTreeModel> children = new ArrayList<>();
	
	public CallTreeModel(MethodExecution methodExecution) {
		this.methodExecution = methodExecution;
	}
	
	public MethodExecution getMethodExecution() {
		return methodExecution;
	}
	
	public String getSignature() {
		return methodExecution.getSignature();
	}
	
	public String getCallTreeSignature() {
		String signature = "";		
		signature = getSignature();
		String objectType = methodExecution.getThisClassName();
		objectType = objectType.substring(objectType.lastIndexOf(".") + 1);
		boolean isConstructor = methodExecution.isConstructor();
		String declaringType = Trace.getDeclaringType(signature, isConstructor);
		declaringType = declaringType.substring(declaringType.lastIndexOf(".") + 1);
		String methodName = Trace.getMethodName(signature);
		String args = "(";
		String delimiter = "";
		String[] argArray = signature.split("\\(")[1].split(",");
		for (String arg : argArray) {
			args += (delimiter + arg.substring(arg.lastIndexOf(".") + 1));
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

	public boolean isHighlighting() {
		return isHighlighting;
	}
	
	public void setHighlighting(boolean isHighlighting) {
		this.isHighlighting = isHighlighting;
	}
	
	public CallTreeModel getParent() {
		return parent;
	}
	
	public List<CallTreeModel> getChildren() {
		return children;
	}
	
	public void setParent(CallTreeModel parent) {
		this.parent = parent;
	}
	
	public void addChildren(CallTreeModel child) {
		this.children.add(child);
	}
}
