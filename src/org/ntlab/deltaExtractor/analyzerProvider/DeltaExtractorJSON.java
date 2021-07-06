package org.ntlab.deltaExtractor.analyzerProvider;
 
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayCreate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
 
/**
 * The latest version of the delta extraction algorithm (for new trace files collected by Javassist). 
 * 
 * @author Nitta
 *
 */
public class DeltaExtractorJSON extends DeltaExtractor {
	public DeltaExtractorJSON(String traceFile) {
		super(new TraceJSON(traceFile));
	}
 
	public DeltaExtractorJSON(TraceJSON trace) {
		super(trace);
	}
	
	/**
	 * A part of the delta extraction algorithm to traverse caller methods (mutual recursion with calleeSearch). 
	 * @param trace a trace to analyze
	 * @param tracePoint execution point to start the traverse
	 * @param objList list of the tracking objects
	 * @param childMethodExecution method execution that has been traversed previously (method execution that was called by this method execution)
	 * @param aliasCollector collector of the aliases that are traversed in this delta extraction
	 * @return the coordinator method execution
	 */
	protected MethodExecution callerSearch(Trace trace, TracePoint tracePoint, ArrayList<String> objList, MethodExecution childMethodExecution, IAliasTracker aliasCollector) {
		MethodExecution methodExecution = tracePoint.getMethodExecution();
		methodExecution.setAugmentation(new DeltaAugmentationInfo());
		eStructure.createParent(methodExecution);
		String thisObjectId = methodExecution.getThisObjId();
		ArrayList<String> removeList = new ArrayList<String>();		// List of object to remove in objList.
		ArrayList<String> creationList = new ArrayList<String>();	// List of objects created in this method execution.
		int existsInFields = 0;			// The number of objects that are originated from fields in this method execution. 
		boolean isTrackingThis = false;	// Whether a tracking object is originated from 'this' object within a method execution called by this method execution.
		boolean isSrcSide = true;		// Which side the referring side or the referred side this object is reached by object tracking.
		ObjectReference thisObj = new ObjectReference(thisObjectId, methodExecution.getThisClassName(), 
				Trace.getDeclaringType(methodExecution.getSignature(), methodExecution.isConstructor()), Trace.getDeclaringType(methodExecution.getCallerSideSignature(), methodExecution.isConstructor()));
		
		HashMap<String, DeltaAlias>  srcAliasList = new HashMap<>();
		HashMap<String, DeltaAlias>  dstAliasList = new HashMap<>();
				
		if (childMethodExecution == null) {
			// At the beginning of the traverse, this object is temporarily removed and it will come back when the caller is traversed.
			removeList.add(thisObjectId);		// This object will be temporarily removed later.
			isTrackingThis = true;				// It will come back before the caller is traversed.
		}
		
		if (childMethodExecution != null && objList.contains(childMethodExecution.getThisObjId())) {
			// A tracking object is originated from 'this' object within a method execution called by this method execution.
			if (thisObjectId.equals(childMethodExecution.getThisObjId())) {
				// If the call is inner-object, then this object is temporarily removed and it will come back when the caller is traversed.
				removeList.add(thisObjectId);		// This object will be temporarily removed later.
				isTrackingThis = true;				// It will come back before the caller is traversed.
				// Case of inner-object call [case 1]
				aliasCollector.addAlias(new Alias(Alias.AliasType.RECEIVER, 0, childMethodExecution.getThisObjId(), tracePoint.duplicate()));
			} else if (!childMethodExecution.isConstructor()) {
				// Case of inter-object and non-constructor call [case 2]
				aliasCollector.addAlias(new Alias(Alias.AliasType.RECEIVER, 0, childMethodExecution.getThisObjId(), tracePoint.duplicate()));
			}
		}
		
		if (childMethodExecution != null) {
			for (String objId : objList) {
				if (!objId.equals(childMethodExecution.getThisObjId())) {
					aliasCollector.addAlias(new Alias(Alias.AliasType.ACTUAL_ARGUMENT, -1, objId, tracePoint.duplicate())); // argIndex is unknown.
				}
			}
		}
		
		if (childMethodExecution != null && childMethodExecution.isConstructor()) {
			// If the method execution called by this method execution is a constructor.
			int newIndex = objList.indexOf(childMethodExecution.getThisObjId());
			if (newIndex != -1) {
				// Regard the call to the constructor as a field access.
				removeList.add(childMethodExecution.getThisObjId());
				existsInFields++;
				removeList.add(thisObjectId);		// This object will be temporarily removed later.
				if (!thisObjectId.equals(childMethodExecution.getThisObjId())) {
					//  Case of inter-object and constructor call [case 3]
					if (childMethodExecution.getThisObjId().equals(srcObject.getId())) {
						srcAliasList.put(childMethodExecution.getThisObjId(), new DeltaAlias(Alias.AliasType.RECEIVER, 0, childMethodExecution.getThisObjId(), tracePoint.duplicate(), true));
					} else if (childMethodExecution.getThisObjId().equals(dstObject.getId())) {
						dstAliasList.put(childMethodExecution.getThisObjId(), new DeltaAlias(Alias.AliasType.RECEIVER, 0, childMethodExecution.getThisObjId(), tracePoint.duplicate(), false));
					}
				}
			}
		}
		
		if (childMethodExecution != null && Trace.getMethodName(childMethodExecution.getSignature()).startsWith("access$")) {
			// If the call is to a method of the enclosing instance. 
			String enclosingObj = childMethodExecution.getArguments().get(0).getId();	// Enclosing instance is passed as the first argument.
			int encIndex = objList.indexOf(enclosingObj);
			if (encIndex != -1) {
				// Replace the enclosing object with this object, and regard it as a field.
				removeList.add(enclosingObj);
				existsInFields++;
				removeList.add(thisObjectId);		// This object will be temporarily removed later.
			}
		}
 
		// The main loop of callerSearch. The executions of statements in this method execution are traversed backward.
		// The calleeSearch method can be recursively called if a tracking object is returned from a called method.
		while (tracePoint.stepBackOver()) {
			Statement statement = tracePoint.getStatement();
			if (statement instanceof FieldAccess) {
				// The statement is a field access of this or another object.
				FieldAccess fs = (FieldAccess)statement;
				String refObjectId = fs.getValueObjId();
				int index = objList.indexOf(refObjectId);
				if (index != -1) {
					String ownerObjectId = fs.getContainerObjId();
										
					if (ownerObjectId.equals(thisObjectId)) {
						// If a tracking object is obtained from a field of this object.
						if (Collections.frequency(objList, refObjectId) > Collections.frequency(removeList, refObjectId)) {
							// The nearest field access is prioritized.
							removeList.add(refObjectId);
							removeList.add(thisObjectId);		// This object will be temporarily removed later.
							if (refObjectId.equals(srcObject.getId())) {
								srcAliasList.put(refObjectId, new DeltaAlias(Alias.AliasType.FIELD, 0, refObjectId, tracePoint.duplicate(), true));
							} else if (refObjectId.equals(dstObject.getId())) {
								dstAliasList.put(refObjectId, new DeltaAlias(Alias.AliasType.FIELD, 0, refObjectId, tracePoint.duplicate(), false));
							}
							existsInFields++;					// To consider the case when a field is accessed after it is updated within the same method execution.
						}
					} else {
						// If a tracking object is obtained from a field of another object.
						boolean isSrcSideChanged = false;
						if (refObjectId.equals(srcObject.getId())) {
							eStructure.addSrcSide(new Reference(ownerObjectId, refObjectId,
									fs.getContainerClassName(), srcObject.getActualType()));
							srcObject = new ObjectReference(ownerObjectId, fs.getContainerClassName());
							isSrcSideChanged = true;
						} else if(refObjectId.equals(dstObject.getId())) {
							eStructure.addDstSide(new Reference(ownerObjectId, refObjectId,
									fs.getContainerClassName(), dstObject.getActualType()));
							dstObject = new ObjectReference(ownerObjectId, fs.getContainerClassName());
							isSrcSideChanged = false;
						}
						objList.set(index, ownerObjectId);
						aliasCollector.addAlias(new Alias(Alias.AliasType.FIELD, 0, refObjectId, tracePoint.duplicate()));
						aliasCollector.changeTrackingObject(refObjectId, ownerObjectId, isSrcSideChanged); // Change the tracking object from the field value to its container.
						aliasCollector.addAlias(new Alias(Alias.AliasType.CONTAINER, 0, ownerObjectId, tracePoint.duplicate()));
					}
				}
			} else if (statement instanceof ArrayAccess) {
				// The statement is an array access.
				ArrayAccess aa = (ArrayAccess)statement;
				String elementObjectId = aa.getValueObjectId();
				int index = objList.indexOf(elementObjectId);
				if (index != -1) {
					// If a tracking object corresponds to an element of an array object.
					boolean isSrcSideChanged = false;
					String arrayObjectId = aa.getArrayObjectId();
					if (elementObjectId.equals(srcObject.getId())) {
						eStructure.addSrcSide(new Reference(arrayObjectId, elementObjectId,
								aa.getArrayClassName(), srcObject.getActualType()));
						srcObject = new ObjectReference(arrayObjectId, aa.getArrayClassName());
						isSrcSideChanged = true;
					} else if(elementObjectId.equals(dstObject.getId())) {
						eStructure.addDstSide(new Reference(arrayObjectId, elementObjectId,
								aa.getArrayClassName(), dstObject.getActualType()));
						dstObject = new ObjectReference(arrayObjectId, aa.getArrayClassName());
						isSrcSideChanged = false;
					}
					objList.set(index, arrayObjectId);
					aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY_ELEMENT, aa.getIndex(), elementObjectId, tracePoint.duplicate()));
					aliasCollector.changeTrackingObject(elementObjectId, arrayObjectId, isSrcSideChanged); // Change the tracking object from the array element to the array object.
					aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY, 0, arrayObjectId, tracePoint.duplicate()));
				}
			} else if (statement instanceof ArrayCreate) {
				// The statement is a create of an array object.
				ArrayCreate ac = (ArrayCreate)statement;
				String arrayObjectId = ac.getArrayObjectId();
				int index = objList.indexOf(arrayObjectId);
				if (index != -1) {
					// An array create is regarded as a field access.
					creationList.add(arrayObjectId);
					removeList.add(arrayObjectId);
					existsInFields++;
					removeList.add(thisObjectId);		// This object will be temporarily removed later.
					if (arrayObjectId.equals(srcObject.getId())) {
						srcAliasList.put(arrayObjectId, new DeltaAlias(Alias.AliasType.ARRAY_CREATE, 0, arrayObjectId, tracePoint.duplicate(), true));
					} else if (arrayObjectId.equals(dstObject.getId())) {
						dstAliasList.put(arrayObjectId, new DeltaAlias(Alias.AliasType.ARRAY_CREATE, 0, arrayObjectId, tracePoint.duplicate(), false));
					}
				}
			} else if (statement instanceof MethodInvocation) {
				// The statement is a method invocation.
				MethodExecution prevChildMethodExecution = ((MethodInvocation)statement).getCalledMethodExecution();
				if (!prevChildMethodExecution.equals(childMethodExecution)) {
					ObjectReference ret = prevChildMethodExecution.getReturnValue();
					if (ret != null) {
						int retIndex = -1;
						retIndex = objList.indexOf(ret.getId());
						if (retIndex != -1) {
							// If the return value is the origin of a tracking object.
							prevChildMethodExecution.setAugmentation(new DeltaAugmentationInfo());						
							if (prevChildMethodExecution.isConstructor()) {
								// If the called method is a constructor, than the call to the constructor is regarded as a field access.
								String newObjId = ret.getId();
								creationList.add(newObjId);
								removeList.add(newObjId);
								existsInFields++;
								removeList.add(thisObjectId);		// This object will be temporarily removed later.
								((DeltaAugmentationInfo)prevChildMethodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(newObjId));		// A tracking object
								((DeltaAugmentationInfo)prevChildMethodExecution.getAugmentation()).setSetterSide(false);	// Similar to referred side invocations.
								if (newObjId.equals(srcObject.getId())) {
									srcAliasList.put(newObjId, new DeltaAlias(Alias.AliasType.CONSTRACTOR_INVOCATION, 0, newObjId, tracePoint.duplicate(), true));
								} else if (newObjId.equals(dstObject.getId())) {
									dstAliasList.put(newObjId, new DeltaAlias(Alias.AliasType.CONSTRACTOR_INVOCATION, 0, newObjId, tracePoint.duplicate(), false));
								}
								continue;
							}
							String retObj = objList.get(retIndex);
							if (retObj.equals(srcObject.getId())) {
								isSrcSide = true;
							} else if (retObj.equals(dstObject.getId())) {
								isSrcSide = false;
							}
							aliasCollector.addAlias(new Alias(Alias.AliasType.METHOD_INVOCATION, 0, retObj, tracePoint.duplicate()));
							if (removeList.contains(retObj)) {
								// After conjecturing that the origin of a tracking object is a field, the origin turned out to be a return value, and cancel the previous conjecture.
								removeList.remove(retObj);
								existsInFields--;
								if (existsInFields == 0) {
									removeList.remove(thisObjectId);
								}
							}
							((DeltaAugmentationInfo)prevChildMethodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(retObj));		// A tracking object
							TracePoint prevChildTracePoint = tracePoint.duplicate();
							prevChildTracePoint.stepBackNoReturn();
							calleeSearch(trace, prevChildTracePoint, objList, prevChildMethodExecution.isStatic(), retIndex, aliasCollector);	// Recursively call to traverse the called method execution.
							if (objList.get(retIndex) != null && objList.get(retIndex).equals(prevChildMethodExecution.getThisObjId())) {
								if ( thisObjectId.equals(prevChildMethodExecution.getThisObjId())) {
									// A tracking object turned out to be originated from a field in the called method execution.
									removeList.add(thisObjectId);		// This object will be temporarily removed later.
									isTrackingThis = true;				// It will come back before the caller is traversed.
								}
								if (isSrcSide) {
									aliasCollector.addAlias(new DeltaAlias(Alias.AliasType.RECEIVER, 0, objList.get(retIndex), tracePoint.duplicate(), true));
								} else {
									aliasCollector.addAlias(new DeltaAlias(Alias.AliasType.RECEIVER, 0, objList.get(retIndex), tracePoint.duplicate(), false));
								}
							}
							if (isLost) {
								checkList.add(objList.get(retIndex));
								isLost = false;
							}
						}
					}
				}
			}
		}
		// --- At this point, the tracePoint is referring to the calling method execution. ---
		
		// For an invocation of a collection type.
		if (methodExecution.isCollectionType()) {
			objList.add(thisObjectId);
		}
 
		// Get the actual arguments of this method execution.
		ArrayList<ObjectReference> arguments = methodExecution.getArguments();
		
		// In the following, we consider the case that it is unclear which expression a formal parameter or a field is the origin of a tracking object when they have the same object ID.
		Reference r;
		for (int i = 0; i < removeList.size(); i++) {
			String removeId = removeList.get(i);
			if (arguments.contains(new ObjectReference(removeId))) { 
				removeList.remove(removeId);	// We conjecture that the origin is a formal parameter.
			} else if(objList.contains(removeId)) {
				// The origin could be found only in a field or the tracking object was crested in the method execution.
				objList.remove(removeId);		// Remove the tracking object temporarily.
				if (!removeId.equals(thisObjectId)) {
					// The reference from this to removeId constitutes the delta.
					if (srcAliasList.get(removeId) != null) {
						r = new Reference(thisObj, srcObject);
						r.setCreation(creationList.contains(removeId));		// Whether removeId object was created in this method execution or not?
						eStructure.addSrcSide(r);
						srcObject = thisObj;
						isSrcSide = true;
						aliasCollector.addAlias(srcAliasList.get(removeId));
						aliasCollector.changeTrackingObject(removeId, thisObjectId, isSrcSide);
						aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, srcAliasList.get(removeId).getOccurrencePoint()));
						srcAliasList.remove(removeId);
					} else if (dstAliasList.get(removeId) != null) {
						r = new Reference(thisObj, dstObject);
						r.setCreation(creationList.contains(removeId));		// Whether removeId object was created in this method execution or not?
						eStructure.addDstSide(r);
						dstObject = thisObj;
						isSrcSide = false;
						aliasCollector.addAlias(dstAliasList.get(removeId));
						aliasCollector.changeTrackingObject(removeId, thisObjectId, isSrcSide);
						aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, dstAliasList.get(removeId).getOccurrencePoint()));
						dstAliasList.remove(removeId);
					}
				}
			}
		}
		// --- At this point, this object has been removed from objList even if it is a tracking object. ---
		
		// Search the origin of a tracking object in the formal parameters.
		boolean existsInAnArgument = false;
		for (int i = 0; i < objList.size(); i++) {
			String objectId = objList.get(i);
			if (objectId != null) {
				ObjectReference trackingObj = new ObjectReference(objectId);
				if (arguments.contains(trackingObj)) {
					// The origin was a formal parameter.
					existsInAnArgument = true;
					((DeltaAugmentationInfo)methodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(objectId));
					aliasCollector.addAlias(new Alias(Alias.AliasType.FORMAL_PARAMETER, arguments.indexOf(trackingObj), trackingObj.getId(), methodExecution.getEntryPoint()));					
				} else {
					// The origin could not be found.
					boolean isSrcSide2 = true;
					trackingObj = null;
					if (objectId.equals(srcObject.getId())) {
						isSrcSide2 = true;
						trackingObj = srcObject;
					} else if (objectId.equals(dstObject.getId())) {
						isSrcSide2 = false;
						trackingObj = dstObject;
					}
				}
			}
		}
		if (existsInAnArgument) {
			// At least one origin was found in the formal parameters.
			if (existsInFields > 0 || isTrackingThis) {
				// If this object is tracking.
				if (!Trace.isNull(thisObjectId)) {
					objList.add(thisObjectId);	// This object comes back to traverse the caller.										
				} else {
					objList.add(null);			// If it is a call to a static method.
				}				
			}
			if (tracePoint.isValid()) {
				finalCount = 0;
				return callerSearch(trace, tracePoint, objList, methodExecution, aliasCollector);		// Recursively call to traverse the calling method execution.				
			}
		}
		
		for (int i = 0; i < objList.size(); i++) {
			objList.remove(null);
		}
		if (objList.isEmpty()) {
			((DeltaAugmentationInfo)methodExecution.getAugmentation()).setCoodinator(true);
		} else {
			// The origin could not be resolved.
			if (!methodExecution.isStatic()) {
				finalCount++;
				if (finalCount <= LOST_DECISION_EXTENSION) {
					// To seek the origin in a final local variable of the calling method execution.
					if (tracePoint.isValid()) { 
						MethodExecution c = callerSearch(trace, tracePoint, objList, methodExecution, aliasCollector);		// Recursively call to traverse the calling method execution.	
						if (((DeltaAugmentationInfo)c.getAugmentation()).isCoodinator()) {
							methodExecution = c;		// The coordinator has been found finally.
						}
					}
				} else if (thisObj.getActualType().contains("$")) {
					// If this object is an instance of an anonymous class, then we conjecture the origin is a final local variable of the enclosing method.
					for (int i = objList.size() - 1; i >= 0; i--) {
						String objectId = objList.get(i);
						if (objectId != null) {
							ObjectReference trackingObj = new ObjectReference(objectId);
							boolean isSrcSide2 = true;
							trackingObj = null;
							if (objectId.equals(srcObject.getId())) {
								isSrcSide2 = true;
								trackingObj = srcObject;
							} else if (objectId.equals(dstObject.getId())) {
								isSrcSide2 = false;
								trackingObj = dstObject;
							}
							if (trackingObj != null) {
								r = new Reference(thisObjectId, trackingObj.getId(),
										methodExecution.getThisClassName(), trackingObj.getActualType());
								r.setFinalLocal(true);
								if (isSrcSide2) {
									eStructure.addSrcSide(r);
									srcObject = thisObj;
									isSrcSide = true;
								} else {
									eStructure.addDstSide(r);
									dstObject = thisObj;
									isSrcSide = false;
								}
								existsInFields++;
								objList.remove(objectId);
							}
						}
					}
				}
			}
			((DeltaAugmentationInfo)methodExecution.getAugmentation()).setCoodinator(false);
		}
		finalCount = 0;
		return methodExecution;
	}
 
	/**
	 * A part of the delta extraction algorithm to traverse called methods (recursive). 
	 * @param trace a trace to analyze
	 * @param tracePoint execution point to start the traverse
	 * @param objList list of the tracking objects
	 * @param isStatic whether this method is static or not?
	 * @param index which object in objList is to traverse in this method?
	 * @param aliasCollector collector of the aliases that are traversed in this delta extraction
	 */
	protected void calleeSearch(Trace trace, TracePoint tracePoint, ArrayList<String> objList, Boolean isStatic, int index, IAliasTracker aliasCollector) {
		MethodExecution methodExecution = tracePoint.getMethodExecution();
		Boolean isResolved = false;
		String objectId = objList.get(index);		// The object to track in calleeSearch() is just one. No object in objList other than the index-th one can be changed.
		String thisObjectId = methodExecution.getThisObjId();
		ObjectReference thisObj = new ObjectReference(thisObjectId, methodExecution.getThisClassName(), 
				Trace.getDeclaringType(methodExecution.getSignature(), methodExecution.isConstructor()), 
				Trace.getDeclaringType(methodExecution.getCallerSideSignature(), methodExecution.isConstructor()));
		
		((DeltaAugmentationInfo)methodExecution.getAugmentation()).setSetterSide(false);		// Basically, the tracking object must be referred side, but it may not be always true.
		ArrayList<ObjectReference> arguments = methodExecution.getArguments();
		ObjectReference trackingObj = null;
 
		aliasCollector.addAlias(new Alias(Alias.AliasType.RETURN_VALUE, 0, objectId, tracePoint.duplicate()));
		// The value of objectId may be null if a static method was traversed.
		if (objectId != null) {
			String returnType = Trace.getReturnType(methodExecution.getSignature());
			if (objectId.equals(srcObject.getId())) {
				trackingObj = srcObject;
				trackingObj.setCalleeType(returnType);
			} else if(objectId.equals(dstObject.getId())) {
				trackingObj = dstObject;
				trackingObj.setCalleeType(returnType);
			} else {
				trackingObj = new ObjectReference(objectId, null, returnType);
			}
			
			Reference r;
			// The main loop of calleeSearch. The executions of statements in this method execution are traversed backward.
			// The calleeSearch method can be recursively called if a tracking object is returned from a called method.
			do {
				if (!tracePoint.isValid()) break;
				Statement statement = tracePoint.getStatement();
				if (statement instanceof FieldAccess) {
					// The statement is a field access of this or another object.
					FieldAccess fs = (FieldAccess)statement;
					if (objectId != null && objectId.equals(fs.getValueObjId())) {						
						String ownerObjectId = fs.getContainerObjId();
						if (ownerObjectId.equals(thisObjectId)) {
							// If the tracking object is obtained from a field of this object.
							boolean isSrcSideChanged = false;
							if (objectId.equals(srcObject.getId())) {
								eStructure.addSrcSide(new Reference(thisObj, srcObject));
								srcObject = thisObj;
								trackingObj = srcObject;
								isSrcSideChanged = true;
							} else if(objectId.equals(dstObject.getId())) {
								eStructure.addDstSide(new Reference(thisObj, dstObject));
								dstObject = thisObj;
								trackingObj = dstObject;
								isSrcSideChanged = false;
							}
							aliasCollector.addAlias(new Alias(Alias.AliasType.FIELD, 0, objectId, tracePoint.duplicate()));
							aliasCollector.changeTrackingObject(objectId, ownerObjectId, isSrcSideChanged);
							aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, ownerObjectId, tracePoint.duplicate()));
							if (Trace.isNull(thisObjectId)) objectId = null;	// If the field is static.
							else objectId = thisObjectId;
							objList.set(index, objectId);
						} else {
							// If the tracking object is obtained from a field of another object.
							boolean isSrcSideChanged = false;
							if (objectId.equals(srcObject.getId())) {
								eStructure.addSrcSide(new Reference(ownerObjectId, objectId,
										fs.getContainerClassName(), srcObject.getActualType()));
								srcObject = new ObjectReference(ownerObjectId, fs.getContainerClassName());
								trackingObj = srcObject;
								isSrcSideChanged = true;
							} else if(objectId.equals(dstObject.getId())) {
								eStructure.addDstSide(new Reference(ownerObjectId, objectId,
										fs.getContainerClassName(), dstObject.getActualType()));
								dstObject = new ObjectReference(ownerObjectId, fs.getContainerClassName());
								trackingObj = dstObject;
								isSrcSideChanged = false;
							}
							aliasCollector.addAlias(new Alias(Alias.AliasType.FIELD, 0, objectId, tracePoint.duplicate()));
							aliasCollector.changeTrackingObject(objectId, ownerObjectId, isSrcSideChanged);
							aliasCollector.addAlias(new Alias(Alias.AliasType.CONTAINER, 0, ownerObjectId, tracePoint.duplicate()));
							if (Trace.isNull(ownerObjectId)) objectId = null;	// If the field is static.
							else objectId = ownerObjectId;
							objList.set(index, objectId);
						}
						isResolved = true;
					}
				} else if (statement instanceof ArrayAccess) {
					// The statement is an array access.
					ArrayAccess aa = (ArrayAccess)statement;
					if (objectId != null && objectId.equals(aa.getValueObjectId())) {
						// If the tracking object corresponds to an element of an array object.
						boolean isSrcSideChanged = false;
						String arrayObjectId = aa.getArrayObjectId();
						if (objectId.equals(srcObject.getId())) {
							eStructure.addSrcSide(new Reference(arrayObjectId, objectId,
									aa.getArrayClassName(), srcObject.getActualType()));
							srcObject = new ObjectReference(arrayObjectId, aa.getArrayClassName());
							trackingObj = srcObject;
							isSrcSideChanged = true;
						} else if(objectId.equals(dstObject.getId())) {
							eStructure.addDstSide(new Reference(arrayObjectId, objectId,
									aa.getArrayClassName(), dstObject.getActualType()));
							dstObject = new ObjectReference(arrayObjectId, aa.getArrayClassName());
							trackingObj = dstObject;
							isSrcSideChanged = false;
						}
						aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY_ELEMENT, aa.getIndex(), objectId, tracePoint.duplicate()));
						aliasCollector.changeTrackingObject(objectId, arrayObjectId, isSrcSideChanged);
						aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY, 0, arrayObjectId, tracePoint.duplicate()));
						objectId = arrayObjectId;
						objList.set(index, objectId);
						isResolved = true;
					}
				} else if (statement instanceof ArrayCreate) {
					// The statement is a create of an array object.
					ArrayCreate ac = (ArrayCreate)statement;
					if (objectId != null && objectId.equals(ac.getArrayObjectId())) {
						boolean isSrcSideChanged = false;
						if (objectId.equals(srcObject.getId())) {
							eStructure.addSrcSide(new Reference(thisObj, srcObject));
							srcObject = thisObj;
							trackingObj = srcObject;
							isSrcSideChanged = true;
						} else if(objectId.equals(dstObject.getId())) {
							eStructure.addDstSide(new Reference(thisObj, dstObject));
							dstObject = thisObj;
							trackingObj = dstObject;
							isSrcSideChanged = false;
						}
						aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY_CREATE, 0, ac.getArrayObjectId(), tracePoint.duplicate()));
						aliasCollector.changeTrackingObject(ac.getArrayObjectId(), thisObjectId, isSrcSideChanged);
						aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, tracePoint.duplicate()));
						if (Trace.isNull(thisObjectId)) objectId = null;	// If this method is static.
						else objectId = thisObjectId;
						objList.set(index, objectId);
					}
				} else if (statement instanceof MethodInvocation) {
					// The statement is a method invocation.
					MethodExecution childMethodExecution = ((MethodInvocation)statement).getCalledMethodExecution();
					ObjectReference ret = childMethodExecution.getReturnValue();
					if (ret != null && objectId != null && objectId.equals(ret.getId())) {
						childMethodExecution.setAugmentation(new DeltaAugmentationInfo());
						((DeltaAugmentationInfo)childMethodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(objectId));
						TracePoint childTracePoint = tracePoint.duplicate();
						childTracePoint.stepBackNoReturn();
						if (!childMethodExecution.isConstructor()) {
							aliasCollector.addAlias(new Alias(Alias.AliasType.METHOD_INVOCATION, 0, ret.getId(), tracePoint.duplicate()));
							calleeSearch(trace, childTracePoint, objList, childMethodExecution.isStatic(), index, aliasCollector);		// Recursively call to traverse the called method execution.	
						} else {
							aliasCollector.addAlias(new Alias(Alias.AliasType.CONSTRACTOR_INVOCATION, 0, ret.getId(), tracePoint.duplicate()));
						}
						if (childMethodExecution.isConstructor()) {
							// If the called method is a constructor.
							if (objectId.equals(srcObject.getId())) {
								r = new Reference(thisObj, srcObject);
								r.setCreation(true);
								eStructure.addSrcSide(r);
								srcObject = thisObj;
								trackingObj = srcObject;
								aliasCollector.changeTrackingObject(objectId, thisObjectId, true);
								aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, tracePoint.duplicate()));
							} else if (objectId.equals(dstObject.getId())) {
								r = new Reference(thisObj, dstObject);
								r.setCreation(true);
								eStructure.addDstSide(r);
								dstObject = thisObj;
								trackingObj = dstObject;
								aliasCollector.changeTrackingObject(objectId, thisObjectId, false);
								aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, tracePoint.duplicate()));
							}
							if (Trace.isNull(thisObjectId)) objectId = null;	// If this method is static.
							else objectId = thisObjectId;
							objList.set(index, objectId);
							isResolved = true;
							isLost = false;
							continue;
						}
						objectId = objList.get(index);
						if (objectId == null) {
							// If the value is returned from a static method.
							trackingObj = null;
							isResolved = true;
						} else if (objectId.equals(srcObject.getId())) {
							trackingObj = srcObject;
						} else if (objectId.equals(dstObject.getId())) {
							trackingObj = dstObject;
						}
						if (isLost) {
							checkList.add(objList.get(index));
							isLost = false;
						}
						if (objectId != null) {
							if (childMethodExecution.getThisObjId().equals(objectId)) {
								aliasCollector.addAlias(new Alias(Alias.AliasType.RECEIVER, 0, objectId, tracePoint.duplicate()));
							}
						}						
					}
				}
			} while (tracePoint.stepBackOver());
			
			// Search the actual arguments of this method execution.
			if (arguments.contains(new ObjectReference(objectId))) {
				((DeltaAugmentationInfo)methodExecution.getAugmentation()).setSetterSide(true);		// Perhaps necessity.
				isResolved = true;
				aliasCollector.addAlias(new Alias(Alias.AliasType.FORMAL_PARAMETER, arguments.indexOf(new ObjectReference(objectId)), objectId, methodExecution.getEntryPoint()));
			}
		}
		
		// For an invocation of a collection type.
		Reference r;
		if (methodExecution.isCollectionType()) {
			if (objectId != null) {
				if (methodExecution.getSignature().contains("Collections.unmodifiable") 
						|| methodExecution.getSignature().contains("Collections.checked") 
						|| methodExecution.getSignature().contains("Collections.synchronized") 
						|| methodExecution.getSignature().contains("Arrays.asList") 
						|| methodExecution.getSignature().contains("Arrays.copyOf")) {
					// If objectId is converted by this method execution, then its origin is the first actual argument of the method.
					if (arguments.size() > 0) {
						if (objectId.equals(srcObject.getId())) {
							r = new Reference(arguments.get(0), srcObject);
							r.setCollection(true);
							r.setCreation(true);		// The return value is assumed to be created within the method execution.
							eStructure.addSrcSide(r);
							srcObject = arguments.get(0);
							aliasCollector.changeTrackingObject(objectId, arguments.get(0).getId(), true);
							aliasCollector.addAlias(new Alias(Alias.AliasType.FORMAL_PARAMETER, 0, arguments.get(0).getId(), tracePoint.duplicate()));
						} else if(objectId.equals(dstObject.getId())) {
							r = new Reference(arguments.get(0), dstObject);
							r.setCollection(true);
							r.setCreation(true);		// The return value is assumed to be created within the method execution.
							eStructure.addDstSide(r);
							dstObject =arguments.get(0);
							aliasCollector.changeTrackingObject(objectId, arguments.get(0).getId(), false);
							aliasCollector.addAlias(new Alias(Alias.AliasType.FORMAL_PARAMETER, 0, arguments.get(0).getId(), tracePoint.duplicate()));
						}
					}
					objList.set(index, arguments.get(0).getId());
				} else {
					// If objectId is returned from this collection object, then it is assumed to be directly held in the object.
					if (objectId.equals(srcObject.getId())) {
						r = new Reference(thisObj, srcObject);
						r.setCollection(true);
						if (methodExecution.getSignature().contains(".iterator()")
								|| methodExecution.getSignature().contains(".listIterator()") 
								|| methodExecution.getSignature().contains(".entrySet()")
								|| methodExecution.getSignature().contains(".keySet()")
								|| methodExecution.getSignature().contains(".values()")) r.setCreation(true);		// Such an iterator object is assumed to be created in this method execution.
						eStructure.addSrcSide(r);
						srcObject = thisObj;
						aliasCollector.changeTrackingObject(objectId, thisObjectId, true);
						aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, tracePoint.duplicate()));
					} else if(objectId.equals(dstObject.getId())) {
						r = new Reference(thisObj, dstObject);
						r.setCollection(true);
						if (methodExecution.getSignature().contains(".iterator()")
								|| methodExecution.getSignature().contains(".listIterator()")
								|| methodExecution.getSignature().contains(".entrySet()")
								|| methodExecution.getSignature().contains(".keySet()")
								|| methodExecution.getSignature().contains(".values()")) r.setCreation(true);		// Such an iterator object is assumed to be created in this method execution.
						eStructure.addDstSide(r);
						dstObject =thisObj;
						aliasCollector.changeTrackingObject(objectId, thisObjectId, false);
						aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, tracePoint.duplicate()));
					}
					objList.set(index, methodExecution.getThisObjId());
				}
			}
			isResolved = true;		// May be needed.
		}
		
		if (objectId == null && isResolved && !isStatic) {	// If the return value from a static method is returned.
			objList.set(index, thisObjectId);	// To make this object be tracked.
			if (Trace.isNull(srcObject.getId())) {
				srcObject = thisObj;
			} else if (Trace.isNull(dstObject.getId())) {
				dstObject = thisObj;
			}
		}
		
		if (isStatic && !isResolved) {		// No more needed?
			objList.set(index, null);
		}
		if(!isStatic && !isResolved){
			isLost = true;					// Do nothing in calleeSearch() even if the origin is a final local variable of a caller method.
		}
	}
}