package org.ntlab.sharedStructureExtractor.views;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.jface.viewers.TreeNodeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.sharedStructureExtractor.DebuggingController;
import org.ntlab.sharedStructureExtractor.SharedStructureExtractorPlugin;
import org.ntlab.sharedStructureExtractor.DeltaMarkerLabelProvider;
import org.ntlab.sharedStructureExtractor.DeltaMarkerManager;
import org.ntlab.sharedStructureExtractor.Variable;
import org.ntlab.sharedStructureExtractor.analyzerProvider.AbstractAnalyzer;
import org.ntlab.sharedStructureExtractor.analyzerProvider.Alias;
import org.ntlab.sharedStructureExtractor.analyzerProvider.DeltaExtractionAnalyzer;
import org.ntlab.sharedStructureExtractor.analyzerProvider.Alias.AliasType;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class DeltaMarkerView extends ViewPart {
	private TreeViewer viewer;
	private Shell shell;
	private IMarker selectionMarker;
	private boolean doNotUpdateCallTreeView;
	private DeltaMarkerManager deltaMarkerManager;
	public static String ID = "org.ntlab.deltaExtractor.deltaMarkerView";

	@Override
	public void createPartControl(Composite parent) {
		shell = parent.getShell();
		viewer = new TreeViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
		Tree tree = viewer.getTree();
		tree.setHeaderVisible(true);
		tree.setLinesVisible(true);

		// Create the columns of the table of delta markers.
		String[] tableColumnTexts = new String[]{"ExecutionPoint", "id", "Type", "Expression", "Source", "Line"};
		int[] tableColumnWidth = {120, 100, 120, 120, 100, 80};
		TreeColumn[] tableColumns = new TreeColumn[tableColumnTexts.length];
		for (int i = 0; i < tableColumns.length; i++) {
			tableColumns[i] = new TreeColumn(tree, SWT.NULL);
			tableColumns[i].setText(tableColumnTexts[i]);
			tableColumns[i].setWidth(tableColumnWidth[i]);
		}
		viewer.setContentProvider(new TreeNodeContentProvider());
		viewer.setLabelProvider(new DeltaMarkerLabelProvider());
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (!(element instanceof TreeNode)) return;
				Object value = ((TreeNode)element).getValue();
				if (!(value instanceof IMarker)) return;
				selectionMarker = (IMarker)value;
				updateOtherViewsByMarker(selectionMarker);
				doNotUpdateCallTreeView = true;
				viewer.getControl().setFocus();
			}
		});
		viewer.refresh();

		createActions();
		createToolBar();
		createMenuBar();
		createPopupMenu();
		SharedStructureExtractorPlugin.setActiveView(ID, this);
	}
	
	@Override
	public String getTitle() {
		return "Process to Relate";
	}
	
	private void createActions() {
		// TODO Auto-generated method stub
	}
	
	private void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
	}

	private void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
	}
	
	private void createPopupMenu() {
		// TODO Auto-generated method stub
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
		SharedStructureExtractorPlugin.setActiveView(ID, this);
		if (!doNotUpdateCallTreeView && deltaMarkerManager != null) {
			CallTreeView callTreeView = (CallTreeView)SharedStructureExtractorPlugin.getActiveView(CallTreeView.ID);
			callTreeView.update(deltaMarkerManager);
		}
		doNotUpdateCallTreeView = false;
		viewer.getControl().setFocus();
	}
	
	public DeltaMarkerManager getDeltaMarkerManager() {
		return deltaMarkerManager;
	}
	
	public TracePoint getCreationPoint() {
		IMarker creationPointMarker = deltaMarkerManager.getBottomDeltaMarker();
		return DeltaMarkerManager.getTracePoint(creationPointMarker);
	}
	
	public TracePoint getCoordinatorPoint() {
		IMarker coordinatorMarker = deltaMarkerManager.getCoordinatorDeltaMarker();
		return DeltaMarkerManager.getTracePoint(coordinatorMarker);
	}
	
	@Override
	public void dispose() {
		CallTreeView callTreeView = ((CallTreeView)SharedStructureExtractorPlugin.getActiveView(CallTreeView.ID));
		callTreeView.reset();
		VariableViewRelatedDelta variableView = ((VariableViewRelatedDelta)SharedStructureExtractorPlugin.getActiveView(VariableViewRelatedDelta.ID));
		variableView.removeDeltaMarkers(deltaMarkerManager.getMarkers());
		CallStackViewRelatedDelta callStackView = ((CallStackViewRelatedDelta)SharedStructureExtractorPlugin.getActiveView(CallStackViewRelatedDelta.ID));
		callStackView.removeHighlight();
		deltaMarkerManager.clearAllMarkers();
		SharedStructureExtractorPlugin.removeView(ID, this);
		super.dispose();
	}

	private void updateOtherViewsByMarker(IMarker marker) {
		DebuggingController controller = DebuggingController.getInstance();
		IMarker coordinator = deltaMarkerManager.getCoordinatorDeltaMarker();
		TracePoint coordinatorPoint = DeltaMarkerManager.getTracePoint(coordinator);
		if (marker != null) {
			try {
				Object obj = marker.getAttribute(DeltaMarkerManager.DELTA_MARKER_ATR_DATA);
				TracePoint jumpPoint;
				boolean isReturned = false;
				if (obj instanceof Alias) {
					Alias alias = (Alias)obj;
					jumpPoint = alias.getOccurrencePoint();
					Alias.AliasType type = alias.getAliasType();
					isReturned = type.equals(AliasType.METHOD_INVOCATION) || type.equals(AliasType.CONSTRACTOR_INVOCATION);
				} else if (obj instanceof TracePoint) {
					jumpPoint = (TracePoint)obj;
				} else {
					jumpPoint = coordinatorPoint;
				}
				controller.jumpToTheTracePoint(jumpPoint, isReturned);
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				IDE.openEditor(page, marker);
				CallStackView callStackView = (CallStackView)SharedStructureExtractorPlugin.getActiveView(CallStackView.ID);
				callStackView.highlight(coordinatorPoint.getMethodExecution());
				VariableViewRelatedDelta variableView = (VariableViewRelatedDelta)SharedStructureExtractorPlugin.getActiveView(VariableViewRelatedDelta.ID);
				variableView.markAndExpandVariablesByDeltaMarkers(deltaMarkerManager.getMarkers());
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
	}

	public void extractDeltaForContainerToComponent(Variable variable) {
		AbstractAnalyzer analyzer = SharedStructureExtractorPlugin.getAnalyzer();
		if (analyzer instanceof DeltaExtractionAnalyzer) {
			DeltaExtractionAnalyzer deltaAnalyzer = (DeltaExtractionAnalyzer)analyzer;
			if (deltaMarkerManager == null) deltaMarkerManager = new DeltaMarkerManager();
			deltaMarkerManager.setDeltaInfo(deltaAnalyzer.extractDeltaForContainerToComponent(variable));
			deltaMarkerManager.createMarkerAndOpenJavaFileForAll(); // Highlight the source code related to the extracted delta.
			updateAfterExtractingDelta();			
		}
	}

	public void extractDeltaForThisToAnother(String thisId, String thisClassName, String anotherId, String anotherClassName, TracePoint before) {
		AbstractAnalyzer analyzer = SharedStructureExtractorPlugin.getAnalyzer();
		if (analyzer instanceof DeltaExtractionAnalyzer) {
			DeltaExtractionAnalyzer deltaAnalyzer = (DeltaExtractionAnalyzer)analyzer;
			if (deltaMarkerManager == null) deltaMarkerManager = new DeltaMarkerManager();
			deltaMarkerManager.setDeltaInfo(deltaAnalyzer.extractDeltaForThisToAnother(thisId, thisClassName, anotherId, anotherClassName, before));
			deltaMarkerManager.createMarkerAndOpenJavaFileForAll(); // Highlight the source code related to the extracted delta.
			updateAfterExtractingDelta();	
		}
	}
	private void updateAfterExtractingDelta() {
		viewer.setInput(deltaMarkerManager.getMarkerTreeNodes());
		viewer.expandAll();
		viewer.refresh();
		VariableViewRelatedDelta.startTime = 0L;
		TracePoint coordinatorPoint = getCoordinatorPoint();
		TracePoint creationPoint = getCreationPoint();
		MethodExecution coordinatorME = coordinatorPoint.getMethodExecution();
		MethodExecution bottomME = creationPoint.getMethodExecution();			
		DebuggingController controller = DebuggingController.getInstance();
		controller.jumpToTheTracePoint(creationPoint, false);
		VariableViewRelatedDelta variableView = (VariableViewRelatedDelta)(SharedStructureExtractorPlugin.getActiveView(VariableViewRelatedDelta.ID));
		variableView.markAndExpandVariablesByDeltaMarkers(deltaMarkerManager.getMarkers());
		CallStackView callStackView = (CallStackView)SharedStructureExtractorPlugin.getActiveView(CallStackView.ID);
		callStackView.highlight(coordinatorME);
		CallTreeView callTreeView = (CallTreeView)SharedStructureExtractorPlugin.getActiveView(CallTreeView.ID);
		if (deltaMarkerManager != null) callTreeView.update(deltaMarkerManager);
		callTreeView.highlight(bottomME);
		TracePointsRegisterView tracePointsView = (TracePointsRegisterView)SharedStructureExtractorPlugin.getActiveView(TracePointsRegisterView.ID);
		tracePointsView.addTracePoint(creationPoint);		
	}	
}
