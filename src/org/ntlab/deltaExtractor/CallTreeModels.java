package org.ntlab.deltaExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.viewers.TreeNode;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;

public class CallTreeModels {
	private TreeNode[] roots;
	private List<CallTreeModel> callTreeModelList = new ArrayList<>();
	private Map<MethodExecution, CallTreeModel> callTreeModelsMemo = new HashMap<>();
	
	public TreeNode[] getCallTreeModels() {
		return roots;
	}
	
	public List<CallTreeModel> getCallTreeModelList() {
		return callTreeModelList;
	}
	
	public void reset() {
		roots = new TreeNode[1];
		callTreeModelList.clear();
		callTreeModelsMemo.clear();
	}
	
	public void update(DeltaMarkerManager deltaMarkerManager) {
		reset();
		IMarker coordinatorMarker = deltaMarkerManager.getCoordinatorDeltaMarker();
		if (coordinatorMarker != null) {
			MethodExecution coordinatorME = DeltaMarkerManager.getMethodExecution(coordinatorMarker);
			List<IMarker> markersOrderByAdding = deltaMarkerManager.getMarkersByOrder();
			for (IMarker marker : markersOrderByAdding) {
				MethodExecution me = DeltaMarkerManager.getMethodExecution(marker);
				CallTreeModel callTreeModel = new CallTreeModel(me);
				if (!(callTreeModelsMemo.containsKey(me))) {
					callTreeModelsMemo.put(me, callTreeModel);
					if (!(me.equals(coordinatorME))) {
						linkTreeToCoordinator(callTreeModel, coordinatorME);	
					}
				}			
			}
			createCallTreeModels(callTreeModelsMemo.get(coordinatorME));			
		}
	}
	
	private void linkTreeToCoordinator(CallTreeModel currentModel, final MethodExecution coordinatorME) {
		MethodExecution currentME = currentModel.getMethodExecution();
		MethodExecution parentME = currentME.getParent();
		if (parentME == null) return;
		CallTreeModel parentModel = callTreeModelsMemo.get(parentME);
		boolean isNewParentModel = (parentModel == null);
		if (isNewParentModel) {
			parentModel = new CallTreeModel(parentME);
			callTreeModelsMemo.put(parentME, parentModel);
		}
		currentModel.setParent(parentModel);
		parentModel.addChildren(currentModel);
		if (isNewParentModel && !(parentME.equals(coordinatorME))) {
			linkTreeToCoordinator(parentModel, coordinatorME);
		}
	}
	
	private void createCallTreeModels(CallTreeModel coordinator) {
		roots = new TreeNode[1];
		callTreeModelList.clear();
		createCallTreeModels(roots, 0, coordinator, null);
	}
	
	private void createCallTreeModels(TreeNode[] nodes, int index, CallTreeModel callTreeModel, TreeNode parent) {
		callTreeModelList.add(callTreeModel);
		nodes[index] = new TreeNode(callTreeModel);
		nodes[index].setParent(parent);
		List<CallTreeModel> children = callTreeModel.getChildren();
		TreeNode[] childNodes = new TreeNode[children.size()];
		nodes[index].setChildren(childNodes);
		for (int i = 0; i < childNodes.length; i++) {
			CallTreeModel child = children.get(i);
			createCallTreeModels(childNodes, i, child, nodes[index]);
		}
	}	
}
