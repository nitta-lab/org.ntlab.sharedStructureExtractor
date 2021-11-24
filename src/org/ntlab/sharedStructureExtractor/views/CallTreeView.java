package org.ntlab.sharedStructureExtractor.views;

import java.util.List;

import org.eclipse.core.resources.IMarker;
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
import org.ntlab.sharedStructureExtractor.CallTreeLabelProvider;
import org.ntlab.sharedStructureExtractor.CallTreeModel;
import org.ntlab.sharedStructureExtractor.CallTreeModels;
import org.ntlab.sharedStructureExtractor.DebuggingController;
import org.ntlab.sharedStructureExtractor.SharedStructureExtractorPlugin;
import org.ntlab.sharedStructureExtractor.DeltaMarkerManager;
import org.ntlab.sharedStructureExtractor.JavaEditorOperator;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class CallTreeView extends ViewPart {
	private TreeViewer viewer;
	private CallTreeModels callTreeModels = new CallTreeModels();
	public static final String ID = "org.ntlab.deltaExtractor.callTreeView";
	
	public CallTreeView() {
	}

	@Override
	public void createPartControl(Composite parent) {
		viewer = new TreeViewer(parent);
		viewer.setContentProvider(new TreeNodeContentProvider());
		viewer.setLabelProvider(new CallTreeLabelProvider());
		
		// Add a listener that is called when the selection is changed and open the source file that contains the selected method.
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (!(element instanceof TreeNode)) return;
				Object value = ((TreeNode)element).getValue();
				if (!(value instanceof CallTreeModel)) return;
				CallTreeModel callTreeModel = (CallTreeModel)value;
				MethodExecution methodExecution = callTreeModel.getMethodExecution();				
				TracePoint tp = methodExecution.getEntryPoint();

				if ((DebuggingController.getInstance().isRunning())) {
					highlight(methodExecution);
					DebuggingController controller = DebuggingController.getInstance();
					controller.jumpToTheTracePoint(tp, false);
					CallStackViewRelatedDelta callStackView = (CallStackViewRelatedDelta)SharedStructureExtractorPlugin.getActiveView(CallStackViewRelatedDelta.ID);
					VariableViewRelatedDelta variableView = ((VariableViewRelatedDelta)SharedStructureExtractorPlugin.getActiveView(VariableViewRelatedDelta.ID));
					DeltaMarkerView deltaMarkerView = (DeltaMarkerView)SharedStructureExtractorPlugin.getActiveView(DeltaMarkerView.ID);
					DeltaMarkerManager deltaMarkerManager = deltaMarkerView.getDeltaMarkerManager();
					IMarker coodinatorMarker = deltaMarkerManager.getCoordinatorDeltaMarker();
					MethodExecution coordinatorME = DeltaMarkerManager.getMethodExecution(coodinatorMarker);
					if (coordinatorME != null) callStackView.highlight((MethodExecution)coordinatorME);						
					variableView.markAndExpandVariablesByDeltaMarkers(deltaMarkerManager.getMarkers());
				} else {
					int lineNo = tp.getStatement().getLineNo();
					JavaEditorOperator.openSrcFileOfMethodExecution(methodExecution, lineNo);
				}
			}
		});
		createActions();
		createToolBar();
		createMenuBar();
		SharedStructureExtractorPlugin.setActiveView(ID, this);
	}
	
	@Override
	public String getTitle() {
		return "CurrentExecutionPoint";
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
	
	private void createActions() {
		
	}
	
	private void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
	}
	
	private void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
	}

	public void update(DeltaMarkerManager deltaMarkerManager) {
		callTreeModels.update(deltaMarkerManager);
		viewer.setInput(callTreeModels.getCallTreeModels());
		viewer.expandAll();		
	}
	
	public void highlight(MethodExecution theMe) {
		List<CallTreeModel> callTreeModelList = callTreeModels.getCallTreeModelList();
		for (CallTreeModel callTreeModel : callTreeModelList) {
			MethodExecution me = callTreeModel.getMethodExecution();
			callTreeModel.setHighlighting(me.equals(theMe));
		}
		viewer.refresh();
	}
	
	public void refresh() {
		
	}
	
	public void reset() {
		callTreeModels.reset();
		viewer.setInput(callTreeModels.getCallTreeModelList());
		viewer.refresh();
	}
}
