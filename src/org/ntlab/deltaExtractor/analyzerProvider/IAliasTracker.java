package org.ntlab.deltaExtractor.analyzerProvider;

public interface IAliasTracker extends IAliasCollector {
	
	void changeTrackingObject(String from, String to, boolean isSrcSide);
	
}