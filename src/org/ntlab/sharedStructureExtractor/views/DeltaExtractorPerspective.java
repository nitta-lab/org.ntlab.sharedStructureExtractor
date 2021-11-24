package org.ntlab.sharedStructureExtractor.views;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class DeltaExtractorPerspective implements IPerspectiveFactory {

	@Override
	public void createInitialLayout(IPageLayout layout) {
		// Get the area of the editor.
		String editorArea = layout.getEditorArea();

		// Place the breakpoint view on the right.
		IFolderLayout breakpointViewArea = layout.createFolder("BreakPointViewArea", IPageLayout.RIGHT, 0.5f, editorArea);
		breakpointViewArea.addView(BreakPointView.ID);
		
		// Place the call stack view on the upper left.
		IFolderLayout callStackViewArea = layout.createFolder("CallStackViewArea", IPageLayout.TOP, 0.25f, editorArea);
		callStackViewArea.addView(CallStackView.ID);
		
		// Place the variable view on the upper right.
		IFolderLayout variableViewArea = layout.createFolder("VariableViewArea", IPageLayout.TOP, 0.25f, "BreakPointViewArea");
		variableViewArea.addView(VariableView.ID);
	}
}
