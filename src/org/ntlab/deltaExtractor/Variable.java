package org.ntlab.deltaExtractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class Variable {
	private String variableName;
	private VariableType variableType;
	private String fullyQualifiedVariableName;
	private String valueClassName;
	private String valueId;
	private Variable parent;
	private List<Variable> children = new ArrayList<>();
	private String containerClassName;
	private String containerId;
	private TracePoint lastUpdatePoint;
	private TracePoint before;
	private DeepHierarchy deepHierarchy;
	private boolean alreadyCreatedChildHierarchy = false;
	private boolean alreadyCreatedGrandChildHierarchy = false;
	private Map<String, Object> additionalAttributes = new HashMap<>();
	public static final String NULL_VALUE = "null";
	public static final String RETURN_VARIABLE_NAME = "return";
	public static final String ARG_VARIABLE_NAME = "arg";
	public static final String RECEIVER_VARIABLE_NAME = "receiver";
	public static final String VALUE_VARIABLE_NAME = "referred";
	public static final String CONTAINER_VARIABLE_NAME = "referring";

	public enum VariableType {
		USE_VALUE, USE_CONTAINER, USE_RECEIVER, USE_RETURN,
		DEF_VALUE, DEF_CONTAINER, DEF_RECEIVER, DEF_ARG, 
		THIS, PARAMETER;
		public boolean isContainerSide() {
			return this.equals(USE_CONTAINER) || this.equals(DEF_CONTAINER) 
					|| this.equals(USE_RECEIVER) || this.equals(DEF_RECEIVER);
		}
		public boolean isDef() {
			return this.equals(DEF_CONTAINER) || this.equals(DEF_VALUE)
					|| this.equals(DEF_RECEIVER) || this.equals(DEF_ARG);
		}
		public boolean isUse() {
			return this.equals(USE_CONTAINER) || this.equals(USE_VALUE)
					|| this.equals(USE_RECEIVER) || this.equals(USE_RETURN);
		}
	}
	
	public Variable(String variableName, String containerClassName, String containerId,
			String valueClassName, String valueId, TracePoint before, VariableType variableType) {
		init(variableName, variableName, containerClassName, containerId, valueClassName, valueId, null, before, variableType);
	}
	
	public Variable(String variableName, String fullyQualifiedVariableName, String containerClassName, String containerId,
			String valueClassName, String valueId, TracePoint lastUpdatePoint, TracePoint before, VariableType variableType) {
		init(variableName, fullyQualifiedVariableName, containerClassName, containerId, valueClassName, valueId, lastUpdatePoint, before, variableType);
	}
	
	private void init(String variableName, String fullyQualifiedVariableName, String containerClassName, String containerId,
			String valueClassName, String valueId, TracePoint lastUpdatePoint, TracePoint before, VariableType variableType) {
		this.variableName = variableName;
		this.fullyQualifiedVariableName = fullyQualifiedVariableName;
		this.containerClassName = containerClassName;
		this.containerId = containerId;
		this.valueClassName = valueClassName;
		this.valueId = valueId;
		this.lastUpdatePoint = lastUpdatePoint;
		this.before = before;
		this.variableType = variableType;
		this.deepHierarchy = checkDeepHierarchy();
		this.alreadyCreatedChildHierarchy = false;
		this.alreadyCreatedGrandChildHierarchy = false;
		this.children.clear();
		this.additionalAttributes.clear();
	}
	
	public void update(String valueClassName, String valueId, TracePoint lastUpdatePoint) {
		init(variableName, fullyQualifiedVariableName, containerClassName, containerId, valueClassName, valueId, lastUpdatePoint, lastUpdatePoint, variableType);
	}
	
	public String getVariableName() {
		return variableName;
	}
	
	public VariableType getVariableType() {
		return variableType;
	}
	
	public String getFullyQualifiedVariableName() {
		return fullyQualifiedVariableName;
	}
	
	public String getContainerClassName() {
		return containerClassName;
	}
	
	public String getContainerId() {
		return containerId;
	}
	
	public String getValueClassName() {
		return valueClassName;
	}
	
	public String getValueId() {
		return valueId;
	}
	
	public TracePoint getLastUpdatePoint() {
		return lastUpdatePoint;
	}
	
	public TracePoint getBeforeTracePoint() {
		return before;
	}

	public void setBeforeTracePoint(TracePoint before) {
		this.before = before;
	}
	
	public Variable getParent() {
		return parent;
	}
	
	public List<Variable> getChildren() {
		return children;
	}
	
	private void addChild(Variable child) {
		children.add(child);
		child.parent = this;
	}
	
	@Override
	public String toString() {
		return variableName + ": " + valueClassName + "(" + "id = " + valueId + ")";
	}
	
	/**
	 * Determine which reference type class or array the value's type is.<br>
	 * (To recursively expand the value of each variable in the variable view.)
	 * 
	 * @return FIELD: class, ARRAY: array, NONE: other
	 */
	private DeepHierarchy checkDeepHierarchy() {
		// note: do nothing if there is no field's id or type, or its actual type is "---".
		String id = (variableType.isContainerSide()) ? containerId : valueId;
		String className = (variableType.isContainerSide()) ? containerClassName : valueClassName;
		if (id == null || id.isEmpty() || className == null || className.isEmpty()) {
			return DeepHierarchy.NONE;
		}		
		
		final String NULL_ACTUAL_TYPE = "---"; // The actual type when null is stored in the field.
		if (className.equals(NULL_ACTUAL_TYPE)) return DeepHierarchy.NONE;

		final String ARRAY_SIGNATURE_HEAD = "["; // The prefix of the signature of an array contains more than one '['.
		if (className.startsWith(ARRAY_SIGNATURE_HEAD)) {
			// note: if the name of the value's type starts with '[' (then invoke the value identification process for each array element.)
			return DeepHierarchy.ARRAY;
		} else {
			String[] primitives = {"byte", "short", "int", "long", "float", "double", "char", "boolean"};
			if (!Arrays.asList(primitives).contains(this.getValueClassName())) {
				// note: if the value's type is a class (then invoke the value identification process for each field.)
				return DeepHierarchy.FIELD;
			}
		}
		return DeepHierarchy.NONE;
	}

	public void createNextHierarchyState() {
		if (alreadyCreatedGrandChildHierarchy) return;
		getDeepHierarchyState();
		for (Variable child : children) {
			child.getDeepHierarchyState();
		}
		alreadyCreatedGrandChildHierarchy = true;
	}

	private void getDeepHierarchyState() {
		if (alreadyCreatedChildHierarchy) return;
		switch (this.deepHierarchy) {
		case FIELD:
			getFieldsState();
			Collections.sort(children, new Comparator<Variable>() {
				@Override
				public int compare(Variable arg0, Variable arg1) {
					// Sort the variables in the ascending order of their names.
					return arg0.getVariableName().compareTo(arg1.getVariableName());
				}
			});
			break;
		case ARRAY:
			getArrayState();
			Collections.sort(children, new Comparator<Variable>() {
				@Override
				public int compare(Variable arg0, Variable arg1) {
					// Sort the array elements in the ascending order of their indexes.
					String arg0Name = arg0.variableName;
					String arg1Name = arg1.variableName;
					int arg0Index = Integer.parseInt(arg0Name.substring(arg0Name.indexOf("[") + 1, arg0Name.lastIndexOf("]"))); 
					int arg1Index = Integer.parseInt(arg1Name.substring(arg0Name.indexOf("[") + 1, arg1Name.lastIndexOf("]")));
					return (arg0Index < arg1Index) ? -1 : 1;
				}
			});
			break;
		case NONE:
			break;
		}
		alreadyCreatedChildHierarchy = true;
	}

	private void getFieldsState() {
		// Display field's id and type.
		IType type = null;
		if (variableType.isContainerSide()) {
			type = JavaElementFinder.findIType(null, containerClassName);
		} else {
			type = JavaElementFinder.findIType(null, valueClassName);
		}
		if (type == null) return;
		getFieldsState(type);		
		getFieldStateForSuperClass(type); // Also display the members of the ancestor types. (The computational load is high.) 
	}
	
	/**
	 * Get the information of the member of the ancestor types. (The computational load is high.)
	 * @param type base type
	 */
	private void getFieldStateForSuperClass(IType type) {
		try {
			while (true) {
				String superClassName = type.getSuperclassName();
				if (superClassName == null) break;
				String fullyQualifiedSuperClassName = JavaElementFinder.resolveType(type, superClassName);
				if (fullyQualifiedSuperClassName == null) break;
				type = JavaElementFinder.findIType(null, fullyQualifiedSuperClassName);
				if (type == null) break;
				getFieldsState(type);
			}				
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
	}

	private void getFieldsState(IType type) {
		try {
			for (IField field : type.getFields()) {
				if (Flags.isStatic(field.getFlags())) continue;
				String fieldName = field.getDeclaringType().getElementName() + "." + field.getElementName(); // fully-qualified call name
				String fullyQualifiedFieldName = field.getDeclaringType().getFullyQualifiedName() + "." + field.getElementName(); // fully-qualified call name
				
				// Get the latest information about the field. (FieldUpdate)
				String nextContainerId = (variableType.isContainerSide()) ? containerId : valueId;
				String nextClassName = (variableType.isContainerSide()) ? containerClassName : valueClassName;
				TracePoint updateTracePoint = VariableUpdatePointFinder.getInstance().getPoint(nextContainerId, fullyQualifiedFieldName, before);
				if (updateTracePoint != null) {
					FieldUpdate fieldUpdate = (FieldUpdate)updateTracePoint.getStatement();
					// Get the field's id and type. (String)
					String nextValueId = (fieldUpdate != null) ? fieldUpdate.getValueObjId() : "---";
					String nextValueClassName  = (fieldUpdate != null) ? fieldUpdate.getValueClassName() : NULL_VALUE;
					Variable fieldData = new Variable(fieldName, fullyQualifiedFieldName, nextClassName, nextContainerId, nextValueClassName, nextValueId, updateTracePoint, before, VariableType.USE_VALUE);
					this.addChild(fieldData);
				} else {
					Variable fieldData = new Variable(fieldName, fullyQualifiedFieldName, valueClassName, valueId, NULL_VALUE, "---", updateTracePoint, before, VariableType.USE_VALUE);
					this.addChild(fieldData);
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
	}

	private void getArrayState() {
		for (int i = 0; i < 10; i++) {
			String index = String.valueOf(i);
			String nextContainerId = (variableType.isContainerSide()) ? containerId : valueId;
			String nextClassName = (variableType.isContainerSide()) ? containerClassName : valueClassName;
			TracePoint updateTracePoint = VariableUpdatePointFinder.getInstance().getPoint(nextContainerId, index, before);

			String arrayIndexName = "[" + i + "]";
			String fullyArrayIndexName = variableName + arrayIndexName;
			String nextValueId = NULL_VALUE;
			String nextValueClassName = "---";
			if (updateTracePoint != null) {
				ArrayUpdate arrayUpdate = (ArrayUpdate)updateTracePoint.getStatement();
				nextValueId = arrayUpdate.getValueObjectId();
				nextValueClassName = arrayUpdate.getValueClassName();
			}
			Variable arrayIndexData = new Variable(arrayIndexName, fullyArrayIndexName, nextClassName, nextContainerId, nextValueClassName, nextValueId, updateTracePoint, before, VariableType.USE_VALUE);
			this.addChild(arrayIndexData);
		}
	}
	
	public void addAdditionalAttribute(String key, Object value) {
		additionalAttributes.put(key, value);
	}
	
	public Object getAdditionalAttribute(String key) {
		return additionalAttributes.get(key);
	}

	private enum DeepHierarchy {
		NONE, FIELD, ARRAY;
	}
}