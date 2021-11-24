package org.ntlab.sharedStructureExtractor;

import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

public class CallTreeLabelProvider extends LabelProvider implements IColorProvider {
	private Image callTreeModelImage = DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_STACKFRAME);
	private Color currentColor = new Color(Display.getDefault(), 198, 219, 174);
	
	@Override
	public String getText(Object element) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof CallTreeModel) {
				CallTreeModel callTreeModel = (CallTreeModel)value;
				return callTreeModel.getCallTreeSignature();
			}
		}
		return "";
	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof CallTreeModel) {
				return callTreeModelImage;
			}
		}
		return null;
	}

	@Override
	public Color getForeground(Object element) {
		return null;
	}

	@Override
	public Color getBackground(Object element) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof CallTreeModel) {
				CallTreeModel callTreeModel = (CallTreeModel)value;
				if (callTreeModel.isHighlighting()) {
					return currentColor;
				}
			}
		}
		return null;
	}
}
