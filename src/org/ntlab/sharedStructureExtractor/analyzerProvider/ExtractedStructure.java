package org.ntlab.sharedStructureExtractor.analyzerProvider;

import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;


/**
 * The result of delta extraction
 * @author Nitta
 *
 */
public class ExtractedStructure {

	private Delta delta = new Delta();
	private MethodExecution coordinator = null;
	private MethodExecution creationCallTree;
	private TracePoint relatedTracePoint = null;
	
	public Delta getDelta() {
		return delta;
	}

	public MethodExecution getCoordinator() {
		return coordinator;
	}


	public void setCoordinator(MethodExecution coordinator) {
		this.coordinator = coordinator;
	}

	public void createParent(MethodExecution methodExecution) {
		coordinator = methodExecution;
	}

	public void addSrcSide(Reference reference) {
		delta.addSrcSide(reference);
	}

	public void addDstSide(Reference reference) {
		delta.addDstSide(reference);
	}

	public void changeParent() {
	}

	public void setCreationMethodExecution(MethodExecution callTree) {
		creationCallTree = callTree;
	}

	public MethodExecution getCreationCallTree() {
		return creationCallTree;
	}
	
	public void setRelatedTracePoint(TracePoint relatedTracePoint) {
		this.relatedTracePoint  = relatedTracePoint;
	}

	public TracePoint getRelatedTracePoint() {
		return relatedTracePoint;
	}
	
}
