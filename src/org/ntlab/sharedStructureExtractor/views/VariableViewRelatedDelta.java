package org.ntlab.sharedStructureExtractor.views;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.ntlab.sharedStructureExtractor.DebuggingController;
import org.ntlab.sharedStructureExtractor.SharedStructureExtractorPlugin;
import org.ntlab.sharedStructureExtractor.DeltaMarkerManager;
import org.ntlab.sharedStructureExtractor.Variable;
import org.ntlab.sharedStructureExtractor.VariableUpdatePointFinder;
import org.ntlab.sharedStructureExtractor.Variable.VariableType;
import org.ntlab.sharedStructureExtractor.analyzerProvider.Alias;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class VariableViewRelatedDelta extends VariableView {
	protected IAction jumpAction;
	private IAction deltaActionForContainerToComponent;
	private IAction deltaActionForThisToAnother;
	public static final String ID = "org.ntlab.deltaExtractor.variableViewRelatedDelta";
	
	public static long startTime = 0L;

	public VariableViewRelatedDelta() {
	}

	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		SharedStructureExtractorPlugin.setActiveView(ID, this);
	}

	@Override
	public void setFocus() {
		SharedStructureExtractorPlugin.setActiveView(ID, this);
		viewer.getControl().setFocus();
	}
	
	@Override
	public void dispose() {
		super.dispose();
		SharedStructureExtractorPlugin.removeView(ID, this);
	}

	@Override
	protected void createActions() {
		super.createActions();
		jumpAction = new Action() {
			public void run() {
				TracePoint before = DebuggingController.getInstance().getCurrentTp();
				TracePoint jumpPoint = findJumpPoint(selectedVariable, before);
				if (jumpPoint == null) return;
				DebuggingController controller = DebuggingController.getInstance();
				controller.jumpToTheTracePoint(jumpPoint, false);
			}
		};

		deltaActionForContainerToComponent = new Action() {
			@Override
			public void run() {
//				startTime = System.nanoTime();
				DeltaMarkerView newDeltaMarkerView = (DeltaMarkerView)SharedStructureExtractorPlugin.createNewView(DeltaMarkerView.ID, IWorkbenchPage.VIEW_ACTIVATE);
				newDeltaMarkerView.extractDeltaForContainerToComponent(selectedVariable);
			}
		};

		deltaActionForThisToAnother = new Action() {
			@Override
			public void run() {
//				startTime = System.nanoTime();
				TracePoint before = selectedVariable.getBeforeTracePoint();
				String thisId = before.getMethodExecution().getThisObjId();
				String thisClassName = before.getMethodExecution().getThisClassName();
				String anotherId;
				String anotherClassName;
				if (selectedVariable.getVariableType().isContainerSide()) {
					anotherId = selectedVariable.getContainerId();
					anotherClassName = selectedVariable.getContainerClassName();
				} else {
					anotherId = selectedVariable.getValueId();
					anotherClassName = selectedVariable.getValueClassName();
				}
				DeltaMarkerView newDeltaMarkerView = (DeltaMarkerView)SharedStructureExtractorPlugin.createNewView(DeltaMarkerView.ID, IWorkbenchPage.VIEW_ACTIVATE);
				newDeltaMarkerView.extractDeltaForThisToAnother(thisId, thisClassName, anotherId, anotherClassName, before);
			}
		};
	}
	
	private TracePoint findJumpPoint(Variable variable, TracePoint before) {
		VariableType variableType = selectedVariable.getVariableType();
		if (variableType.equals(VariableType.USE_VALUE)) {
			String containerId = selectedVariable.getContainerId();
			String fieldName = selectedVariable.getFullyQualifiedVariableName();
			if (fieldName.contains("[")) {
				// If it is an array element, then only the part of the array index is used. 
				fieldName = fieldName.substring(fieldName.lastIndexOf("[") + 1, fieldName.lastIndexOf("]"));
			}
			return VariableUpdatePointFinder.getInstance().getPoint(containerId, fieldName, before);
		} else if (variableType.equals(VariableType.USE_RETURN)) {
			return findJumpPointWithReturnValue(variable, before);
		}
		return null;
	}
	
	private TracePoint findJumpPointWithReturnValue(Variable variable, TracePoint before) {
		TracePoint tp = null;
		String receiverId = selectedVariable.getContainerId();
		String valueId = selectedVariable.getValueId();
		String receiverClassName = selectedVariable.getContainerClassName();
		VariableUpdatePointFinder finder = VariableUpdatePointFinder.getInstance();

		// note: if it is an iterator, then search the source collection and get the object id. 
		if (receiverClassName.contains("Iterator") || receiverClassName.contains("Itr")
				|| receiverClassName.contains("Collections$UnmodifiableCollection$1")) {
			tp = finder.getIteratorPoint(receiverId); // Get the execution point where the iterated is obtained.
			if (tp == null) return null;
			MethodInvocation mi = ((MethodInvocation)tp.getStatement()); 
			receiverId = mi.getCalledMethodExecution().getThisObjId(); // the source collection's id of the iterator.
			receiverClassName = mi.getCalledMethodExecution().getThisClassName(); // the source collection's name of the iterator.
		}
		
		// note: get the destination point where the returned object is added to the source collection.
		tp = finder.getDefinitionInvocationPoint(receiverId, valueId, before);
		
		// note: if the destination point could not be found, then search the source collection of the source collection. 
		if (tp == null && receiverClassName.startsWith("java.util.")) {
			String afterCollectionId = receiverId;
			while (true) {
				tp = finder.getTransferCollectionPoint(afterCollectionId, before);
				if (tp == null) break; // If the source does not exist any more.
				MethodInvocation mi = ((MethodInvocation)tp.getStatement()); 
				String fromCollectionId = mi.getCalledMethodExecution().getArguments().get(0).getId();
				tp = finder.getDefinitionInvocationPoint(fromCollectionId, valueId, before);
				if (tp != null) break; // If the destination point could be found for the source collection of the source collection. 
				afterCollectionId = fromCollectionId;
			}
		}
		return tp;
	}

	@Override
	protected void createPopupMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				// Called whenever right clicked.
				VariableType variableType = selectedVariable.getVariableType();
				if (variableType.equals(VariableType.USE_VALUE)) {
					manager.add(jumpAction);
					String msg = "Back to Value Stored Point";
					jumpAction.setText(msg);
					jumpAction.setToolTipText(msg);
				} else if (variableType.equals(VariableType.USE_RETURN)) {
					manager.add(jumpAction);
					if (updateDeltaActionForThisToAnotherTexts(selectedVariable)) {
						manager.add(deltaActionForThisToAnother);
					}
					String msg = "Back to Object Added Point";
					jumpAction.setText(msg);
					jumpAction.setToolTipText(msg);
				} else if (variableType.isDef()) {
					if (updateDeltaActionForContainerToComponentTexts(selectedVariable)) {
						manager.add(deltaActionForContainerToComponent);
					}
					if (updateDeltaActionForThisToAnotherTexts(selectedVariable)) {
						String text1 = deltaActionForThisToAnother.getText();
						String text2 = deltaActionForContainerToComponent.getText();
						if (!(text1.equals(text2))) {
							manager.add(deltaActionForThisToAnother);
						}
					}
				} else if (variableType.equals(VariableType.PARAMETER)) {
					if (updateDeltaActionForThisToAnotherTexts(selectedVariable)) {
						manager.add(deltaActionForThisToAnother);
					}
				}
				manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	private boolean updateDeltaActionForContainerToComponentTexts(Variable variable) {
		String valueId = selectedVariable.getValueId();
		String valueClassName = selectedVariable.getValueClassName();
		valueClassName = valueClassName.substring(valueClassName.lastIndexOf(".") + 1);
		if (!(valueId.isEmpty()) && !(valueClassName.isEmpty())) {
			String containerId = selectedVariable.getContainerId();
			String containerClassName = selectedVariable.getContainerClassName();
			if (containerId != null  && containerClassName != null) {
				containerClassName = containerClassName.substring(containerClassName.lastIndexOf(".") + 1);
				String msg = "Extract Delta to Relate";
				String textForContainerToComponent = String.format("%s [ %s (id = %s) -> %s (id = %s) ]", msg, containerClassName, containerId, valueClassName, valueId);
				deltaActionForContainerToComponent.setText(textForContainerToComponent);
				deltaActionForContainerToComponent.setToolTipText(textForContainerToComponent);
				return true;
			}
		}
		deltaActionForContainerToComponent.setText("");
		deltaActionForContainerToComponent.setToolTipText("");
		return false;
	}

	private boolean updateDeltaActionForThisToAnotherTexts(Variable variable) {
		VariableType variableType = variable.getVariableType();
		String anotherId;
		String anotherClassName;
		if (variableType.isContainerSide()) {
			anotherId = selectedVariable.getContainerId();
			anotherClassName = selectedVariable.getContainerClassName();
		} else {
			anotherId = selectedVariable.getValueId();
			anotherClassName = selectedVariable.getValueClassName();
		}
		anotherClassName = anotherClassName.substring(anotherClassName.lastIndexOf(".") + 1);		
		TracePoint before = selectedVariable.getBeforeTracePoint();
		String thisId = before.getMethodExecution().getThisObjId();
		String thisClassName = before.getMethodExecution().getThisClassName();
		if (thisId != null && thisClassName != null) {			
			thisClassName = thisClassName.substring(thisClassName.lastIndexOf(".") + 1);
			String msg = "Extract Delta to Relate";
			String textForThisToAnother = String.format("%s [ %s (id = %s) -> %s (id = %s) ]", msg, thisClassName, thisId, anotherClassName, anotherId);
			deltaActionForThisToAnother.setText(textForThisToAnother);
			deltaActionForThisToAnother.setToolTipText(textForThisToAnother);
			return true;
		} else {
			deltaActionForThisToAnother.setText("");
			deltaActionForThisToAnother.setToolTipText("");
			return false;
		}
	}

	public void markAndExpandVariablesByDeltaMarkers(Map<String, List<IMarker>> markers) {
		List<IMarker> srcSideDeltaMarkers = markers.get(DeltaMarkerManager.SRC_SIDE_DELTA_MARKER);
		List<IMarker> dstSideDeltaMarkers = markers.get(DeltaMarkerManager.DST_SIDE_DELTA_MARKER);
		List<IMarker> coordinatorMarker = markers.get(DeltaMarkerManager.COORDINATOR_DELTA_MARKER);
		if (srcSideDeltaMarkers != null) {
			markVariables(DeltaMarkerManager.SRC_SIDE_DELTA_MARKER, srcSideDeltaMarkers);	
		}
		if (dstSideDeltaMarkers != null) {
			markVariables(DeltaMarkerManager.DST_SIDE_DELTA_MARKER, dstSideDeltaMarkers);	
		}
		if (coordinatorMarker != null) {
			markVariables(DeltaMarkerManager.COORDINATOR_DELTA_MARKER, coordinatorMarker);	
		}
		viewer.refresh();
		expandAllMarkedNodes();
	}
	
	private void markVariables(String markerId, List<IMarker> markerList) {
		Set<String> idSet = new HashSet<>();
		Map<String, Object> additionalAttributesForVariables = new HashMap<>();
		additionalAttributesForVariables.put("markerId", markerId);
		for (IMarker marker : markerList) {
			try {
				Object data = marker.getAttribute(DeltaMarkerManager.DELTA_MARKER_ATR_DATA);
				if (data instanceof Alias) {
					idSet.add(((Alias)data).getObjectId());
				} else if (data instanceof MethodExecution) {
					idSet.add(((MethodExecution)data).getThisObjId());
				}
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		variables.addAdditionalAttributes(idSet, additionalAttributesForVariables);
	}
	
	private void expandAllMarkedNodes() {
		Set<TreeNode> expandedNodes = new HashSet<>();
		for (TreeItem item : viewer.getTree().getItems()) {
			Object obj = item.getData();
			if (!(obj instanceof TreeNode)) continue;
			collectNodes((TreeNode)obj, expandedNodes);
		}
		viewer.setExpandedElements(expandedNodes.toArray(new Object[expandedNodes.size()]));
	}
	
	private void collectNodes(TreeNode node, final Set<TreeNode> expandedNodes) {
		Object value = node.getValue();
		if (!(value instanceof Variable)) return;
		Variable variable = (Variable)value;
		if (variable.getAdditionalAttribute("markerId") != null) {
			TreeNode parent = node.getParent();
			if (parent != null) {
				expandedNodes.add(parent);
			}
		}
		TreeNode[] children = node.getChildren();
		if (children == null) return;
		for (TreeNode child : children) {
			collectNodes(child, expandedNodes);
		}
	}
	
	public void removeDeltaMarkers(Map<String, List<IMarker>> markers) {
		List<IMarker> srcSideDeltaMarkers = markers.get(DeltaMarkerManager.SRC_SIDE_DELTA_MARKER);
		List<IMarker> dstSideDeltaMarkers = markers.get(DeltaMarkerManager.DST_SIDE_DELTA_MARKER);
		List<IMarker> coordinatorMarker = markers.get(DeltaMarkerManager.COORDINATOR_DELTA_MARKER);
		if (srcSideDeltaMarkers != null) {
			markVariables("", srcSideDeltaMarkers);	
		}
		if (dstSideDeltaMarkers != null) {
			markVariables("", dstSideDeltaMarkers);	
		}
		if (coordinatorMarker != null) {
			markVariables("", coordinatorMarker);	
		}
		viewer.refresh();
	}
}
