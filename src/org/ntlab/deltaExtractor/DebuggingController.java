package org.ntlab.deltaExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.ntlab.deltaExtractor.analyzerProvider.DeltaExtractionAnalyzer;
import org.ntlab.deltaExtractor.views.BreakPointView;
import org.ntlab.deltaExtractor.views.CallStackView;
import org.ntlab.deltaExtractor.views.CallTreeView;
import org.ntlab.deltaExtractor.views.DeltaMarkerView;
import org.ntlab.deltaExtractor.views.VariableView;
import org.ntlab.deltaExtractor.views.VariableViewRelatedDelta;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

/**
 * The controller of the forward/reverse debugger.
 * 
 * @author Isitani
 *
 */
public class DebuggingController {
	private static final DebuggingController theInstance = new DebuggingController();
	private TracePoint debuggingTp;
	private TraceBreakPoint selectedTraceBreakPoint;
	private TraceBreakPoints traceBreakPoints;
	private List<IMarker> currentLineMarkers = new ArrayList<>();
	private LoadingTraceFileStatus loadingTraceFileStatus = LoadingTraceFileStatus.NOT_YET;		// The status of the loading of a trace file.
	private boolean isRunning = false;		// The debugger is running.
	public static final String CURRENT_MARKER_ID = "org.ntlab.deltaExtractor.currentMarker";
	
	private enum LoadingTraceFileStatus {
		NOT_YET, PROGRESS, DONE
	}
	
	private DebuggingController() {
		
	}
	
	public static DebuggingController getInstance() {
		return theInstance;
	}
	
	public void setTraceBreakPoints(TraceBreakPoints traceBreakPoints) {
		this.traceBreakPoints = traceBreakPoints;
	}
	
	public void setDebuggingTp(TracePoint tp) {
		this.debuggingTp = tp;
	}
	
	public void setSelectedTraceBreakPoint(TraceBreakPoint tbp) {
		this.selectedTraceBreakPoint = tbp;
	}
	
	public TracePoint getCurrentTp() {
		return debuggingTp.duplicate();
	}
		
	public boolean isLoadingTraceFile() {
		return (loadingTraceFileStatus == LoadingTraceFileStatus.PROGRESS);
	}
	
	public boolean hasLoadedTraceFile() {
		return (loadingTraceFileStatus == LoadingTraceFileStatus.DONE);
	}
	
	public void setLodingTraceFile() {
		loadingTraceFileStatus = LoadingTraceFileStatus.PROGRESS;
	}
	
	public void setHasLoadedTraceFile() {
		loadingTraceFileStatus = LoadingTraceFileStatus.DONE;
	}
	
	public void setHasNotLoadedTraceFile() {
		loadingTraceFileStatus = LoadingTraceFileStatus.NOT_YET;
	}
	
	public boolean isRunning() {
		return isRunning;
	}
		
