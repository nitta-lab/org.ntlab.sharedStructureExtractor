package org.ntlab.sharedStructureExtractor.analyzerProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.ntlab.sharedStructureExtractor.analyzerProvider.Alias.AliasType;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class SharedStructureExtractor {
	public Set<Reference> extractedSharedStructure = new HashSet<Reference>();
	public HashMap<String, HashSet<Integer>> extractedLines = new HashMap<String, HashSet<Integer>>();
	public Stack<Reference> stack = new Stack<Reference>();
	public Map<Reference, TracePoint> refToLastTp = new HashMap<Reference, TracePoint>();
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
	
	//�ŏ��̃f���^��target��ڂ̎Q�Ƃɑ΂���f���^
	public void extract(TraceJSON trace, long startingTimeDependedFeature, long endTimeDependedFeature, long startingTimeDependingFeature,
			String sharedObject, String startingPointDependingFeature, int target) {
		long time = System.nanoTime();
		ArrayList<TracePoint>bottomTracePoints = new ArrayList<TracePoint>();//�f���^�̒�ӕ���
		DeltaExtractorJSON s = new DeltaExtractorJSON(trace);
		MethodExecution m = null;
		ExtractedStructure e = null;

	
		//////�������珈��//////
		System.out.println(System.nanoTime() - time);
		m = s.getLastMethodExecution(startingPointDependingFeature);
		{
			TracePoint  tp = m.getEntryPoint();
			tp.stepBackOver();
			for(int i=1; i<target; i++) {
				m = s.getLastMethodExecution(startingPointDependingFeature, tp);
				tp.stepBackOver();
			}
		}

		if(m != null) {
			Map<ObjectReference, TracePoint> map = m.getObjectReferences(sharedObject);
			for(ObjectReference obj : map.keySet() ) {
				e = s.extract(map.get(obj), obj, aliasTracker);
				if(e != null) {
					bottomTracePoints.add(e.getRelatedTracePoint());
					System.out.println("----------------------------------");
					extractSub(s, startingTimeDependedFeature, endTimeDependedFeature, startingTimeDependingFeature, sharedObject, startingPointDependingFeature, e, m, bottomTracePoints);
					//break;//�b��I�ɍŏ��̃f���^������ΏۂƂ���
				}
			}
		}
	}		
		

	
	//�ŏ��̃f���^���o��TracePoint�ōs��
	public void extract(TraceJSON trace, long startingTimeDependedFeature, long endTimeDependedFeature, long startingTimeDependingFeature,
			String sharedObject, String startingPointDependingFeature, TracePoint tp) {
		ArrayList<TracePoint>bottomTracePoints = new ArrayList<TracePoint>();//�f���^�̒�ӕ���
		DeltaExtractorJSON s = new DeltaExtractorJSON(trace);
		MethodExecution m = null;
		ExtractedStructure e = null;
		
		e = s.extract(tp, aliasTracker);
		if(e != null) {
			bottomTracePoints.add(e.getRelatedTracePoint());
			System.out.println("----------------------------------");
			extractSub(s, startingTimeDependedFeature, endTimeDependedFeature, startingTimeDependingFeature, sharedObject, startingPointDependingFeature, e, m, bottomTracePoints);
		}
		
	}

	//�ŏ��̃f���^��target��ڂ̎Q�Ƃɑ΂���f���^
	public void extractSub(DeltaExtractorJSON s, long startingTimeDependedFeature, long endTimeDependedFeature, long startingTimeDependingFeature,
			String sharedObject, String startingPointDependingFeature, ExtractedStructure e, MethodExecution m, ArrayList<TracePoint>bottomTracePoints ) {
		// TODO Auto-generated method stub
		
		TracePoint lastTp = m.getExitPoint();
		do{
			if(e != null){
				//collectionAdd�̃V�������N�����s��
				Set<Reference> extractedReferences = summarizeExtractedRef(e);				
				//���o���ꂽ�Q�Ƃ����L�\���ɒǉ��C�f���^���o�Ώۂ��̔���
				for(Reference ref : extractedReferences) {
					addToExtractedSharedStructure(ref, e, s, lastTp, startingTimeDependedFeature, endTimeDependedFeature, startingTimeDependingFeature);
				}
			}
			
			if(!stack.empty()) {
				Reference ref = stack.pop();
				lastTp = refToLastTp.get(ref);
				e = s.extract(ref, lastTp, aliasTracker);
				if(e != null) {
					bottomTracePoints.add(e.getRelatedTracePoint());
				}
				System.out.println("----------------------------------");
			}else {
				break;
			}
		}while(true);

		printResult(bottomTracePoints);//�o��
	}
	
	
	/*ref�ɑ΂��ăf���^�𒊏o���邩�̔���ƁCextractedSharedStructure�ւ̒ǉ�*/
	public void addToExtractedSharedStructure(Reference ref, ExtractedStructure e, DeltaExtractorJSON s, TracePoint lastTp,
			long startingTimeDependedFeature, long endTimeDependedFeature, long startingTimeDependingFeature) {
		long relatedTime = e.getRelatedTracePoint().getStatement().getTimeStamp();
		if(relatedTime < startingTimeDependingFeature) {
			//�ˑ����@�\�Ŏg���Ă��Ȃ��Q�Ƃɑ΂���f���^�@�ˁ@����ȏ�ǐՂ��Ȃ�
			extractedSharedStructure.add(ref);
			return;
		}
		if(ref.getSrcObjectId().equals("0")) {
			extractedSharedStructure.add(ref);
			return;
		}
		
		TracePoint tp;			
		do {
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
				break;
			}
			long t = tp.getStatement().getTimeStamp();
			if(t < startingTimeDependedFeature) {
				break;
			}else if((startingTimeDependedFeature <= t && t < endTimeDependedFeature)
					||  startingTimeDependingFeature< t ) {
					if(!extractedSharedStructure.contains(ref) && !stack.contains(ref)) {
						stack.add(ref);
						refToLastTp.put(ref, lastTp);
					}
					break;
			}
			if(lastTp.equals(tp)) {
				break;
			}
			lastTp = tp;
		}while(tp != null);
		
		extractedSharedStructure.add(ref);
	}
	
	/*src�T�C�h��ArrayList��dst�T�C�h��ArryaList��HashSet�ɂ܂Ƃ߂ĕԂ�*/
	public Set<Reference> summarizeExtractedRef(ExtractedStructure e){
		Set<Reference> refSet =  new HashSet<Reference>();
		refSet.addAll(shrink(e.getDelta().getSrcSide()));
		refSet.addAll(shrink(e.getDelta().getDstSide()));
		return refSet;
	}
	
	/*Java�W���R���N�V�����^�̏ꍇ�C�C�e���[�^������镔�����ȗ�����*/
	public Set<Reference> shrink(ArrayList<Reference> refs){
		Set<Reference> shrinkedRefs = new HashSet<Reference>();
		
		//�n�_�ȊO�̃I�u�W�F�N�g��ID�𒲂ׂ�
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
		
		//collection���z����Q�Ƃ��Ă��鎞 jedit ArrayList(selection) -> [Lorg.gjt.sp.jedit.textarea.Selection;
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
		return shrinkedRefs;
	}
	
	public void printResult(ArrayList<TracePoint>bottomTracePoints) {
		System.out.println("extractedSharedStructure");
		for(Reference ref : extractedSharedStructure) {
			System.out.println(ref.getSrcClassName() + "(" + ref.getSrcObjectId() + ") -> " + ref.getDstClassName() + "("  + ref.getDstObjectId() +")" );
		}
		
		System.out.println("----------------------------------");
		System.out.println("extractedLineNo");

		for(Alias alias : aliasTracker.getAliasList()) {
			if(alias.getAliasType().equals(AliasType.FORMAL_PARAMETER)) {
				continue;
			}
			int lineNo = alias.getLineNo();
			String methodSignature = alias.getMethodExecution().getSignature();
			
			if(extractedLines.containsKey(methodSignature)) {
				extractedLines.get(methodSignature).add(lineNo);
			}else {
				HashSet<Integer> set = new HashSet<Integer>();
				set.add(lineNo);
				extractedLines.put(methodSignature, set);
			}
		}
		
		for(TracePoint tp : bottomTracePoints) {
			int lineNo = tp.getStatement().getLineNo();
			String methodSignature = tp.getMethodExecution().getSignature();
			if(extractedLines.containsKey(methodSignature)) {
				extractedLines.get(methodSignature).add(lineNo);
			}else {
				HashSet<Integer> set = new HashSet<Integer>();
				set.add(lineNo);
				extractedLines.put(methodSignature, set);
			}
		}

		for(String className : extractedLines.keySet()) {
			System.out.println();
			System.out.println(className);
			for(int lineNo : extractedLines.get(className)) {
				System.out.println(lineNo);
			}
		}
	}
	
}
