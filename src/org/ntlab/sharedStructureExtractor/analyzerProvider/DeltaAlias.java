package org.ntlab.sharedStructureExtractor.analyzerProvider;

import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class DeltaAlias extends Alias {
	boolean bSrcSide = false;

	public DeltaAlias(AliasType aliasType, int index, String objectId, TracePoint occurrencePoint, boolean isSrcSide) {
		super(aliasType, index, objectId, occurrencePoint);
		bSrcSide = isSrcSide;
	}

	public boolean isSrcSide() {
		return bSrcSide;
	}
}
