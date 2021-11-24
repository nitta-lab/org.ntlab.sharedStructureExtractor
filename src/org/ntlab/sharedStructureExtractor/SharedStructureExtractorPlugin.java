package org.ntlab.sharedStructureExtractor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.ntlab.sharedStructureExtractor.analyzerProvider.AbstractAnalyzer;
import org.ntlab.sharedStructureExtractor.analyzerProvider.DeltaExtractionAnalyzer;
import org.ntlab.sharedStructureExtractor.views.BreakPointView;
import org.ntlab.sharedStructureExtractor.views.BreakPointViewRelatedDelta;
import org.ntlab.sharedStructureExtractor.views.CallStackView;
import org.ntlab.sharedStructureExtractor.views.CallTreeView;
import org.ntlab.sharedStructureExtractor.views.TracePointsRegisterView;
import org.ntlab.sharedStructureExtractor.views.VariableView;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle.
 * 
 * @author Isitani
 * 
 */
public class SharedStructureExtractorPlugin extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.ntlab.sharedStructureExtractor"; //$NON-NLS-1$
	
	private static DebuggingController debuggingController = DebuggingController.getInstance();;

	private static AbstractAnalyzer analyzer;
	
	private static int uniqueIdForViews = 0;
	
	private static Map<String, Set<IViewPart>> viewIdToAllViews = new HashMap<>();
	
	private static Map<String, IViewPart> viewIdToActiveView = new HashMap<>();
	
	// The shared instance
	private static SharedStructureExtractorPlugin plugin;
	
	private static boolean isJapanese = false;
	
	/**
	 * The constructor
	 */
	public SharedStructureExtractorPlugin() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static SharedStructureExtractorPlugin getDefault() {
		return plugin;
	}
	
	public boolean fileOpenAction(Shell shell) {
		if (debuggingController.isLoadingTraceFile()) {
			MessageDialog.openInformation(null, "Loading", "This debugger is loading the trace.");	
			return false;
		}
		if (debuggingController.isRunning()) {
			MessageDialog.openInformation(null, "Running", "This debugger is running on the trace.");	
			return false;
		}
		FileDialog fileDialog = new FileDialog(shell, SWT.OPEN);
		fileDialog.setText("Open Trace File");
		fileDialog.setFilterExtensions(new String[]{"*.*"});
		String path = fileDialog.open();
		if (path == null) return false;
		
		((CallStackView)getActiveView(CallStackView.ID)).reset();
		((VariableView)getActiveView(VariableView.ID)).reset();
		((BreakPointView)getActiveView(BreakPointView.ID)).reset();
		TracePointsRegisterView tracePointsView = (TracePointsRegisterView)getActiveView(TracePointsRegisterView.ID);
		if (tracePointsView != null) tracePointsView.reset();
		CallTreeView callTreeView = (CallTreeView)getActiveView(CallTreeView.ID);
		if (callTreeView != null) callTreeView.reset();
		loadTraceFileOnNewThread(path);
		return true;
	}
	
	private void loadTraceFileOnNewThread(final String filePath) {
		final String msg = "Loading Trace File";
		Job job = new Job(msg) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				monitor.beginTask(msg + " (" + filePath + ")", IProgressMonitor.UNKNOWN);
				debuggingController.setLodingTraceFile();
				analyzer = null;
				TraceJSON trace = new TraceJSON(filePath);
				analyzer = new DeltaExtractionAnalyzer(trace);
				VariableUpdatePointFinder.getInstance().setTrace(trace);
				final TraceBreakPoints traceBreakPoints = new TraceBreakPoints(trace);
				debuggingController.setTraceBreakPoints(traceBreakPoints);

				// GUI can be operated only on the GUI thread.
				final BreakPointView breakpointView = (BreakPointView)getActiveView(BreakPointView.ID);
				Control control = breakpointView.getViewer().getControl();
				control.getDisplay().syncExec(new Runnable() {
					@Override
					public void run() {
						breakpointView.updateTraceBreakPoints(traceBreakPoints);
						breakpointView.updateImagesForBreakPoint(true);
					}
				});
				monitor.done();
				if (!(monitor.isCanceled())) {
					debuggingController.setHasLoadedTraceFile();
					return Status.OK_STATUS;
				} else {
					debuggingController.setHasNotLoadedTraceFile();
					return Status.CANCEL_STATUS;
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}
	
	public static AbstractAnalyzer getAnalyzer() {
		return analyzer;
	}
	
	public static IViewPart getActiveView(String viewId) {
		return viewIdToActiveView.get(viewId);
	}
	
	public static Map<String, Set<IViewPart>> getAllViews() {
		return viewIdToAllViews;
	}
	
	public static Set<IViewPart> getViews(String viewId) {
		return viewIdToAllViews.get(viewId);
	}
	
	public static void setAnalyzer(AbstractAnalyzer analyzer) {
		SharedStructureExtractorPlugin.analyzer = analyzer;
	}
	
	public static void setActiveView(String viewId, IViewPart activeView) {
		viewIdToActiveView.put(viewId, activeView);
		addView(viewId, activeView);
	}
	
	private static void addView(String viewId, IViewPart view) {
		Set<IViewPart> views = viewIdToAllViews.get(viewId);
		if (views == null) {
			views = new HashSet<IViewPart>();
			viewIdToAllViews.put(viewId, views);
		}
		views.add(view);
	}

	public static IViewPart createNewView(String viewId, int mode) {
		String secondaryId = "View" + (uniqueIdForViews++);
		IWorkbenchPage workbenchPage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		try {
			return workbenchPage.showView(viewId, secondaryId, mode);
		} catch (PartInitException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void removeView(String viewId, IViewPart view) {
		viewIdToActiveView.remove(viewId, view);
		Set<IViewPart> views = viewIdToAllViews.get(viewId);
		if (views != null) {
			views.remove(view);
		}
	}
	
	@Override
	protected void initializeImageRegistry(ImageRegistry reg) {
		// note: icons for the breakpoint view.
		reg.put(BreakPointView.DEBUG_ELCL, getImageDescriptor("/icons/debug/debug_elcl.png"));
		reg.put(BreakPointView.DEBUG_DLCL, getImageDescriptor("/icons/debug/debug_dlcl.png"));
		reg.put(BreakPointView.IMPORT_BREAKPOINT_ELCL, getImageDescriptor("/icons/debug/import_brkpts_elcl.png"));
		reg.put(BreakPointView.IMPORT_BREAKPOINT_DLCL, getImageDescriptor("/icons/debug/import_brkpts_dlcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_NEXT_ELCL, getImageDescriptor("/icons/debug/stepnext_elcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_NEXT_DLCL, getImageDescriptor("/icons/debug/stepnext_dlcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_BACK_INTO_ELCL, getImageDescriptor("/icons/debug/stepbackinto_elcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_BACK_INTO_DLCL, getImageDescriptor("/icons/debug/stepbackinto_dlcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_BACK_OVER_ELCL, getImageDescriptor("/icons/debug/stepbackover_elcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_BACK_OVER_DLCL, getImageDescriptor("/icons/debug/stepbackover_dlcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_BACK_RETURN_ELCL, getImageDescriptor("/icons/debug/stepbackreturn_elcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_BACK_RETURN_DLCL, getImageDescriptor("/icons/debug/stepbackreturn_dlcl.png"));
		reg.put(BreakPointViewRelatedDelta.BACK_RESUME_ELCL, getImageDescriptor("/icons/debug/backresume_elcl.png"));		
		reg.put(BreakPointViewRelatedDelta.BACK_RESUME_DLCL, getImageDescriptor("/icons/debug/backresume_dlcl.png"));
		
		// note: icons for the variable view.
		reg.put(VariableLabelProvider.PSEUDO_VARIABLE, getImageDescriptor("/icons/variable/specialvariable.png"));
		reg.put(VariableLabelProvider.THIS_VARIABLE, getImageDescriptor("/icons/variable/thisvariable.png"));
		reg.put(VariableLabelProvider.FIELD_VARIABLE, getImageDescriptor("/icons/variable/fieldvariable.png"));
		reg.put(VariableLabelProvider.ARG_VARIABLE, getImageDescriptor("/icons/variable/localvariable.png"));
	}
	
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}
}
