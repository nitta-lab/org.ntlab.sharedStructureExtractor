package org.ntlab.deltaExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.ntlab.deltaExtractor.Variable.VariableType;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class Variables {
	private static final Variables theInstance = new Variables();
	private List<Variable> roots = new ArrayList<>();
	private List<ExtendedTreeNode> rootTreeNodes = new ArrayList<>();
	public static final String VARIABLE_TYPE_KEY = "variableType";
	
	public static Variables getInstance() {
		return theInstance;
	}

	public List<ExtendedTreeNode> getVariablesTreeNodesList() {
		return rootTreeNodes;
	}

	private void createVariablesTreeNodeList(ExtendedTreeNode parentNode, List<ExtendedTreeNode> addingNodes, int index, Variable addingVariableData) {
		ExtendedTreeNode newNode = new ExtendedTreeNode(addingVariableData);
		newNode.setParent(parentNode);
		addingNodes.add(index, newNode);
		List<ExtendedTreeNode> childNodes = new ArrayList<>();
		addingNodes.get(index).setChildList(childNodes);
		for (int i = 0; i < addingVariableData.getChildren().size(); i++) {
			Variable child = addingVariableData.getChildren().get(i);
			createVariablesTreeNodeList(newNode, childNodes, i, child);
		}
	}
	
	public void updateAllObjectDataByMethodExecution(MethodExecution methodExecution) {
		if (methodExecution == null) return;			
		List<Statement> statements = methodExecution.getStatements();
		int lastOrder = statements.size() - 1;
		TracePoint tp  = methodExecution.getTracePoint(lastOrder);
		updateAllObjectData(null, tp, null);
	}

	public void updateAllObjectDataByTracePoint(TracePoint from, TracePoint to, TracePoint before) {
		updateAllObjectData(from, to, before);
	}

	private void updateAllObjectData(TracePoint from, TracePoint to, TracePoint before) {
		resetData();
		MethodExecution me = to.getMethodExecution();
		updateRootThisState(me, to, before);
		updateArgsState(me, to, before);		
		for (int i = 0; i < roots.size(); i++) {
			Variable rootVariableData = roots.get(i);
			createVariablesTreeNodeList(null, rootTreeNodes, i, rootVariableData);
		}
		createPseudoVariables(from, to);	
	}
	
	private void updateRootThisState(MethodExecution methodExecution, TracePoint tp, TracePoint before) {
		String thisObjId = methodExecution.getThisObjId();
		String thisClassName = methodExecution.getThisClassName();
		if (before == null) before = tp;
		Variable variable = new Variable("this", null, null, thisClassName, thisObjId, before, VariableType.THIS);
		roots.add(variable);
		variable.createNextHierarchyState();
	}

	private void updateArgsState(MethodExecution methodExecution, TracePoint tp, TracePoint before) {
		// Get the arguments of the methodExecution.
		List<ObjectReference> args = methodExecution.getArguments();
		if (before == null) before = tp;
		if (args.size() > 0) {
			IType type = JavaElementFinder.findIType(methodExecution);
			String methodSignature = methodExecution.getSignature();
			IMethod method = JavaElementFinder.findIMethod(type, methodSignature);			
			String[] argNames = getParameterNames(method); // Get the names of formal parameters from a given IMethod object.
			for (int i = 0; i < args.size(); i++) {
				String argName = (argNames.length == args.size()) ? argNames[i] : "arg" + i; // check the number of arguments.
				ObjectReference arg = args.get(i);
				String argId = arg.getId();
				String argType = arg.getActualType();
				Variable argData = new Variable(argName, null, null, argType, argId, before, VariableType.PARAMETER);
				argData.createNextHierarchyState();
				roots.add(argData);
			}
		}
	}
	
	private String[] getParameterNames(IMethod method) {
		String[] argNames = new String[0];
		if (method != null) {
			try {
				argNames = method.getParameterNames();
			} catch (JavaModelException e) {
				e.printStackTrace();
			}
		}
		return argNames;
	}
	
	public void addAdditionalAttributes(final Set<String> idSet, final Map<String, Object> additionalAttributes) {
		for (Variable root : roots) {
			addAdditionalAttributes(root, idSet, additionalAttributes);
		}
	}
	
	private void addAdditionalAttributes(Variable variable, final Set<String> idSet, final Map<String, Object> additionalAttributes) {
		if (variable == null) return;
		VariableType variableType = variable.getVariableType();
		String id = variableType.isContainerSide() ? variable.getContainerId() : variable.getValueId();
		if (id.equals("0")) return;
		if (idSet.contains(id)) {
			for (Map.Entry<String, Object> entry : additionalAttributes.entrySet()) {
				variable.addAdditionalAttribute(entry.getKey(), entry.getValue());	
			}			
		}
		for (Variable child : variable.getChildren()) {
			addAdditionalAttributes(child, idSet, additionalAttributes);
		}
	}
		
	public void updateForDifferential(TracePoint from, TracePoint to) {
		updateForDifferential(to);
		resetPseudoValues();
		createPseudoVariables(from, to);
	}
	
	private void updateForDifferential(TracePoint before) {
		for (Variable variable : roots) {
			updateForDifferental(variable, before);
		}
	}
	
	private void updateForDifferental(Variable variable, TracePoint before) {
		variable.setBeforeTracePoint(before);
		String objectId = variable.getContainerId();
		String variableName = variable.getFullyQualifiedVariableName();
		if (variableName.contains("[")) {
			// note: if it is an array, then only the part of the array index is used. 
			String arrayIndexName = variable.getVariableName();
			variableName = arrayIndexName.substring(arrayIndexName.indexOf("[") + 1, arrayIndexName.lastIndexOf("]"));
		}
		
		TracePoint lastUpdatePoint = variable.getLastUpdatePoint();
		TracePoint updateTracePoint = VariableUpdatePointFinder.getInstance().getPoint(objectId, variableName, before);
		if (updateTracePoint != null) {
			Statement statement = updateTracePoint.getStatement();
			if (lastUpdatePoint == null || !(statement.equals(lastUpdatePoint.getStatement()))) {
				if (statement instanceof FieldUpdate) {
					FieldUpdate fu = (FieldUpdate)statement;
					updateForDifferentialField(variable, fu.getValueClassName(), fu.getValueObjId(), updateTracePoint);
				} else if (statement instanceof ArrayUpdate) {
					ArrayUpdate au = (ArrayUpdate)statement;
					updateForDifferentialField(variable, au.getValueClassName(), au.getValueObjectId(), updateTracePoint);
				}
			}
		}
		for (Variable child : variable.getChildren()) {
			updateForDifferental(child, before);
		}
	}
	
	private void updateForDifferentialField(Variable variable, String valueClassName, String valueId, TracePoint lastUpdatePoint) {		
		variable.update(valueClassName, valueId, lastUpdatePoint);
		variable.createNextHierarchyState();
		ExtendedTreeNode node = getTreeNodeFor(variable, rootTreeNodes);
		List<ExtendedTreeNode> childList = node.getChildList();
		childList.clear();
		for (int i = 0; i < variable.getChildren().size(); i++) {
			Variable childVariable = variable.getChildren().get(i);
			createVariablesTreeNodeList(node, childList, i, childVariable);	
		}
	}
	
	private void createPseudoVariables(TracePoint from, TracePoint to) {
		List<Variable> pseudoVariablesOfUseSide = new ArrayList<>();
		List<Variable> pseudoVariablesDefSide = new ArrayList<>();
		String parentNodeNameOfUseSide = null;
		String parentNodeNameOfDefSide = null;
		if (from != null) {
			// Just after an access or return.
			Statement fromStatement = from.getStatement();
			if (fromStatement instanceof FieldAccess) {
				FieldAccess fa = (FieldAccess)fromStatement;
				String containerClassName = fa.getContainerClassName();
				String containerObjId = fa.getContainerObjId();
				String valueClassName = fa.getValueClassName();
				String valueObjId = fa.getValueObjId();
				Variable container = new Variable(Variable.CONTAINER_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, from, VariableType.USE_CONTAINER);
				Variable value = new Variable(Variable.VALUE_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, from, VariableType.USE_VALUE);
				pseudoVariablesOfUseSide.add(container);
				pseudoVariablesOfUseSide.add(value);
				parentNodeNameOfUseSide = "after field access of:";
				parentNodeNameOfUseSide += fa.getFieldName();
			} else if (fromStatement instanceof ArrayAccess) {
				ArrayAccess aa = (ArrayAccess)fromStatement;
				String arrayClassName = aa.getArrayClassName();
				String arrayObjId = aa.getArrayObjectId();
				String valueClassName = aa.getValueClassName();
				String valueObjId = aa.getValueObjectId();
				Variable array = new Variable(Variable.CONTAINER_VARIABLE_NAME, arrayClassName, arrayObjId, valueClassName, valueObjId, from, VariableType.USE_CONTAINER);
				Variable value = new Variable(Variable.VALUE_VARIABLE_NAME, arrayClassName, arrayObjId, valueClassName, valueObjId, from, VariableType.USE_VALUE);
				pseudoVariablesOfUseSide.add(array);
				pseudoVariablesOfUseSide.add(value);
				parentNodeNameOfUseSide = "after array access of:";
				parentNodeNameOfUseSide += aa.getArrayClassName().replace(";", "") + "[" + aa.getIndex() + "]";
			} else if (fromStatement instanceof MethodInvocation) {
				MethodInvocation mi = (MethodInvocation)fromStatement;
				MethodExecution calledME = mi.getCalledMethodExecution();
				ObjectReference returnValue = calledME.getReturnValue();
				if (returnValue != null) {
					String containerClassName = calledME.getThisClassName();
					String containerObjId = calledME.getThisObjId();
					String valueClassName = returnValue.getActualType();
					String valueObjId = returnValue.getId();
					Variable receiver = new Variable(Variable.RECEIVER_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, from, VariableType.USE_RECEIVER);
					Variable returned = new Variable(Variable.RETURN_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, from, VariableType.USE_RETURN);
					pseudoVariablesOfUseSide.add(receiver);
					pseudoVariablesOfUseSide.add(returned);
					if (calledME.isConstructor()) {
						parentNodeNameOfUseSide = "after invocation of Constructor:";
						parentNodeNameOfUseSide += calledME.getSignature();
					} else {
						parentNodeNameOfUseSide = "after invocation of:";
						parentNodeNameOfUseSide += calledME.getSignature();
					}
				}
			}			
		}

		if (to != null) {
			// Just before an update or invocation. 
			Statement toStatement = to.getStatement();
			if (toStatement instanceof FieldUpdate) {
				FieldUpdate fu = (FieldUpdate)toStatement;
				String containerClassName = fu.getContainerClassName();
				String containerObjId = fu.getContainerObjId();
				String valueClassName = fu.getValueClassName();
				String valueObjId = fu.getValueObjId();
				Variable container = new Variable(Variable.CONTAINER_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, to, VariableType.DEF_CONTAINER);
				Variable value = new Variable(Variable.VALUE_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, to, VariableType.DEF_VALUE);
				pseudoVariablesDefSide.add(container);
				pseudoVariablesDefSide.add(value);
				parentNodeNameOfDefSide = "before field update of:";
				parentNodeNameOfDefSide += fu.getFieldName();
			} else if (toStatement instanceof ArrayUpdate) {
				ArrayUpdate au = (ArrayUpdate)toStatement;
				String arrayClassName = au.getArrayClassName();
				String arrayObjId = au.getArrayObjectId();
				String valueClassName = au.getValueClassName();
				String valueObjId = au.getValueObjectId();
				Variable array = new Variable(Variable.CONTAINER_VARIABLE_NAME, arrayClassName, arrayObjId, valueClassName, valueObjId, to, VariableType.DEF_CONTAINER);
				Variable value = new Variable(Variable.VALUE_VARIABLE_NAME, arrayClassName, arrayObjId, valueClassName, valueObjId, to, VariableType.DEF_VALUE);
				pseudoVariablesDefSide.add(array);
				pseudoVariablesDefSide.add(value);
				parentNodeNameOfDefSide = "before array update of:";
				parentNodeNameOfDefSide += au.getArrayClassName().replace(";", "") + "[" + au.getIndex() + "]";
			} else if (toStatement instanceof MethodInvocation) {
				MethodInvocation mi = (MethodInvocation)toStatement;
				MethodExecution calledME = mi.getCalledMethodExecution();
				List<ObjectReference> args = calledME.getArguments();				
				String valueClassName = "";
				String valueObjId = "";
				if (args.size() > 0) {
					valueClassName = args.get(0).getActualType();
					valueObjId = args.get(0).getId();
				}
				String containerClassName = calledME.getThisClassName();
				String containerObjId = calledME.getThisObjId();
				Variable receiver = new Variable(Variable.RECEIVER_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, to, VariableType.DEF_RECEIVER);
				pseudoVariablesDefSide.add(receiver);
				for (ObjectReference obj : args) {
					valueClassName = obj.getActualType();
					valueObjId = obj.getId();
					Variable arg = new Variable(Variable.ARG_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, to, VariableType.DEF_ARG);
					pseudoVariablesDefSide.add(arg);
				}
				if (calledME.isConstructor()) {
					parentNodeNameOfDefSide = "before invocation of Constructor:";
					parentNodeNameOfDefSide += calledME.getSignature();
				} else {
					parentNodeNameOfDefSide = "before invocation of:";
					parentNodeNameOfDefSide += calledME.getSignature();
				}
			} 
		}
		if (parentNodeNameOfDefSide != null) {
			setPseudoVariableNodes(parentNodeNameOfDefSide, pseudoVariablesDefSide);
		}
		if (parentNodeNameOfUseSide != null) {
			setPseudoVariableNodes(parentNodeNameOfUseSide, pseudoVariablesOfUseSide);
		}
	}
	
	private void setPseudoVariableNodes(String parentNodeName, List<Variable> pseudoVariables) {
		ExtendedTreeNode parentNode = new ExtendedTreeNode(parentNodeName);
		rootTreeNodes.add(0, parentNode);
		ExtendedTreeNode[] children = new ExtendedTreeNode[pseudoVariables.size()];
		for (int i = 0; i < pseudoVariables.size(); i++) {
			Variable variable = pseudoVariables.get(i);
			variable.createNextHierarchyState();
			roots.add(0, variable);
			ExtendedTreeNode variableNode = new ExtendedTreeNode(variable);
			children[i] = variableNode;
			variableNode.setParent(parentNode);
			createChildNodesOfPseudoVariableNode(variableNode);				
		}
		parentNode.setChildren(children);		
	}
	
	private void createChildNodesOfPseudoVariableNode(ExtendedTreeNode variableNode) {
		List<ExtendedTreeNode> childList = new ArrayList<>();
		variableNode.setChildList(childList);
		Variable variable = (Variable)variableNode.getValue();
		for (int i = 0; i < variable.getChildren().size(); i++) {
			Variable childVariable = variable.getChildren().get(i);
			createVariablesTreeNodeList(variableNode, childList, i, childVariable);	
		}
	}
	
	public void resetData() {
		roots.clear();
		rootTreeNodes.clear();
	}
	
	private void resetPseudoValues() {
		for (int i = roots.size() - 1; i >= 0; i--) {
			Variable root = roots.get(i);
			String variableName = root.getVariableName();
			if (variableName.equals(Variable.CONTAINER_VARIABLE_NAME)
				|| variableName.equals(Variable.VALUE_VARIABLE_NAME)
				|| variableName.equals(Variable.RECEIVER_VARIABLE_NAME)
				|| variableName.equals(Variable.ARG_VARIABLE_NAME)
				|| variableName.equals(Variable.RETURN_VARIABLE_NAME)) {
				roots.remove(i);
			}
		}
		for (int i = rootTreeNodes.size() - 1; i >= 0; i--) {
			ExtendedTreeNode node = rootTreeNodes.get(i);
			if (node.getValue() instanceof String) {
				rootTreeNodes.remove(i);
			}
		}
	}
	
	private ExtendedTreeNode getTreeNodeFor(Variable variable, List<ExtendedTreeNode> nodes) {
		for (ExtendedTreeNode node : nodes) {
			if (node.getValue().equals(variable)) return node;
			ExtendedTreeNode deep = getTreeNodeFor(variable, node.getChildList());
			if (deep != null) return deep;
		}
		return null;
	}
}
