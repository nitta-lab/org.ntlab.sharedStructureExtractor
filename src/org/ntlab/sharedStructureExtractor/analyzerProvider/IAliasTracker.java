package org.ntlab.sharedStructureExtractor.analyzerProvider;

public interface IAliasTracker extends IAliasCollector {
	
	void changeTrackingObject(String from, String to, boolean isSrcSide);
	
}