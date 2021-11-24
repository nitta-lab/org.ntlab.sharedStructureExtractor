package org.ntlab.sharedStructureExtractor;

import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

public class BreakPointLabelProvider  extends LabelProvider implements ITableLabelProvider {

	@Override
	public String getColumnText(Object element, int columnIndex) {
		if (element instanceof TraceBreakPoint) {
			TraceBreakPoint tbp = (TraceBreakPoint)element;
			switch (columnIndex) {
			case 0:
				return "";
			case 1:
				return String.valueOf(tbp.getLineNo());
			case 2:
				return tbp.getReadableSignature();
			}
		}
		return "Text for Test" + columnIndex;
	}
	
	@Override
	public Image getColumnImage(Object element, int columnIndex) {
		if (columnIndex == 0 && element instanceof TraceBreakPoint) {
			TraceBreakPoint tbp = (TraceBreakPoint)element;
			if (tbp.isAvailable()) {
				return DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_BREAKPOINT);
			} else {
				return DebugUITools.getImage(IDebugUIConstants.IMG_OBJS_BREAKPOINT_DISABLED);
			}
		}
		return null;
	}	
}
