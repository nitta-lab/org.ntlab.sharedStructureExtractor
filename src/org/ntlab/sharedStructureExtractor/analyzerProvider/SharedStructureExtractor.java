package org.ntlab.sharedStructureExtractor.analyzerProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class SharedStructureExtractor {
	public Stack<Reference> stack = new Stack<Reference>();
	public Set<Reference> extractedSharedStructure = new HashSet<Reference>();
	
	public SharedStructureExtractor() {
	}

	public void extract() {
		// TODO Auto-generated method stub
		long time = System.nanoTime();
		//input
//		String startingPointDependingFeature = "protected void org.tigris.gef.base.SelectionManager.addFig(";	
		//D1
		//図形開始機能の開始時間にすると...
		long startingTimeDependedFeature = 340186653401300L;
		String sharedObject = "org.argouml.uml.diagram.static_structure.ui.FigClass";
	    String startingPointDependingFeature = "public void org.argouml.uml.diagram.ui.ActionRemoveFromDiagram.actionPerformed(";	
		String tracePath = "traces\\ArgoUMLBenchmarkWithMoreStandardClasses.trace";	
		
		//D2
//		long startingTimeDependedFeature = 340186489032100L;
//	    String startingPointDependingFeature = "public void org.tigris.gef.base.ModeSelect.mousePressed(";	
//		String sharedObject = "org.argouml.uml.diagram.static_structure.ui.FigClass";
//		String tracePath = "traces\\ArgoUMLBenchmarkWithMoreStandardClasses.trace";	
		
		//D3
//		//isArrayがtrueにならない？
//		long startingTimeDependedFeature = 399760005397200L;
//	    String startingPointDependingFeature = "private void org.gjt.sp.jedit.textarea.TextArea.delete(";	
//		String sharedObject = "org.gjt.sp.jedit.textarea.Selection$Range";
//		String tracePath = "traces\\jEditBenchmarkWithMoreStandardClasses.trace";	
		
		//D4
		//itrへの対応(多分済) 
//		long startingTimeDependedFeature = 242741022589800L;
//	    String startingPointDependingFeature = "public void org.jhotdraw.draw.tool.DefaultDragTracker.mouseDragged(";	
//		String sharedObject = "org.jhotdraw.draw.RectangleFigure";
//		String tracePath = "traces\\jHotDrawBenchmarkWithMoreStandardClasses.trace";	
		
		//D5
		//いろいろ取りすぎ？
//		long startingTimeDependedFeature = 242740038130700L;
//	    String startingPointDependingFeature = "public void org.jhotdraw.draw.tool.SelectionTool.mousePressed(";	
//		String sharedObject = "org.jhotdraw.draw.RectangleFigure";
//		String tracePath = "traces\\jHotDrawBenchmarkWithMoreStandardClasses.trace";	
		
		DeltaExtractorJSON s = new DeltaExtractorJSON(tracePath);
		MethodExecution m = null;
		ExtractedStructure e = null;

		//first delta extraction
		System.out.println(System.nanoTime() - time);
		m = s.getLastMethodExecution(startingPointDependingFeature);
		if(m != null) {
			Map<ObjectReference, TracePoint> map = m.getObjectReferences(sharedObject);
			for(ObjectReference obj : map.keySet() ) {
				e = s.extract(map.get(obj), obj);
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
					addToExtractedSharedStructure(ref, s, lastTp, startingTimeDependedFeature);
				}
			}
			
			if(!stack.empty()) {
				Reference ref = stack.pop();
				e = s.extract(ref, lastTp);
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

	}
	
	public void addToExtractedSharedStructure(Reference ref, DeltaExtractorJSON s, TracePoint lastTp, long startingTimeDependedFeature) {
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
		if(startingTimeDependedFeature <= t) {
			if(!ref.getSrcObjectId().equals("0")) {
				if(!extractedSharedStructure.contains(ref) && !stack.contains(ref)) {
					stack.add(ref);
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
				do {
					if(node.containsKey(toObj.getId())) {
						toObj = node.get(toObj.getId());
					}else {
						break;
					}
				}while(true);
				Reference newRef = new Reference(ref.getSrcObject(), toObj);
				newRef.setCollection(true);
				shrinkedRefs.add(newRef);
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
