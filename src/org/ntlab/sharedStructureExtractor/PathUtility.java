package org.ntlab.sharedStructureExtractor;

import java.net.URI;

import org.eclipse.core.internal.localstore.FileSystemResourceManager;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.IPath;

@SuppressWarnings("restriction")
public class PathUtility {

	public static URI workspaceRelativePathToAbsoluteURI(IPath relativePath, IWorkspace iworkspace) {
		if (iworkspace instanceof Workspace) {
			Workspace workspace = (Workspace) iworkspace;
			FileSystemResourceManager fsm = workspace.getFileSystemManager();
			return fsm.locationURIFor(workspace.getRoot().getFolder(relativePath));
		} else {
			return null;
		}
	}

	public static String URIPathToPath(String uriPath) {
		if (uriPath.indexOf('/') >= 0) {
			return uriPath.substring(uriPath.indexOf('/') + 1).split("!/")[0];
		} else {
			return uriPath.split("!/")[0];
		}
	}

}
