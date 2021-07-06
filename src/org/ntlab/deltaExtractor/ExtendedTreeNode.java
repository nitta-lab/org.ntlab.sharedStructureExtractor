package org.ntlab.deltaExtractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jface.viewers.TreeNode;

public class ExtendedTreeNode extends TreeNode {
	private List<ExtendedTreeNode> children = new ArrayList<>();
	
	public ExtendedTreeNode(Object value) {
		super(value);
	}

	@Override
	public ExtendedTreeNode[] getChildren() {
		if (children != null && children.size() == 0) {
			return null;
		}
		return children.toArray(new ExtendedTreeNode[children.size()]);
	}
	
	public List<ExtendedTreeNode> getChildList() {
		return children;
	}

	@Override
	public boolean hasChildren() {
		return children != null && children.size() > 0;
	}

	public void setChildren(final ExtendedTreeNode[] children) {
		this.children = new ArrayList<ExtendedTreeNode>(Arrays.asList(children));
	}
	
	public void setChildList(final List<ExtendedTreeNode> children) {
		this.children = children;
	}
}
