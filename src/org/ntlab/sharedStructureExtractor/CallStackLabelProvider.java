package org.ntlab.sharedStructureExtractor;

import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;

public class CallStackLabelProvider extends LabelProvider implements IColorProvider {
	private Image threadSuspendImage = DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_THREAD_SUSPENDED);
	private Image callStackModelImage = DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_STACKFRAME);

	@Override
	public String getText(Object element) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof String) {
				String threadId = (String)value;
				String msg = "Thread ID: ";
				return msg + threadId;
			}
			if (value instanceof CallStackModel) {
				CallStackModel callStackModel = (CallStackModel)value;
				StringBuilder text = new StringBuilder();
				text.append(callStackModel.getCallStackSignature());
				text.append(" line: ");
				text.append(callStackModel.getCallLineNo());
				return text.toString();
			}
		}
		return "";
	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof String) {
				return threadSuspendImage;
			} else if (value instanceof CallStackModel) {
				return callStackModelImage;
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
			if (value instanceof CallStackModel) {
				CallStackModel callStackModel = (CallStackModel)value;
				if (callStackModel.isHighlighting()) {
					return DeltaMarkerLabelProvider.COORDINATOR_LABEL_COLOR;
				}
			}
		}
		return null;
	}
}
