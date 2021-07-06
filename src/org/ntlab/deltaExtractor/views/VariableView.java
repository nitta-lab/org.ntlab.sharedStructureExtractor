package org.ntlab.deltaExtractor.views;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.deltaExtractor.DeltaExtractorPlugin;
import org.ntlab.deltaExtractor.ExtendedTreeNode;
import org.ntlab.deltaExtractor.ExtendedTreeNodeContentProvider;
import org.ntlab.deltaExtractor.Variable;
import org.ntlab.deltaExtractor.VariableLabelProvider;
import org.ntlab.deltaExtractor.Variables;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class VariableView extends ViewPart {	
	protected TreeViewer viewer;
	protected Variable selectedVariable;
	protected Variables variables = Variables.getInstance();
	public static final String ID = "org.ntlab.deltaExtractor.variableView";

	public VariableView() {
	}

	@Override
	public void createPartControl(Composite parent) {
		viewer = new TreeViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
		Tree tree = viewer.getTree();
		tree.setHeaderVisible(true);
		tree.setLinesVisible(true);

		String[] treeColumnTexts = new String[]{"Name", "Value"};
		int[] treeColumnWidth = {200, 300};
		TreeColumn[] treeColumns = new TreeColumn[treeColumnTexts.length];
		for (int i = 0; i < treeColumns.length; i++) {
			treeColumns[i] = new TreeColumn(tree, SWT.NULL);
			treeColumns[i].setText(treeColumnTexts[i]);
			treeColumns[i].setWidth(treeColumnWidth[i]);
		}
		viewer.setContentProvider(new ExtendedTreeNodeContentProvider());
		viewer.setLabelProvider(new VariableLabelProvider());
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {				
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (element instanceof TreeNode) {
					Object value = ((TreeNode)element).getValue();
					if (value instanceof Variable) {
						selectedVariable = (Variable)value;
					}	
				}
			}
		});
		viewer.addTreeListener(new ITreeViewerListener() {
			@Override
			public void treeExpanded(TreeExpansionEvent event) {
				// This method is called when a node in the variable tree is expanded. The nodes that are within three levels under the expanded node are created.
				Object element = event.getElement();
				if (!(element instanceof ExtendedTreeNode)) return;
				ExtendedTreeNode expandedNode = (ExtendedTreeNode)element;
				Object value = expandedNode.getValue();
				if (!(value instanceof Variable)) return;
				List<ExtendedTreeNode> childNodes = expandedNode.getChildList();
				if (childNodes == null) return;
				for (ExtendedTreeNode childNode : childNodes) {
					List<ExtendedTreeNode> grandChildNodes = childNode.getChildList();
					if (grandChildNodes == null) continue;
					for (ExtendedTreeNode grandChildNode : grandChildNodes) {
						Variable grandChildVariable = (Variable)grandChildNode.getValue();
						grandChildVariable.createNextHierarchyState();
						List<Variable> list = grandChildVariable.getChildren();
						List<ExtendedTreeNode> nodes = new ArrayList<>();
						for (int i = 0; i < list.size(); i++) {
							nodes.add(i, new ExtendedTreeNode(list.get(i)));
						}
						grandChildNode.setChildList(nodes);
					}
				}
				viewer.refresh();
			}
			@Override
			public void treeCollapsed(TreeExpansionEvent event) {}
		});
		createActions();
		createToolBar();
		createMenuBar();
		createPopupMenu();
		DeltaExtractorPlugin.setActiveView(ID, this);
	}

	@Override
	public String getTitle() {
		return "Variables";
	}
	
	@Override
	public void setFocus() {
		DeltaExtractorPlugin.setActiveView(ID, this);
		viewer.getControl().setFocus();
	}
	
	@Override
	public void dispose() {
		DeltaExtractorPlugin.removeView(ID, this);
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
	
	public void reset() {
		variables.resetData();
		viewer.setInput(variables.getVariablesTreeNodesList());
		viewer.refresh();
	}	
		
	public void updateVariablesByTracePoints(TracePoint tp, TracePoint before) {
		updateVariablesByTracePoints(null, tp, before);
	}

	public void updateVariablesByTracePointsFromAToB(TracePoint from, TracePoint to) {
		updateVariablesByTracePoints(from, to, null);
	}
	
	public void updateVariablesByTracePoints(TracePoint from, TracePoint to, TracePoint before) {
		variables.updateAllObjectDataByTracePoint(from, to, before);
		viewer.setInput(variables.getVariablesTreeNodesList());
	}
	
	public void updateVariablesForDifferential(TracePoint from, TracePoint to) {
		variables.updateForDifferential(from, to);
		viewer.refresh();
	}	
}
