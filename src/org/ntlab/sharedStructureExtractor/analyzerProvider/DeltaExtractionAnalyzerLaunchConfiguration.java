package org.ntlab.sharedStructureExtractor.analyzerProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.ntlab.traceAnalysisPlatform.IAdditionalLaunchConfiguration;

public class DeltaExtractionAnalyzerLaunchConfiguration implements IAdditionalLaunchConfiguration {
	public static final String ANALYZER_PATH = "org/ntlab/sharedStructureExtractor/analyzerProvider/DeltaExtractionAnalyzer.class";
	public static final String ANALYZER_PACKAGE = "org.ntlab.sharedStructureExtractor.analyzerProvider";
	public static final String ANALYZER_CLASS = "DeltaExtractionAnalyzer";
	
	@Override
	public String[] getAdditionalClasspaths() {
		try {
			List<String> classPathList = new ArrayList<>();
			String tracerClassPath = FileLocator.resolve(this.getClass().getClassLoader().getResource(ANALYZER_PATH)).getPath();
			String classPath = tracerClassPath.substring(0, tracerClassPath.length() - ANALYZER_PATH.length());
			classPathList.add(classPath);			
			return classPathList.toArray(new String[classPathList.size()]);
		} catch (IOException e) {
			e.printStackTrace();
		}
		throw new IllegalStateException();
	}
}
