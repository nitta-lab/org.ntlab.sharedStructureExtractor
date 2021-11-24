package org.ntlab.sharedStructureExtractor;

import java.util.List;

import org.eclipse.jface.viewers.TreeNodeContentProvider;

public class ExtendedTreeNodeContentProvider extends TreeNodeContentProvider {
	@Override
	public Object[] getElements(final Object inputElement) {
		if (inputElement instanceof List<?>) {
			List<?> list = (List<?>)inputElement;
			ExtendedTreeNode[] nodes = list.toArray(new ExtendedTreeNode[list.size()]);
			return super.getElements(nodes);
		}
		return new Object[0];
	}
}
