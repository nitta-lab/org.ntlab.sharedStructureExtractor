package org.ntlab.deltaExtractor.analyzerProvider;

import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;

/**
 * Trace analyzer.
 * 
 * @author Isitani
 *
 */
public abstract class AbstractAnalyzer {
	protected Trace trace;
	
	public AbstractAnalyzer(Trace trace) {
		this.trace = trace;
	}
	
	public Trace getTrace() {
		return trace;
	}
}
