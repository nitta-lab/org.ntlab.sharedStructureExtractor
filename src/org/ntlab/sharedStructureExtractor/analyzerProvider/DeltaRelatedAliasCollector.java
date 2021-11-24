package org.ntlab.sharedStructureExtractor.analyzerProvider;

import java.util.ArrayList;
import java.util.List;

public class DeltaRelatedAliasCollector implements IAliasTracker {
	private List<Alias> srcSideRelatedAliases = new ArrayList<>();
	private List<Alias> dstSideRelatedAliases = new ArrayList<>();
	private List<String> srcSideIdList = new ArrayList<>();
	private List<String> dstSideIdList = new ArrayList<>();
	private List<Alias> relatedAliases = new ArrayList<>();
	public static final String SRC_SIDE = "SrcSide";
	public static final String DST_SIDE = "DstSide";
	
	public DeltaRelatedAliasCollector(String srcId, String dstId) {
		srcSideIdList.add(srcId);
		dstSideIdList.add(dstId);
	}
	
	@Override
	public void addAlias(Alias alias) {
		if (alias == null) return;
		relatedAliases.add(alias);
		String objId = alias.getObjectId();
		String srcOrDst = "";
		if (alias instanceof DeltaAlias) {
			boolean isSrcSide = ((DeltaAlias)alias).isSrcSide();
			if (isSrcSide) {
				srcSideRelatedAliases.add(alias);
				srcOrDst = "Src ";
			} else {
				dstSideRelatedAliases.add(alias);
				srcOrDst = "Dst ";
			}
		} else {
			if (srcSideIdList.contains(objId) && !(dstSideIdList.contains(objId))) {
				if (alias.getAliasType().equals(Alias.AliasType.ACTUAL_ARGUMENT)) {
					Alias formalParameterAlias = srcSideRelatedAliases.get(srcSideRelatedAliases.size() - 1);
					alias.setIndex(formalParameterAlias.getIndex());
				}
				srcSideRelatedAliases.add(alias);
				srcOrDst = "Src ";
			} else if (dstSideIdList.contains(objId) && !(srcSideIdList.contains(objId))) {
				if (alias.getAliasType().equals(Alias.AliasType.ACTUAL_ARGUMENT)) {
					Alias formalParameterAlias = dstSideRelatedAliases.get(dstSideRelatedAliases.size() - 1);
					alias.setIndex(formalParameterAlias.getIndex());
				}
				dstSideRelatedAliases.add(alias);
				srcOrDst = "Dst ";
			} else if (srcSideIdList.contains(objId) && dstSideIdList.contains(objId)) {			
				boolean hasSrcSide = false;
				for (Alias ra : srcSideRelatedAliases) {
					if (ra.getObjectId().equals(objId)) {
						hasSrcSide = true;
						break;
					}
				}
				if (!hasSrcSide) {
					srcSideRelatedAliases.add(alias);
					srcOrDst = "Src ";
				} else {
					dstSideRelatedAliases.add(alias);
					srcOrDst = "Dst ";
				}
			}
		}
		try {
			System.out.println(srcOrDst + alias.getAliasType() + ": 			" + alias.getMethodSignature() + " line" + alias.getLineNo() + "		index" + alias.getIndex());
		} catch (Exception e) {
			System.out.println(srcOrDst + alias.getAliasType() + ": 			" + alias.getMethodSignature());				
		}
	}

	@Override
	public void changeTrackingObject(String from, String to, boolean isSrcSide) {
		if (isSrcSide) {
			srcSideIdList.add(to);
		} else {
			dstSideIdList.add(to);
		}
	}
	
	public List<Alias> getAliasList() {
		return relatedAliases;
	}
	
	public List<Alias> getSrcSideRelatedAliases() {
		return srcSideRelatedAliases;
	}
	
	public List<Alias> getDstSideRelatedAliases() {
		return dstSideRelatedAliases;
	}
		
	public String resolveSideInTheDelta(Alias alias) {
		int index;
		index = srcSideRelatedAliases.indexOf(alias);
		if (index != -1) return SRC_SIDE;
		index = dstSideRelatedAliases.indexOf(alias);
		if (index != -1) return DST_SIDE;
		return "";
	}
}
