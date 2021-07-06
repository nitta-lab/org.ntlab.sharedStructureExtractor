package org.ntlab.deltaExtractor;

import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.ntlab.deltaExtractor.Variable.VariableType;

public class VariableLabelProvider extends LabelProvider implements ITableLabelProvider, ITableColorProvider {
	public static final String PSEUDO_VARIABLE = "PseudoVariable";
	public static final String THIS_VARIABLE = "ThisVariable";
	public static final String FIELD_VARIABLE = "FieldVariable";
	public static final String ARG_VARIABLE = "ArgVariable";
	private Image pseudoVariableImage = DeltaExtractorPlugin.getDefault().getImageRegistry().getDescriptor(PSEUDO_VARIABLE).createImage();
	private Image thisVariableImage = DeltaExtractorPlugin.getDefault().getImageRegistry().getDescriptor(THIS_VARIABLE).createImage();
	private Image fieldVariableImage = DeltaExtractorPlugin.getDefault().getImageRegistry().getDescriptor(FIELD_VARIABLE).createImage();
	private Image argVariableImage = DeltaExtractorPlugin.getDefault().getImageRegistry().getDescriptor(ARG_VARIABLE).createImage();

	@Override
	public String getColumnText(Object element, int columnIndex) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof String) {
				String name = (String)value;
				String constructorMsg = "Constructor";
				switch (columnIndex) {
				case 0: {
					if (name.contains(constructorMsg)) {
						return name.substring(0, name.indexOf(constructorMsg));
					}
					return name.substring(0, name.indexOf(":"));
				}
				case 1:
					String valueName = name.substring(name.indexOf(":") + 1);
					valueName = valueName.substring(valueName.lastIndexOf(" ") + 1);
					boolean isConstructor = name.contains(constructorMsg);
					return getReadableName(valueName, isConstructor);
				}
			}
			if (value instanceof Variable) {
				Variable variableData = (Variable)value;
				String variableName = variableData.getVariableName();
				switch (columnIndex) {
				case 0:
					if (variableName.contains("[")) {
						return variableName.substring(variableName.lastIndexOf("["));
					} else if (variableName.contains(".")) {
						return variableName.substring(variableName.lastIndexOf(".") + 1);	
					}
					return variableName;
				case 1:
					String simpleName;
					String id;
					if (variableData.getVariableType().isContainerSide()) {
						simpleName = variableData.getContainerClassName();
						id = variableData.getContainerId();
					} else {
						simpleName = variableData.getValueClassName();
						id = variableData.getValueId();
					}
					simpleName = simpleName.substring(simpleName.lastIndexOf(".") + 1);
					simpleName = simpleName.replace(";", "[]"); // for an array
					if (simpleName.equals(Variable.NULL_VALUE)) {
						return simpleName;
					} else {
						return simpleName + " (" + "id = " + id + ")";	
					}
				}
			}
		}
		return "Text for Test" + columnIndex;
	}
	
	@Override
	public Image getColumnImage(Object element, int columnIndex) {
		if (columnIndex == 0) {
			if (element instanceof TreeNode) {
				Object value = ((TreeNode)element).getValue();
				if (value instanceof String) {
					return pseudoVariableImage;
				} else if (value instanceof Variable) {
					Variable variable = (Variable)value;
					VariableType variableType = variable.getVariableType();				
					if (variableType == VariableType.THIS) {
						return thisVariableImage;
					} else if (variableType == VariableType.PARAMETER){
						return argVariableImage;
					} else if (variableType.isContainerSide()) {
						return thisVariableImage;
					} else {
						return fieldVariableImage;
					}				
				}
			}			
		}
		return null;
	}

	@Override
	public Color getForeground(Object element, int columnIndex) {
		return null;
	}

	@Override
	public Color getBackground(Object element, int columnIndex) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof Variable) {
				Variable variable = (Variable)value;
				Object markerId = variable.getAdditionalAttribute("markerId");
				if (!(markerId instanceof String)) return null;
				switch ((String)markerId) {
				case DeltaMarkerManager.SRC_SIDE_DELTA_MARKER:
					return DeltaMarkerLabelProvider.SETTER_SIDE_LABEL_COLOR;
				case DeltaMarkerManager.DST_SIDE_DELTA_MARKER:
					return DeltaMarkerLabelProvider.GETTER_SIDE_LABEL_COLOR;
				case DeltaMarkerManager.COORDINATOR_DELTA_MARKER:
					return DeltaMarkerLabelProvider.COORDINATOR_LABEL_COLOR;
				}
			}
		}
		return null;
	}
	
	private String getReadableName(String name, boolean isConstrutor) {
		if (!(name.contains("("))) {
			String[] splits = name.split("\\.");
			if (splits.length < 2) return name;
			return splits[splits.length - 2] + "." + splits[splits.length - 1];
		}
		StringBuilder sb = new StringBuilder();	
		String receiverTypeAndMethodName = name.substring(0, name.indexOf("("));
		String[] receiverTypeAndMethodNameSplits = receiverTypeAndMethodName.split("\\."); 
		if (receiverTypeAndMethodNameSplits.length < 2) {
			sb.append(receiverTypeAndMethodName);
		} else {
			if (!isConstrutor) {
				sb.append(receiverTypeAndMethodNameSplits[receiverTypeAndMethodNameSplits.length - 2]);
				sb.append(".");
			}
			sb.append(receiverTypeAndMethodNameSplits[receiverTypeAndMethodNameSplits.length - 1]);
		}
		sb.append("(");
		String argsName = name.substring(name.indexOf("(") + 1, name.lastIndexOf(")"));
		String delimiter = "";
		for (String argName : argsName.split(",")) {
			String[] argNameSplits = argName.split("\\.");
			if (argNameSplits.length < 1) {
				sb.append(delimiter + argName);
			} else {
				sb.append(delimiter + argNameSplits[argNameSplits.length - 1]);
			}
			delimiter = ",";
		}
		sb.append(")");
		return sb.toString();		
	}
}
