package org.ntlab.sharedStructureExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.TreeNode;
import org.ntlab.traceAnalysisPlatform.tracer.trace.IStatementVisitor;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ThreadInstance;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class CallStackModels {
	private String debuggingThreadId = "";
	private List<CallStackModel> debuggingThreadCallStacks = new ArrayList<>();
	private Map<String, List<CallStackModel>> allCallStacks = new HashMap<>();

	public List<CallStackModel> getDebuggingThreadCallStacks() {
		return debuggingThreadCallStacks;
	}
	
	public Map<String, List<CallStackModel>> getAllCallStacks() {
		return allCallStacks;
	}

	public TreeNode[] getAllCallStacksTree() {
		if (allCallStacks.isEmpty()) return new TreeNode[1];
		TreeNode[] roots = new TreeNode[allCallStacks.size()];
		int rootIndex = 0;
		for (String threadId : allCallStacks.keySet()) {
			TreeNode node = getCallStackModelsTree(threadId);
			roots[rootIndex++] = node;
		}
		return roots;
	}

	public TreeNode getCallStackModelsTree(String threadId) {
		List<CallStackModel> callStackModelsInThread = allCallStacks.get(threadId);
		if (callStackModelsInThread.isEmpty()) return null;
		TreeNode root = new TreeNode(threadId);
		TreeNode parentNode = root;
		TreeNode[] childrenNode = new TreeNode[callStackModelsInThread.size()];
		parentNode.setChildren(childrenNode);
		for (int i = 0; i < callStackModelsInThread.size(); i++) {
			TreeNode childNode = new TreeNode(callStackModelsInThread.get(i));
			childNode.setParent(parentNode);
			childrenNode[i] = childNode;
		}
		return root;
	}
	
	public TreeNode[] getDebugingThreadCallStacksTree() {
		TreeNode[] roots = new TreeNode[1];
		roots[0] = getCallStackModelsTree(debuggingThreadId);
		return roots;
	}

	public void reset() {
		debuggingThreadId = "";
		debuggingThreadCallStacks.clear();
		allCallStacks.clear();
	}
	
	public void updateByTracePoint(TracePoint tp) {
		if (tp == null) return;
		int lineNo = tp.getStatement().getLineNo();
		updateInAllThreads(tp, lineNo);
	}
	
	private void updateInAllThreads(TracePoint tp, int topMethodCallLineNo) {
		Statement tpStatement = tp.getStatement();
		reset();
		debuggingThreadId = tpStatement.getThreadNo();
		debuggingThreadCallStacks = update(tp);
		allCallStacks.put(debuggingThreadId, debuggingThreadCallStacks);
//		IStatementVisitor visitor = new CallStackVisitor(tp);
//		updateOtherThreadCallStacks(visitor);		
	}
	
	private List<CallStackModel> update(TracePoint tp) {
		List<CallStackModel> list = new ArrayList<>();
		TracePoint tmpTp = tp;
		while (tmpTp != null) {
			CallStackModel callStackModel = new CallStackModel(tmpTp);
			list.add(callStackModel);
			tmpTp = tmpTp.getMethodExecution().getCallerTracePoint();
		}
		return list;
	}

	private void updateOtherThreadCallStacks(IStatementVisitor visitor) {
		TraceJSON traceJSON = (TraceJSON)SharedStructureExtractorPlugin.getAnalyzer().getTrace();
		Map<String, ThreadInstance> allThreads = traceJSON.getAllThreads();
		for (String threadId : allThreads.keySet()) {
			if (threadId.equals(debuggingThreadId)) continue;
			TracePoint[] start = new TracePoint[1];
			start[0] = allThreads.get(threadId).getCurrentTracePoint();
			traceJSON.getLastStatementInThread(threadId, start, visitor);
			TracePoint resultTp = start[0];
			allCallStacks.put(threadId, update(resultTp));
		}
	}

	public void highlight(MethodExecution methodExecution) {
		String signature1 = methodExecution.getSignature();
		for (List<CallStackModel> callStackModels : allCallStacks.values()) {
			for (CallStackModel callStackModel : callStackModels) {
				String signature2 = callStackModel.getMethodExecution().getSignature();
				callStackModel.setHighlighting(signature1.equals(signature2));
			}
		}		
	}
	
	public void removeHighlight() {
		for (List<CallStackModel> callStackModels : allCallStacks.values()) {
			for (CallStackModel callStackModel : callStackModels) {
				callStackModel.setHighlighting(false);
			}
		}		
	}

//	public IStatementVisitor tmp(TracePoint tp) {
////		return new CallStackVisitor(tp);
//		try {
//			Class<?> classClass = Class.forName("java.lang.Class");
//			Class<?>[] classClassArray = (Class[])Array.newInstance(classClass, 1);
//			Class<?> tpClass = tp.getClass();
//			Array.set(classClassArray, 0, tpClass);
//			Class<?> visitorClass = Class.forName("org.ntlab.reverseDebugger.CallStackVisitor");
//			Constructor<?> constructor = visitorClass.getConstructor(classClassArray);
//			Class<?>[] tpClassArray = (Class[])Array.newInstance(tpClass, 1);
//			Array.set(tpClassArray, 0, tp);
//			IStatementVisitor visitor = (IStatementVisitor)constructor.newInstance(tpClassArray);
//			return visitor;
//		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException
//				| IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
//			e.printStackTrace();
//		}
//		return null;
//	}	
}
