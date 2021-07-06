package org.ntlab.deltaExtractor.analyzerProvider;

import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class DeltaInfo {
	private MethodExecution coordinator;
	private TracePoint relatedPoint;
	private Reference relatedPointReference;
	private DeltaRelatedAliasCollector aliasCollector;

	public DeltaInfo(MethodExecution coordinator, TracePoint relatedPoint, Reference relatedPointReference, DeltaRelatedAliasCollector aliasCollector) {
		this.coordinator = coordinator;
		this.relatedPoint = relatedPoint;
		this.relatedPointReference = relatedPointReference;
		this.aliasCollector = aliasCollector;
	}

	public MethodExecution getCoordinator() {
		return coordinator;
	}

	public TracePoint getRelatedPoint() {
		return relatedPoint;
	}

	public Reference getRelatedPointReference() {
		return relatedPointReference;
	}

	public DeltaRelatedAliasCollector getAliasCollector() {
		return aliasCollector;
	}
}
