package org.ntlab.sharedStructureExtractor.views;

import java.util.List;
import java.util.Map;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.jface.viewers.TreeNodeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.sharedStructureExtractor.CallStackLabelProvider;
import org.ntlab.sharedStructureExtractor.CallStackModel;
import org.ntlab.sharedStructureExtractor.CallStackModels;
import org.ntlab.sharedStructureExtractor.DebuggingController;
import org.ntlab.sharedStructureExtractor.SharedStructureExtractorPlugin;
import org.ntlab.sharedStructureExtractor.JavaEditorOperator;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class CallStackView extends ViewPart {
	protected TreeViewer viewer;
	protected CallStackModel selectionCallStackModel;
	protected CallStackModels callStackModels = new CallStackModels();
	public static final String ID = "org.ntlab.deltaExtractor.callStackView";
	
	public CallStackView() {
	}

	@Override
	public void createPartControl(Composite parent) {
		viewer = new TreeViewer(parent);
		viewer.setContentProvider(new TreeNodeContentProvider());
		viewer.setLabelProvider(new CallStackLabelProvider());
		viewer.expandAll();
		
		// Add a listener that is called when the selection is changed and open the source file that contains the selected method.
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (element instanceof TreeNode) {
					Object value = ((TreeNode)element).getValue();
					if (value instanceof CallStackModel) {
						CallStackModel callStackModel = (CallStackModel)value;
						selectionCallStackModel = callStackModel;
						MethodExecution methodExecution = callStackModel.getMethodExecution();
						JavaEditorOperator.openSrcFileOfMethodExecution(methodExecution, callStackModel.getCallLineNo());
						additonalActionOnSelectionChanged(callStackModel);
					}
				}
			}
		});
		createActions();
		createToolBar();
		createMenuBar();
		createPopupMenu();
		SharedStructureExtractorPlugin.setActiveView(ID, this);
	}
	
	@Override
	public String getTitle() {
		return "CallStack";
	}

	@Override
	public void setFocus() {
		SharedStructureExtractorPlugin.setActiveView(ID, this);
		viewer.getControl().setFocus();
	}
	
	@Override
	public void dispose() {
		SharedStructureExtractorPlugin.removeView(ID, this);
	}
	
	protected void createActions() {

	}
	
	protected void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
	}
	
	protected void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
	}
	
	protected void createPopupMenu() {

	}
	
	protected void additonalActionOnSelectionChanged(CallStackModel selectedCallStackModel) {
		TracePoint tp = selectedCallStackModel.getTracePoint();
		TracePoint debuggingTp = DebuggingController.getInstance().getCurrentTp();
		VariableView variableView = (VariableView)SharedStructureExtractorPlugin.getActiveView(VariableView.ID);
		variableView.updateVariablesByTracePoints(tp, debuggingTp);
	}
	
	public void updateByTracePoint(TracePoint tp) {
		callStackModels.updateByTracePoint(tp);
		refresh();
		selectionCallStackModel = null;
	}

	public void refresh() {
		TreeNode[] nodes = callStackModels.getAllCallStacksTree();
		if (nodes == null || nodes[0] == null) {
			viewer.setInput(null);
			viewer.expandAll();
			return;
		}
		viewer.setInput(nodes);
		viewer.expandAll();
	}
	
	public void reset() {
		callStackModels.reset();
		refresh();
	}
	
	public CallStackModel getSelectionCallStackModel() {
		return selectionCallStackModel;
	}
	
	public boolean isSelectionOnTop() {
		if (selectionCallStackModel == null) return false;
		TreeNode[] nodes = callStackModels.getAllCallStacksTree();
		if (nodes == null || nodes[0] == null) return false;
		TreeNode[] children = nodes[0].getChildren();
		Object obj = children[0].getValue();
		if (!(obj instanceof CallStackModel)) return false;
		CallStackModel topCallStackModel = (CallStackModel)obj;
		return topCallStackModel.equals(selectionCallStackModel);
	}
	
	public Map<String, List<CallStackModel>> getCallStackModels() {
		return callStackModels.getAllCallStacks();
	}
	
	public void highlight(MethodExecution methodExecution) {
		callStackModels.highlight(methodExecution);
		viewer.refresh();
	}
	
	public void removeHighlight() {
		callStackModels.removeHighlight();
		viewer.refresh();
	}
}
