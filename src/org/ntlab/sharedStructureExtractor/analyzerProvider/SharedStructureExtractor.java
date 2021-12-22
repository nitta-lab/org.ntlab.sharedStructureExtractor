package org.ntlab.sharedStructureExtractor.analyzerProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;

import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class SharedStructureExtractor {
	public Stack<Reference> stack = new Stack<Reference>();
	public Map<Reference, TracePoint> refToLastTp = new HashMap<Reference, TracePoint>();
	public Set<Reference> extractedSharedStructure = new HashSet<Reference>();
	
	public IAliasTracker aliasTracker = new IAliasTracker() {
		public ArrayList<Alias> aliasList =  new ArrayList<Alias>();

		@Override
		public List<Alias> getAliasList() {
			// TODO Auto-generated method stub
			return aliasList;
		}
		
		@Override
		public void addAlias(Alias alias) {
			// TODO Auto-generated method stub
			aliasList.add(alias);
		}
		
		@Override
		public void changeTrackingObject(String from, String to, boolean isSrcSide) {
			// TODO Auto-generated method stub
		}
	};
		
	public SharedStructureExtractor() {
	}

	public void extract() {
		// TODO Auto-generated method stub
		long time = System.nanoTime();
		//input
//		String startingPointDependingFeature = "protected void org.tigris.gef.base.SelectionManager.addFig(";	
		//D1 OK 
		//long startingTimeDependedFeature = 340186653401300L;//多分違う
		long startingTimeDependedFeature = 340188826720900L;
		long endTimeDependedFeature = 340188848197200L;	
		long startingTimeDependingFeature = 340190571779800L;
		String sharedObject = "org.argouml.uml.diagram.static_structure.ui.FigClass";
	    String startingPointDependingFeature = "public void org.argouml.uml.diagram.ui.ActionRemoveFromDiagram.actionPerformed(";	
		String tracePath = "traces\\ArgoUMLBenchmarkWithMoreStandardClasses.trace";	
		
		//D2
//		long startingTimeDependedFeature = 340186489032100L;
//		long endTimeDependedFeature = 340186766341500L;	
//		long startingTimeDependingFeature = 340188826720900L;
//		//long startingTimeDependingFeature = 340186653401300L; //多分違う
//	    String startingPointDependingFeature = "public void org.tigris.gef.base.ModeSelect.mousePressed(";	
//		String sharedObject = "org.argouml.uml.diagram.static_structure.ui.FigClass";
//		String tracePath = "traces\\ArgoUMLBenchmarkWithMoreStandardClasses.trace";	
		
		//D3
//		long startingTimeDependedFeature = 399757723832900L;
//		long endTimeDependedFeature = 399758531407600L;
//		long startingTimeDependingFeature = 399760005373600L;
//	    String startingPointDependingFeature = "private void org.gjt.sp.jedit.textarea.TextArea.delete(";	
//		String sharedObject = "org.gjt.sp.jedit.textarea.Selection$Range";
//		String tracePath = "traces\\jEditBenchmarkWithMoreStandardClasses.trace";	
		
		//D4
//		long startingTimeDependedFeature = 242741022589800L;
//		long endTimeDependedFeature = 242741173330700L;
//		long startingTimeDependingFeature =242745352334200L;
//	    String startingPointDependingFeature = "public void org.jhotdraw.draw.tool.DefaultDragTracker.mouseDragged(";	
//		String sharedObject = "org.jhotdraw.draw.RectangleFigure";
//		String tracePath = "traces\\jHotDrawBenchmarkWithMoreStandardClasses.trace";	
		
		//D5
//		long startingTimeDependedFeature = 242738929650000L;
//		long endTimeDependedFeature =       242740075715600L;
//		long startingTimeDependingFeature =242744389297100L;
//	    String startingPointDependingFeature = "public void org.jhotdraw.draw.tool.SelectionTool.mousePressed(";	
//		String sharedObject = "org.jhotdraw.draw.RectangleFigure";
//		String tracePath = "traces\\jHotDrawBenchmarkWithMoreStandardClasses.trace";	
		
		DeltaExtractorJSON s = new DeltaExtractorJSON(tracePath);
		MethodExecution m = null;
		ExtractedStructure e = null;

		//first delta extraction
		System.out.println(System.nanoTime() - time);
		
		m = s.getLastMethodExecution(startingPointDependingFeature);
//		{
//			TracePoint  tp= m.getEntryPoint();
//			tp.stepBackOver();
//			for(int i=0; i<7; i++) {
//				m = s.getLastMethodExecution(startingPointDependingFeature, tp);
//				tp.stepBackOver();
//			}
//		}

		if(m != null) {
			Map<ObjectReference, TracePoint> map = m.getObjectReferences(sharedObject);
			for(ObjectReference obj : map.keySet() ) {
				e = s.extract(map.get(obj), obj, aliasTracker);
				//break;//暫定的に最初のデルタだけを対象とする
			}
		}
		
		System.out.println("----------------------------------");
		TracePoint lastTp = m.getExitPoint();
		
		do{
			if(e != null){
				//はじめにcollectionAddのシュリンクを行う
				Set<Reference> extractedReferences = summarizeExtractedRef(e);
				
				//抽出された共有構造に追加，デルタ抽出対象の参照を判定する
				for(Reference ref : extractedReferences) {
					addToExtractedSharedStructure(ref, e, s, lastTp, startingTimeDependedFeature, endTimeDependedFeature, startingTimeDependingFeature);
				}
			}
			
			if(!stack.empty()) {
				Reference ref = stack.pop();
				lastTp = refToLastTp.get(ref);
				e = s.extract(ref, lastTp, aliasTracker);
				System.out.println("----------------------------------");
			}else {
				break;
			}
		}while(true);
		
		
		//出力
		System.out.println("extractedSharedStructure");
		for(Reference ref : extractedSharedStructure) {
			System.out.println(ref.getSrcClassName() + "(" + ref.getSrcObjectId() + ") -> " + ref.getDstClassName() + "("  + ref.getDstObjectId() +")" );
		}
		
		System.out.println("----------------------------------");
		System.out.println("extractedLineNo");
		HashMap<String, HashSet<Integer>> extractedLines = new HashMap<String, HashSet<Integer>>();
		for(Alias alias : aliasTracker.getAliasList()) {
			int lineNo = alias.getLineNo();
			String className = alias.getMethodExecution().getThisClassName();
			if(extractedLines.containsKey(className)) {
				extractedLines.get(className).add(lineNo);
			}else {
				HashSet<Integer> set = new HashSet<Integer>();
				set.add(lineNo);
				extractedLines.put(className, set);
			}
		}
		
		for(String className : extractedLines.keySet()) {
			System.out.println(className);
			for(int lineNo : extractedLines.get(className)) {
				System.out.println(lineNo);
			}
		}

	}
	
	
	public void addToExtractedSharedStructure(Reference ref, ExtractedStructure e, DeltaExtractorJSON s, TracePoint lastTp,
			long startingTimeDependedFeature, long endTimeDependedFeature, long startingTimeDependingFeature) {
		long relatedTime = e.getRelatedTracePoint().getStatement().getTimeStamp();
		if(relatedTime < startingTimeDependingFeature) {
			//依存元機能で使われていない参照に対するデルタ　⇒　これ以上追跡しない
			extractedSharedStructure.add(ref);
			return;
		}
		
		TracePoint tp;
		if(ref.isCreation()) {
			tp = s.trace.getCreationTracePoint(ref.getDstObject(), lastTp);
		}else if(ref.isCollection()) {
			tp = s.trace.getCollectionAddTracePoint(ref, lastTp);
		}else if(ref.isArray()){
			tp = s.trace.getArraySetTracePoint(ref, lastTp);
		}else {
			tp = s.trace.getFieldUpdateTracePoint(ref, lastTp);
		}
		
		if(tp == null) {
			extractedSharedStructure.add(ref);
			return;
		}
		long t = tp.getStatement().getTimeStamp();
		if((startingTimeDependedFeature < t && t < endTimeDependedFeature)
				||  startingTimeDependingFeature< t ) {
			if(!ref.getSrcObjectId().equals("0")) {
				if(!extractedSharedStructure.contains(ref) && !stack.contains(ref)) {
					stack.add(ref);
					refToLastTp.put(ref, lastTp);
				}
			}
		}
		extractedSharedStructure.add(ref);
	}
	
	public Set<Reference> summarizeExtractedRef(ExtractedStructure e){
		Set<Reference> refSet =  new HashSet<Reference>();
		
		//collectionAddの集約
		refSet.addAll(shrink(e.getDelta().getSrcSide()));
		refSet.addAll(shrink(e.getDelta().getDstSide()));
		
		return refSet;
	}
	
	public Set<Reference> shrink(ArrayList<Reference> refs){
		Set<Reference> shrinkedRefs = new HashSet<Reference>();
		
		//始点以外のオブジェクトのIDを調べる
		Set<String> notRoot = new HashSet<String>(); 
		Map<String, ObjectReference> node = new HashMap<String, ObjectReference>();
		for(Reference ref : refs) {
			if(ref.isCollection()) {
				notRoot.add(ref.getDstObjectId());
				node.put(ref.getSrcObjectId(), ref.getDstObject());
			}
		}
		
		for(Reference ref : refs) {
			if(!ref.isCollection()) {
				shrinkedRefs.add(ref);
				continue;
			}
			if(!notRoot.contains(ref.getSrcObjectId())) {
				ObjectReference toObj = ref.getSrcObject();
				while(node.containsKey(toObj.getId())) {
					toObj = node.get(toObj.getId());
				}
				Reference newRef = new Reference(ref.getSrcObject(), toObj);
				newRef.setCollection(true);
				shrinkedRefs.add(newRef);
			}
		}
		
		//collectionが配列を参照している時 jedit ArrayList(selection) -> [Lorg.gjt.sp.jedit.textarea.Selection;
		for(Reference ref : refs) {
			if(ref.getDstClassName().startsWith("[")) {
				for(Reference r : refs) {
					if(r.getSrcClassName().startsWith("[")) {
						Reference newRef = new Reference(ref.getSrcObject(), r.getDstObject());
						newRef.setCollection(true);
						shrinkedRefs.add(newRef);
						break;
					}
				}
			}
		}
			
		//////////////////////////////////////////////////////////////
		//とりあえず決め打ち
//		Reference newRef = new Reference(null, null, null, null);
//		newRef.setCollection(true);
//		Set<Reference> removedRefs =  new HashSet<Reference>();
//		
//		for(Reference ref : refs) {
//			if(ref.isCollection()) {
//				if(ref.getSrcClassName().equals("java.util.Vector$Itr")) {
//					newRef.setDstClassName(ref.getDstClassName());
//					newRef.setDstObjectId(ref.getDstObjectId());
//					removedRefs.add(ref);
//				}else if(ref.getDstClassName().equals("java.util.Vector$Itr")) {
//					newRef.setSrcClassName(ref.getSrcClassName());
//					newRef.setSrcObjectId(ref.getSrcObjectId());
//					removedRefs.add(ref);
//				}
//			}else {
//				newRefs.add(ref);
//			}
//		}
//		
//		if(!removedRefs.isEmpty()) {
//			newRefs.add(newRef);
//		}
		//////////////////////////////////////////////////////
		
		return shrinkedRefs;
	}

}
