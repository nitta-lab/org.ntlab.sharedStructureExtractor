package org.ntlab.sharedStructureExtractor.analyzerProvider;

import java.util.ArrayList;

import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;


/**
 * Object graph related to the extracted delta.
 * @author Nitta
 *
 */
public class Delta {

	private ArrayList<Reference> srcSide = new ArrayList<Reference>();
	private ArrayList<Reference> dstSide = new ArrayList<Reference>();

	public void addSrcSide(Reference r){
		getSrcSide().add(r);
	}
	
	public void addDstSide(Reference r){
		getDstSide().add(r);
	}

	public ArrayList<Reference> getSrcSide() {
		return srcSide;
	}

	public ArrayList<Reference> getDstSide() {
		return dstSide;
	}
}