	public boolean addTraceBreakPointAction() {
		if (loadingTraceFileStatus != LoadingTraceFileStatus.DONE) {
			MessageDialog.openInformation(null, "Error", "Trace was not found");	
			return false;
		}
		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "", null);
		if (inputDialog.open() != InputDialog.OK) return false;
		String methodSignature = inputDialog.getValue();
		inputDialog = new InputDialog(null, "line Number dialog", "Input line number", "", null);
		if (inputDialog.open() != InputDialog.OK) return false;
		int lineNo = Integer.parseInt(inputDialog.getValue());
		boolean isSuccess = traceBreakPoints.addTraceBreakPoint(methodSignature, lineNo);
		if (!isSuccess) {
			MessageDialog.openInformation(null, "Error", "This point does not exist in the trace.");	
			return false;
		}
		((BreakPointView)DeltaExtractorPlugin.getActiveView(BreakPointView.ID)).updateTraceBreakPoints(traceBreakPoints);
		return true;
	}

	public boolean importBreakpointAction() {
		if (loadingTraceFileStatus != LoadingTraceFileStatus.DONE) {
			MessageDialog.openInformation(null, "Error", "Trace was not found");	
			return false;
		}
		traceBreakPoints.importBreakpointFromEclipse();
		((BreakPointView)DeltaExtractorPlugin.getActiveView(BreakPointView.ID)).updateTraceBreakPoints(traceBreakPoints);
		return true;
	}
	
	public boolean removeTraceBreakPointAction() {
		if (selectedTraceBreakPoint == null) return false;
		traceBreakPoints.removeTraceBreakPoint(selectedTraceBreakPoint);
		((BreakPointView)DeltaExtractorPlugin.getActiveView(BreakPointView.ID)).updateTraceBreakPoints(traceBreakPoints);
		return true;
	}
	
	public boolean debugAction() {
		if (loadingTraceFileStatus != LoadingTraceFileStatus.DONE) {
			MessageDialog.openInformation(null, "Error", "Trace was not found");				
			return false;
		}
		if (isRunning) {
			MessageDialog.openInformation(null, "Error", "This Debugger is running on the trace");	
			return false;
		}
		debuggingTp = traceBreakPoints.getFirstTracePoint();
		if (debuggingTp == null) {
			MessageDialog.openInformation(null, "Error", "No available breakpoint was found");	
			return false;
		}
		refresh(null, debuggingTp, false);
		((BreakPointView)DeltaExtractorPlugin.getActiveView(BreakPointView.ID)).updateImagesForDebug(true);
		isRunning = true;
		return true;
	}
	
	public void terminateAction() {
		debuggingTp = null;
		if (!(currentLineMarkers.isEmpty())) {
			for (IMarker currentLineMarker : currentLineMarkers) {
				try {
					currentLineMarker.delete();
				} catch (CoreException e) {
					e.printStackTrace();
				}				
			}
		}
		CallStackView callStackView = (CallStackView)DeltaExtractorPlugin.getActiveView(CallStackView.ID);
		if (callStackView != null) callStackView.reset();
		VariableView variableView = (VariableView)DeltaExtractorPlugin.getActiveView(VariableView.ID);
		if (variableView != null) variableView.reset();
		BreakPointView breakPointView = (BreakPointView)DeltaExtractorPlugin.getActiveView(BreakPointView.ID);
		if (breakPointView != null) breakPointView.updateImagesForDebug(false);		
		isRunning = false;
	}

	public boolean stepIntoAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		debuggingTp.stepFull();
		if (debugExecutionIsTerminated(debuggingTp)) return false;
		refresh(null, debuggingTp, false);
		return true;
	}

	public boolean stepOverAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		int currentLineNo = debuggingTp.getStatement().getLineNo();
		boolean isReturned = false;

		while (!(isReturned = !(debuggingTp.stepOver()))) {
			if (currentLineNo != debuggingTp.getStatement().getLineNo()) break;
			previousTp = debuggingTp.duplicate();
		}
		if (isReturned) {
			while (!debuggingTp.stepOver()); // Keep on going to the next statement in the called method.
		}
		if (debugExecutionIsTerminated(debuggingTp)) return false;
		refresh(previousTp, debuggingTp, isReturned, true);
		return true;
	}
	
	public boolean stepReturnAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		
		// note: Keep on going until the control is returned to the calling method.
		while (debuggingTp.stepOver()) {
			previousTp = debuggingTp.duplicate();
		}
		while (!debuggingTp.stepOver()); // Keep on going to the next statement in the called method.
		
		if (debugExecutionIsTerminated(debuggingTp)) return false;
		refresh(previousTp, debuggingTp, true);
		return true;
	}

	public boolean stepNextAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		boolean isReturned = !(debuggingTp.stepNext());
		if (isReturned) {
			while (!debuggingTp.stepOver()); // Keep on going to the next statement in the called method.
		}
		if (debugExecutionIsTerminated(debuggingTp)) return false;
		refresh(previousTp, debuggingTp, isReturned, true);
		return true;
	}
	
	public boolean resumeAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		long currentTime = debuggingTp.getStatement().getTimeStamp();
		TracePoint previousTp = debuggingTp;
		debuggingTp = traceBreakPoints.getNextTracePoint(currentTime);
		if (debugExecutionIsTerminated(debuggingTp)) return false;
		refresh(null, debuggingTp, false);
		return true;
	}
	
	public boolean stepBackIntoAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		debuggingTp.stepBackFull();
		if (debugExecutionIsTerminated(debuggingTp)) return false;
		refresh(null, debuggingTp, true);
		return true;
	}
	
	public boolean stepBackOverAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		int currentLineNo = debuggingTp.getStatement().getLineNo();
		boolean isReturned;
		while (!(isReturned = !debuggingTp.stepBackOver())) {
			if (currentLineNo != debuggingTp.getStatement().getLineNo()) break;
		}
		if (debugExecutionIsTerminated(debuggingTp)) return false;
		refresh(null, debuggingTp, !isReturned);
		return true;
	}
	
	public boolean stepBackReturnAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		while (debuggingTp.stepBackOver());
		if (debugExecutionIsTerminated(debuggingTp)) return false;
		refresh(null, debuggingTp, false);
		return true;
	}
	
	public boolean backResumeAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;		
		TracePoint previousTp = debuggingTp;
		long currentTime = debuggingTp.getStatement().getTimeStamp();
		debuggingTp = traceBreakPoints.getPreviousTracePoint(currentTime);
		if (debugExecutionIsTerminated(debuggingTp)) return false;
		refresh(null, debuggingTp, false);
		return true;
	}
	
	private boolean debugExecutionIsTerminated(TracePoint tp) {
		if (tp == null || !(tp.isValid())) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "The execution is terminated");	
			return true;
		}
		return false;
	}

	/**
	 * Move the current execution point to the specified one. 
	 * @return
	 */
	public boolean jumpToTheTracePoint(TracePoint tp, boolean isReturned) {
		if (!isRunning) return false;
		if (tp == null) return false;
		TracePoint previousTp = debuggingTp;
		debuggingTp = tp.duplicate();
		refresh(null, debuggingTp, isReturned);
		return true;
	}
	
	private void refresh(TracePoint from, TracePoint to, boolean isReturned) {
		refresh(from, to, isReturned, false);
	}
	
	private void refresh(TracePoint from, TracePoint to, boolean isReturned, boolean canDifferentialUpdateVariables) {
		List<IMarker> markers = createCurrentLineMarkers(to);
		if (!(markers.isEmpty())) JavaEditorOperator.markAndOpenJavaFile(markers.get(0));

		CallStackView callStackView = ((CallStackView)DeltaExtractorPlugin.getActiveView(CallStackView.ID));
		callStackView.updateByTracePoint(to);
		VariableView variableView = ((VariableView)DeltaExtractorPlugin.getActiveView(VariableView.ID));
		if (!isReturned && canDifferentialUpdateVariables) {
			variableView.updateVariablesForDifferential(from, to);
		} else {
			variableView.updateVariablesByTracePointsFromAToB(from, to);
		}
		if (DeltaExtractorPlugin.getActiveView(DeltaMarkerView.ID) != null) {
			refreshRelatedDelta(to);
		}
	}
	
	private void refreshRelatedDelta(TracePoint tp) {
		DeltaMarkerView deltaMarkerView = (DeltaMarkerView)DeltaExtractorPlugin.getActiveView(DeltaMarkerView.ID);
		if (deltaMarkerView == null) return;
		DeltaMarkerManager deltaMarkerManager = deltaMarkerView.getDeltaMarkerManager();
		if (deltaMarkerManager == null) return;
		IMarker coordinatorMarker = deltaMarkerManager.getCoordinatorDeltaMarker();
		if (coordinatorMarker == null) return;
		MethodExecution coordinatorME = DeltaMarkerManager.getMethodExecution(coordinatorMarker);
		CallStackView callStackView = (CallStackView)DeltaExtractorPlugin.getActiveView(CallStackView.ID);
		callStackView.highlight(coordinatorME);
		CallTreeView callTreeView = (CallTreeView)DeltaExtractorPlugin.getActiveView(CallTreeView.ID);
		callTreeView.highlight(tp.getMethodExecution());
		VariableViewRelatedDelta variableView = (VariableViewRelatedDelta)DeltaExtractorPlugin.getActiveView(VariableViewRelatedDelta.ID);
		variableView.markAndExpandVariablesByDeltaMarkers(deltaMarkerManager.getMarkers());
	}

	public List<IMarker> createCurrentLineMarkers(TracePoint tp) {
		deleteCurrentLineMarkers();
		while (tp != null) {
			try {
				MethodExecution methodExecution = tp.getMethodExecution();
				int highlightLineNo = tp.getStatement().getLineNo();
				IFile file = JavaElementFinder.findIFile(methodExecution);
				IMarker currentLineMarker = file.createMarker(CURRENT_MARKER_ID);
				currentLineMarkers.add(currentLineMarker);
				Map<String, Object> attributes = new HashMap<>();
				attributes.put(IMarker.TRANSIENT, true);
				attributes.put(IMarker.LINE_NUMBER, highlightLineNo);			
				
				IPath path = file.getFullPath();
				ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();			
				manager.connect(path, LocationKind.IFILE, null);
				ITextFileBuffer buffer = manager.getTextFileBuffer(path, LocationKind.IFILE);
				IDocument document = buffer.getDocument();
				try {
					IRegion region = document.getLineInformation(highlightLineNo - 1);
					attributes.put(IMarker.CHAR_START, region.getOffset());
					attributes.put(IMarker.CHAR_END, region.getOffset() + region.getLength());
				} catch (BadLocationException e) {
					e.printStackTrace();
				}
				currentLineMarker.setAttributes(attributes);
			} catch (CoreException e) {
				e.printStackTrace();
			}
			tp = tp.getMethodExecution().getCallerTracePoint();
		}
		return currentLineMarkers;
	}
	
	private void deleteCurrentLineMarkers() {
		for (IMarker currentLineMarker : currentLineMarkers) {
			try {
				currentLineMarker.delete();
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		currentLineMarkers.clear();
	}
	
	private TracePoint getTracePointSelectedOnCallStack() {
		CallStackView callStackView = (CallStackView)DeltaExtractorPlugin.getActiveView(CallStackView.ID);
		CallStackModel callStackModel = callStackView.getSelectionCallStackModel();
		if (callStackModel != null) {
			return callStackModel.getTracePoint();
		}
		return null;
	}
	
	public void resetExcludingForLoadingStatusOfTheTrace() {
		terminateAction();
		selectedTraceBreakPoint = null;
	}
}
