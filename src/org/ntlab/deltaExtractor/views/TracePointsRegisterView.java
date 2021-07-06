package org.ntlab.deltaExtractor.views;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.deltaExtractor.DebuggingController;
import org.ntlab.deltaExtractor.DeltaExtractorPlugin;
import org.ntlab.deltaExtractor.JavaEditorOperator;
import org.ntlab.deltaExtractor.TracePointsRegister;
import org.ntlab.deltaExtractor.TracePointsRegisterLabelProvider;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class TracePointsRegisterView extends ViewPart {
	private TableViewer viewer;
	private Shell shell;
	private IAction addAction;
	private IAction removeAction;
	private IAction jumpAction;
	private TracePoint selectedTp;
	private TracePointsRegister tracePoints = new TracePointsRegister();
	public static final String ID = "org.ntlab.deltaExtractor.tracePointsRegisterView";

	public TracePointsRegisterView() {
	}

	@Override
	public void createPartControl(Composite parent) {
		shell = parent.getShell();
		viewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION);
		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		// Create the columns of the table of the registered trace points.
		String[] tableColumnTexts = new String[]{"Line", "Signature"};
		int[] tableColumnWidth = {80, 1000};
		TableColumn[] tableColumns = new TableColumn[tableColumnTexts.length];
		for (int i = 0; i < tableColumns.length; i++) {
			tableColumns[i] = new TableColumn(table, SWT.NULL);
			tableColumns[i].setText(tableColumnTexts[i]);
			tableColumns[i].setWidth(tableColumnWidth[i]);
		}
		viewer.setContentProvider(new ArrayContentProvider());
		viewer.setLabelProvider(new TracePointsRegisterLabelProvider());
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (element instanceof TracePoint) {
					selectedTp = (TracePoint)element;
					MethodExecution me = selectedTp.getMethodExecution();
					int lineNo = selectedTp.getStatement().getLineNo();
					JavaEditorOperator.openSrcFileOfMethodExecution(me, lineNo);
				}
			}
		});
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (element instanceof TracePoint) {
					selectedTp = (TracePoint)element;
					if (DebuggingController.getInstance().isRunning()) {
						jumpToTheTracePoint(selectedTp);	
					}
				}
			}
		});

		createActions();
		createToolBar();
		createMenuBar();
		createPopupMenu();
		DeltaExtractorPlugin.setActiveView(ID, this);
	}
	
	@Override
	public String getTitle() {
		return "MarkedExecutionPoints";
	}

	@Override
	public void setFocus() {
		DeltaExtractorPlugin.setActiveView(ID, this);
		viewer.getControl().setFocus();
	}
	
	@Override
	public void dispose() {
		DeltaExtractorPlugin.removeView(ID, this);
	}
	
	private void createActions() {
		String msg;

		addAction = new Action() {
			@Override
			public void run() {
				DebuggingController debuggingController = DebuggingController.getInstance();
				TracePoint currentTp = debuggingController.getCurrentTp();
				addTracePoint(currentTp);
			}
		};
		msg = "Add";
		addAction.setText(msg);
		addAction.setToolTipText(msg);
		
		removeAction = new Action() {
			@Override
			public void run() {
				if (selectedTp != null) {
					tracePoints.remove(selectedTp);
					update();					
				}
			}
		};
		msg = "Remove";
		removeAction.setText(msg);
		removeAction.setToolTipText(msg);
		
		jumpAction = new Action() {
			@Override
			public void run() {
				if (selectedTp != null && DebuggingController.getInstance().isRunning()) {
					jumpToTheTracePoint(selectedTp);	
				}
			}
		};
		msg = "Jump";
		jumpAction.setText(msg);
		jumpAction.setToolTipText(msg);
	}
	
	private void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		mgr.add(addAction);
		mgr.add(removeAction);
		mgr.add(jumpAction);
	}
	
	private void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
		mgr.add(addAction);
		mgr.add(removeAction);
		mgr.add(jumpAction);
	}
	
	private void createPopupMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
//				manager.add(addAction);
//				manager.add(removeAction);
//				manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}
	
	public void addTracePoint(TracePoint tp) {
		if (!(tracePoints.contains(tp))) {
			tracePoints.add(tp);
			update();			
		}
	}

	public void reset() {
		tracePoints.clear();
		update();
	}
	
	private void update() {
		viewer.setInput(tracePoints.getToArray());
		viewer.refresh();
	}
	
	protected void jumpToTheTracePoint(TracePoint tp) {
		DebuggingController debuggingController = DebuggingController.getInstance();
		debuggingController.jumpToTheTracePoint(tp, false);
		List<IMarker> markers = DebuggingController.getInstance().createCurrentLineMarkers(tp);
		if (!(markers.isEmpty())) JavaEditorOperator.markAndOpenJavaFile(markers.get(0));
	}
}