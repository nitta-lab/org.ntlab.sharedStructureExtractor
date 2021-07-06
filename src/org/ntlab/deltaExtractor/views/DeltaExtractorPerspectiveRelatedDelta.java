package org.ntlab.deltaExtractor.views;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class DeltaExtractorPerspectiveRelatedDelta implements IPerspectiveFactory {

	@Override
	public void createInitialLayout(IPageLayout layout) {
		// Get the area of the editor.
		String editorArea = layout.getEditorArea();

		// Place the breakpoint view on the right.
		IFolderLayout breakpointViewArea = layout.createFolder("BreakpointViewArea", IPageLayout.RIGHT, 0.5f, editorArea);
		breakpointViewArea.addView(BreakPointViewRelatedDelta.ID);

		// Place the call tree view on the upper left.
		IFolderLayout callTreeViewArea = layout.createFolder("CallTreeViewArea", IPageLayout.BOTTOM, 0.25f, "BreakpointViewArea");
		callTreeViewArea.addView(CallTreeView.ID);
		
		// Place the trace point view on the lower right.
		IFolderLayout tracePointsViewArea = layout.createFolder("TracePointsViewArea", IPageLayout.BOTTOM, 0.5f, "CallTreeViewArea");
		tracePointsViewArea.addView(TracePointsRegisterView.ID);
		
		// Place the variable view on the upper right.
		IFolderLayout variableViewArea = layout.createFolder("VariableViewArea", IPageLayout.TOP, 0.25f, editorArea);
		variableViewArea.addView(VariableViewRelatedDelta.ID);
		
		// Place the call stack view on the upper left.
		IFolderLayout callStackViewArea = layout.createFolder("CallStackViewArea", IPageLayout.LEFT, 0.25f, "VariableViewArea");
		callStackViewArea.addView(CallStackViewRelatedDelta.ID);		
	}
}
