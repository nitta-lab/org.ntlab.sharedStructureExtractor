package org.ntlab.sharedStructureExtractor.analyzerProvider;

import java.util.List;

public interface IAliasCollector {

	void addAlias(Alias alias);

	List<Alias> getAliasList();

}