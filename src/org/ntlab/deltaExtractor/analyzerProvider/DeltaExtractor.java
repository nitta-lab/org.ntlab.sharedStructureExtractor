package org.ntlab.deltaExtractor.analyzerProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

/**
 * The original version of the delta extraction algorithm (for old trace files collected by AspectJ). 
 * A delta can be extracted by calling either one of DeltaExtractor#extract(...) methods.
 * 
 * @author Nitta
 *
 */
public class DeltaExtractor {
	protected static final int LOST_DECISION_EXTENSION = 0;		// Basically, the value should be 0. It had not been used together with the detection algorithm of final local variables.
	protected ArrayList<String> data = new ArrayList<String>();
	protected ArrayList<String> objList = new ArrayList<String>(2);
	protected ArrayList<String> methodList = new ArrayList<String>();
	protected ExtractedStructure eStructure = new ExtractedStructure();
	protected ObjectReference srcObject = null;
	protected ObjectReference dstObject = null;
	protected String returnValue;
	protected String threadNo;
	protected boolean isLost = false;
	protected ArrayList<String> checkList = new ArrayList<String>();
	protected Trace trace = null;
	protected int finalCount = 0;			// To detect final local variables.
	
	protected static final boolean DEBUG1 = true;
	protected static final boolean DEBUG2 = true;
	
	protected final IAliasTracker defaultAliasCollector = new IAliasTracker() {
		@Override
		public void changeTrackingObject(String from, String to, boolean isSrcSide) {
		}
		@Override
		public void addAlias(Alias alias) {
		}
		@Override
		public List<Alias> getAliasList() {
			return null;
		}
	};
	
	public DeltaExtractor(String traceFile) {
		trace = new Trace(traceFile);
	}
 
	public DeltaExtractor(Trace trace) {
		this.trace = trace;
	}
 
	/**
	 * A part of the delta extraction algorithm to traverse caller methods (mutual recursion with calleeSearch). 
	 * @param trace a trace to analyze
	 * @param tracePoint execution point to start the traverse
	 * @param objList list of the tracking objects
	 * @param childMethodExecution method execution that has been traversed previously
	 * @return the coordinator method execution
	 */
	@Deprecated
	protected MethodExecution callerSearch(Trace trace, TracePoint tracePoint, ArrayList<String> objList, MethodExecution childMethodExecution) {
		return callerSearch(trace, tracePoint, objList, childMethodExecution, defaultAliasCollector);
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
	@Deprecated
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
		ArrayList<ObjectReference> fieldArrays = new ArrayList<ObjectReference>();
		ArrayList<ObjectReference> fieldArrayElements = new ArrayList<ObjectReference>();
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
			String enclosingObj = childMethodExecution.getArguments().get(0).getId();	// The enclosing instance is passed as the first parameter.
			int encIndex = objList.indexOf(enclosingObj);
			if (encIndex != -1) {
				// Replace the enclosing object with this object, and regard it as a field.
				removeList.add(enclosingObj);
				existsInFields++;
				removeList.add(thisObjectId);		// This object will be temporarily removed later.
				if (enclosingObj.equals(srcObject.getId())) {
					srcAliasList.put(enclosingObj, new DeltaAlias(Alias.AliasType.FIELD, 0, enclosingObj, tracePoint.duplicate(), true));
				} else if (enclosingObj.equals(dstObject.getId())) {
					dstAliasList.put(enclosingObj, new DeltaAlias(Alias.AliasType.FIELD, 0, enclosingObj, tracePoint.duplicate(), false));
				}
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
						removeList.add(refObjectId);
						removeList.add(thisObjectId);		// This object will be temporarily removed later.
						if (refObjectId.equals(srcObject.getId())) {
							srcAliasList.put(refObjectId, new DeltaAlias(Alias.AliasType.FIELD, 0, refObjectId, tracePoint.duplicate(), true));
						} else if (refObjectId.equals(dstObject.getId())) {
							dstAliasList.put(refObjectId, new DeltaAlias(Alias.AliasType.FIELD, 0, refObjectId, tracePoint.duplicate(), false));
						}
						existsInFields++;					// To consider the case when a field is accessed after it is updated within the same method execution.
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
				} else {
					// A tracking object might be obtained as an array element accessed here.
					String refObjType = fs.getValueClassName();
					if (refObjType.startsWith("[L")) {
						// If the accessed field is an array object.
						ObjectReference trackingObj = null;
						if ((srcObject.getActualType() != null && refObjType.endsWith(srcObject.getActualType() + ";"))
								|| (srcObject.getCalleeType() != null && refObjType.endsWith(srcObject.getCalleeType() + ";"))
								|| (srcObject.getCallerType() != null && refObjType.endsWith(srcObject.getCallerType() + ";"))) {
							trackingObj = srcObject;
						} else if ((dstObject.getActualType() != null && refObjType.endsWith(dstObject.getActualType() + ";")) 
								|| (dstObject.getCalleeType() != null && refObjType.endsWith(dstObject.getCalleeType() + ";"))
								|| (dstObject.getCallerType() != null && refObjType.endsWith(dstObject.getCallerType() + ";"))) {
							trackingObj = dstObject;
						}
						if (trackingObj != null) {
							// If there is a tracking object whose type matches the type of the array element.
							String ownerObjectId = fs.getContainerObjId();
							if (ownerObjectId.equals(thisObjectId)) {
								fieldArrays.add(new ObjectReference(refObjectId, refObjType));
								fieldArrayElements.add(trackingObj);
								if (trackingObj.getId().equals(srcObject.getId())) {
									srcAliasList.put(trackingObj.getId(), new DeltaAlias(Alias.AliasType.ARRAY_ELEMENT, 0, trackingObj.getId(), tracePoint.duplicate(), true));
								} else if (trackingObj.getId().equals(dstObject.getId())) {
									dstAliasList.put(trackingObj.getId(), new DeltaAlias(Alias.AliasType.ARRAY_ELEMENT, 0, trackingObj.getId(), tracePoint.duplicate(), false));
								}
							}
						}
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
								((DeltaAugmentationInfo)prevChildMethodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(newObjId));	// A tracking object
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
								if (thisObjectId.equals(prevChildMethodExecution.getThisObjId())) {
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
						} else {
							// The origin of a tracking object may be an element of the array object that is returned from the called method execution.
							String retType = ret.getActualType();
							if (retType.startsWith("[L")) {
								// If the returned object is an array object.
								if ((srcObject.getActualType() != null && retType.endsWith(srcObject.getActualType() + ";")) 
										|| (srcObject.getCalleeType() != null && retType.endsWith(srcObject.getCalleeType() + ";"))
										|| (srcObject.getCallerType() != null && retType.endsWith(srcObject.getCallerType() + ";"))) {
									retType = srcObject.getActualType();
								} else if ((dstObject.getActualType() != null && retType.endsWith(dstObject.getActualType() + ";"))
										|| (dstObject.getCalleeType() != null && retType.endsWith(dstObject.getCalleeType() + ";"))
										|| (dstObject.getCallerType() != null && retType.endsWith(dstObject.getCallerType() + ";"))) {
									retType = dstObject.getActualType();
								} else {
									retType = null;
								}
								if (retType != null) {
								}
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
					if (removeId.equals(srcObject.getId())) {
						r = new Reference(thisObj, srcObject);
						r.setCreation(creationList.contains(removeId));		// Whether removeId object was created in this method execution or not?
						eStructure.addSrcSide(r);
						srcObject = thisObj;
						isSrcSide = true;
						if (srcAliasList.containsKey(removeId)) {
							aliasCollector.addAlias(srcAliasList.get(removeId));
							aliasCollector.changeTrackingObject(removeId, thisObjectId, isSrcSide);
							aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, srcAliasList.get(removeId).getOccurrencePoint()));
							srcAliasList.remove(removeId);
						}
					} else if (removeId.equals(dstObject.getId())) {
						r = new Reference(thisObj, dstObject);
						r.setCreation(creationList.contains(removeId));		// Whether removeId object was created in this method execution or not?
						eStructure.addDstSide(r);
						dstObject = thisObj;
						isSrcSide = false;
						if (dstAliasList.containsKey(removeId)) {
							aliasCollector.addAlias(dstAliasList.get(removeId));
							aliasCollector.changeTrackingObject(removeId, thisObjectId, isSrcSide);
							aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, dstAliasList.get(removeId).getOccurrencePoint()));
							dstAliasList.remove(removeId);
						}
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
					if (trackingObj != null) {
						// First, we conjecture that it comes from an element of an array object passed as a parameter.
						for (int j = 0; j < arguments.size(); j++) {
							ObjectReference argArray = arguments.get(j);
							if (argArray.getActualType().startsWith("[L") 
									&& (trackingObj.getActualType() != null && (argArray.getActualType().endsWith(trackingObj.getActualType() + ";"))
											|| (trackingObj.getCalleeType() != null && argArray.getActualType().endsWith(trackingObj.getCalleeType() + ";"))
											|| (trackingObj.getCallerType() != null && argArray.getActualType().endsWith(trackingObj.getCallerType() + ";")))) {
								// If the types match.
								existsInAnArgument = true;
								objList.remove(objectId);
								objList.add(argArray.getId());	// Change the tracking object from the array element to the array object.
								((DeltaAugmentationInfo)methodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(argArray.getId()));
								r = new Reference(argArray.getId(), trackingObj.getId(), 
										argArray.getActualType(), trackingObj.getActualType());
								r.setArray(true);
								if (isSrcSide2) {
									eStructure.addSrcSide(r);
									srcObject = new ObjectReference(argArray.getId(), argArray.getActualType());
								} else {
									eStructure.addDstSide(r);
									dstObject = new ObjectReference(argArray.getId(), argArray.getActualType());
								}
								objectId = null;
								aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY_ELEMENT, 0, trackingObj.getId(), methodExecution.getEntryPoint()));	// We assume that the element is get at the entry of the method.
								aliasCollector.changeTrackingObject(trackingObj.getId(), argArray.getId(), isSrcSide2); // Change the tracking object from the array element to the array object.
								aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY, 0, argArray.getId(), methodExecution.getEntryPoint()));		// We assume that the array is accessed at the entry of the method.
								aliasCollector.addAlias(new Alias(Alias.AliasType.FORMAL_PARAMETER, arguments.indexOf(argArray), trackingObj.getId(), methodExecution.getEntryPoint()));					
								break;
							}
						}
						if (objectId != null) {
							// Second, we conjecture that it comes from an element of an array object stored in a field.
							int index = fieldArrayElements.indexOf(trackingObj);
							if (index != -1) {
								// The types match.
								ObjectReference fieldArray = fieldArrays.get(index);
								existsInFields++;
								objList.remove(objectId);
								r = new Reference(fieldArray.getId(), trackingObj.getId(),
										fieldArray.getActualType(), trackingObj.getActualType());
								r.setArray(true);
								if (isSrcSide2) {
									eStructure.addSrcSide(r);
									eStructure.addSrcSide(new Reference(thisObjectId, fieldArray.getId(),
											methodExecution.getThisClassName(), fieldArray.getActualType()));
									srcObject = thisObj;
									if (srcAliasList.containsKey(trackingObj.getId())) {
										aliasCollector.addAlias(srcAliasList.get(trackingObj.getId()));
										aliasCollector.changeTrackingObject(trackingObj.getId(), fieldArray.getId(), isSrcSide2); // Change the tracking object from the array element to the array object.
										aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY, 0, fieldArray.getId(), srcAliasList.get(trackingObj.getId()).getOccurrencePoint()));
										aliasCollector.changeTrackingObject(fieldArray.getId(), thisObjectId, isSrcSide2); // Change the tracking object from the array object (referred to by the field) to this object.
										aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, srcAliasList.get(trackingObj.getId()).getOccurrencePoint()));					
										srcAliasList.remove(trackingObj.getId());
									}
								} else {
									eStructure.addDstSide(r);
									eStructure.addDstSide(new Reference(thisObjectId, fieldArray.getId(),
											methodExecution.getThisClassName(), fieldArray.getActualType()));
									dstObject = thisObj;
									if (dstAliasList.containsKey(trackingObj.getId())) {
										aliasCollector.addAlias(dstAliasList.get(trackingObj.getId()));
										aliasCollector.changeTrackingObject(trackingObj.getId(), fieldArray.getId(), isSrcSide2); // Change the tracking object from the array element to the array object.
										aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY, 0, fieldArray.getId(), dstAliasList.get(trackingObj.getId()).getOccurrencePoint()));
										aliasCollector.changeTrackingObject(fieldArray.getId(), thisObjectId, isSrcSide2); // Change the tracking object from the array object (referred to by the field) to this object.
										aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, dstAliasList.get(trackingObj.getId()).getOccurrencePoint()));					
										dstAliasList.remove(trackingObj.getId());
									}
								}
							}
						}
						if (trackingObj.getActualType() != null && trackingObj.getActualType().startsWith("[L")) {
							// Last, we conjecture that it comes from an element of an array object created in this method execution.
							objList.remove(objectId);
							if (isSrcSide2) {
								eStructure.addSrcSide(new Reference(thisObjectId, trackingObj.getId(),
										methodExecution.getThisClassName(), trackingObj.getActualType()));
								srcObject = thisObj;
							} else {
								eStructure.addDstSide(new Reference(thisObjectId, trackingObj.getId(),
										methodExecution.getThisClassName(), trackingObj.getActualType()));
								dstObject = thisObj;
							}
							aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY_CREATE, 0, trackingObj.getId(), methodExecution.getEntryPoint()));	// We assume that the element is created at the entry of the method.
							aliasCollector.changeTrackingObject(trackingObj.getId(), thisObjectId, isSrcSide2); // Change the tracking object from the array to this object.
							aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, methodExecution.getEntryPoint()));		// We assume that the array is created at the entry of the method.
						}
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
								} else {
									eStructure.addDstSide(r);
									dstObject = thisObj;
								}
								existsInFields++;
								objList.remove(objectId);
								aliasCollector.addAlias(new Alias(Alias.AliasType.FIELD, 0, objectId,  methodExecution.getEntryPoint()));
								aliasCollector.changeTrackingObject(objectId, thisObjectId, isSrcSide2); // Change the tracking object from an object referred to by a local final variable to this object.
								aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId,  methodExecution.getEntryPoint()));
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
	 * @param index which object in objList is to track in this method?
	 * @param aliasCollector collector of the aliases that are traversed in this delta extraction
	 */
	@Deprecated
	protected void calleeSearch(Trace trace, TracePoint tracePoint, ArrayList<String> objList, Boolean isStatic, int index, IAliasTracker aliasCollector) {
		MethodExecution methodExecution = tracePoint.getMethodExecution();
		Boolean isResolved = false;
		String objectId = objList.get(index);		// The object to track in calleeSearch() is only one. No object in objList other than the index-th one can be changed.
		String thisObjectId = methodExecution.getThisObjId();
		ArrayList<ObjectReference> fieldArrays = new ArrayList<ObjectReference>();
		ArrayList<ObjectReference> fieldArrayElements = new ArrayList<ObjectReference>();
		ObjectReference thisObj = new ObjectReference(thisObjectId, methodExecution.getThisClassName(), 
				Trace.getDeclaringType(methodExecution.getSignature(), methodExecution.isConstructor()), 
				Trace.getDeclaringType(methodExecution.getCallerSideSignature(), methodExecution.isConstructor()));
		
		((DeltaAugmentationInfo)methodExecution.getAugmentation()).setSetterSide(false);		// Basically, the tracking object must be referred side, but it may not be always true.
		ArrayList<ObjectReference> arguments = methodExecution.getArguments();
		ObjectReference trackingObj = null;
		
		HashMap<String, DeltaAlias>  srcAliasList = new HashMap<>();
		HashMap<String, DeltaAlias>  dstAliasList = new HashMap<>();

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
					} else {
						// A tracking object might be obtained as an array element accessed here.
						String refObjType = fs.getValueClassName();
						if (refObjType.startsWith("[L")) {
							// If the accessed field is an array object.
							if ((trackingObj.getActualType() != null && refObjType.endsWith(trackingObj.getActualType() + ";")) 
									|| (trackingObj.getCalleeType() != null && refObjType.endsWith(trackingObj.getCalleeType() + ";"))
									|| (trackingObj.getCallerType() != null && refObjType.endsWith(trackingObj.getCallerType() + ";"))) {
								// If the type of the tracking object matches the type of the array element.
								String ownerObjectId = fs.getContainerObjId();
								if (ownerObjectId.equals(thisObjectId)) {
									// If the accessed field is of this.
									fieldArrays.add(new ObjectReference(fs.getValueObjId(), refObjType));
									fieldArrayElements.add(trackingObj);
									if (objectId.equals(srcObject.getId())) {
										srcAliasList.put(objectId, new DeltaAlias(Alias.AliasType.ARRAY_ELEMENT, 0, objectId, tracePoint.duplicate(), true));
									} else if(objectId.equals(dstObject.getId())) {
										dstAliasList.put(objectId, new DeltaAlias(Alias.AliasType.ARRAY_ELEMENT, 0, objectId, tracePoint.duplicate(), false));
									}
								}
							}
						}
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
					} else {
						// A tracking object might be obtained as an element of the return value.
						String retType = ret.getActualType();
						if (retType.startsWith("[L")) {
							// If the return value is an array object.
							if ((trackingObj.getActualType() != null && retType.endsWith(trackingObj.getActualType() + ";"))
											|| (trackingObj.getCalleeType() != null && retType.endsWith(trackingObj.getCalleeType() + ";"))
											|| (trackingObj.getCallerType() != null && retType.endsWith(trackingObj.getCallerType() + ";"))) {
							}
						}
					}
				}
			} while (tracePoint.stepBackOver());
			
			// Search the actual arguments of this method execution.
			if (arguments.contains(new ObjectReference(objectId))) {
				((DeltaAugmentationInfo)methodExecution.getAugmentation()).setSetterSide(true);		// May be needed?
				isResolved = true;
				aliasCollector.addAlias(new Alias(Alias.AliasType.FORMAL_PARAMETER, arguments.indexOf(new ObjectReference(objectId)), objectId, methodExecution.getEntryPoint()));
			}
		}
		
		// For an invocation of a collection type.
		Reference r;
		if (methodExecution.isCollectionType()) {
			if (objectId != null) {
				// If objectId is returned from this collection object, then it is assumed to be directly held in the object.
				if (objectId.equals(srcObject.getId())) {
					r = new Reference(thisObj, srcObject);
					r.setCollection(true);
					eStructure.addSrcSide(r);
					srcObject = thisObj;
					aliasCollector.changeTrackingObject(objectId, thisObjectId, true);
					aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, tracePoint.duplicate()));
				} else if(objectId.equals(dstObject.getId())) {
					r = new Reference(thisObj, dstObject);
					r.setCollection(true);
					eStructure.addDstSide(r);
					dstObject =thisObj;
					aliasCollector.changeTrackingObject(objectId, thisObjectId, false);
					aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, tracePoint.duplicate()));
				}
			}
			objList.set(index, methodExecution.getThisObjId());
			isResolved = true;		// Should be needed.
		}
		
		if (!isResolved && objectId != null) {
			// The origin could not be found.
			boolean isSrcSide = true;
			if (objectId.equals(srcObject.getId())) {
				isSrcSide = true;
			} else if (objectId.equals(dstObject.getId())) {
				isSrcSide = false;				
			}
			if (trackingObj != null) {
				// First, we conjecture that it comes from an element of an array object passed as a parameter.
				for (int i = 0; i < arguments.size(); i++) {
					ObjectReference argArray = arguments.get(i);
					if (argArray.getActualType().startsWith("[L") 
							&& ((trackingObj.getActualType() != null && argArray.getActualType().endsWith(trackingObj.getActualType() + ";"))
									|| (trackingObj.getCalleeType() != null && argArray.getActualType().endsWith(trackingObj.getCalleeType() + ";"))
									|| (trackingObj.getCallerType() != null && argArray.getActualType().endsWith(trackingObj.getCallerType() + ";")))) {
						// If the types match.
						isResolved = true;
						objList.set(index, argArray.getId());	// Change the tracking object from the array element to the array object.
						((DeltaAugmentationInfo)methodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(argArray.getId()));
						r = new Reference(argArray.getId(), trackingObj.getId(),
								argArray.getActualType(), trackingObj.getActualType());
						r.setArray(true);
						if (isSrcSide) {
							eStructure.addSrcSide(r);
							srcObject = new ObjectReference(argArray.getId(), argArray.getActualType());
						} else {
							eStructure.addDstSide(r);
							dstObject = new ObjectReference(argArray.getId(), argArray.getActualType());
						}
						objectId = null;
						aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY_ELEMENT, 0, trackingObj.getId(), methodExecution.getEntryPoint()));	// We assume that the element is get at the entry of the method.
						aliasCollector.changeTrackingObject(trackingObj.getId(), argArray.getId(), isSrcSide); // Change the tracking object from the array element to the array object.
						aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY, 0, argArray.getId(), methodExecution.getEntryPoint()));		// We assume that the array is accessed at the entry of the method.
						aliasCollector.addAlias(new Alias(Alias.AliasType.FORMAL_PARAMETER, arguments.indexOf(argArray), trackingObj.getId(), methodExecution.getEntryPoint()));					
						break;
					}
				}
				if (objectId != null) {
					// Second, we conjecture that it comes from an element of an array object stored in a field.
					int indArg = fieldArrayElements.indexOf(trackingObj);
					if (indArg != -1) {
						// The types match.
						isResolved = true;
						ObjectReference fieldArray = fieldArrays.get(indArg);
						objList.set(index, thisObjectId);	// Change the tracking object from the array element to this object.
						r = new Reference(fieldArray.getId(), trackingObj.getId(),
								fieldArray.getActualType(), trackingObj.getActualType());
						r.setArray(true);
						if (isSrcSide) {
							eStructure.addSrcSide(r);
							eStructure.addSrcSide(new Reference(thisObjectId, fieldArray.getId(),
									methodExecution.getThisClassName(), fieldArray.getActualType()));
							srcObject = thisObj;
							aliasCollector.addAlias(srcAliasList.get(trackingObj.getId()));
							aliasCollector.changeTrackingObject(trackingObj.getId(), fieldArray.getId(), isSrcSide); // Change the tracking object from the array element to the array object.
							aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY, 0, fieldArray.getId(), srcAliasList.get(trackingObj.getId()).getOccurrencePoint()));
							aliasCollector.changeTrackingObject(fieldArray.getId(), thisObjectId, isSrcSide); // Change the tracking object from the array object (referred to by the field) to this object.
							aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, srcAliasList.get(trackingObj.getId()).getOccurrencePoint()));					
							srcAliasList.remove(trackingObj.getId());
						} else {
							eStructure.addDstSide(r);
							eStructure.addDstSide(new Reference(thisObjectId, fieldArray.getId(),
									methodExecution.getThisClassName(), fieldArray.getActualType()));
							dstObject = thisObj;
							aliasCollector.addAlias(dstAliasList.get(trackingObj.getId()));
							aliasCollector.changeTrackingObject(trackingObj.getId(), fieldArray.getId(), isSrcSide); // Change the tracking object from the array element to the array object.
							aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY, 0, fieldArray.getId(), dstAliasList.get(trackingObj.getId()).getOccurrencePoint()));
							aliasCollector.changeTrackingObject(fieldArray.getId(), thisObjectId, isSrcSide); // Change the tracking object from the array object (referred to by the field) to this object.
							aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, dstAliasList.get(trackingObj.getId()).getOccurrencePoint()));					
							dstAliasList.remove(trackingObj.getId());
						}
					}
				}
				if (trackingObj.getActualType() != null && trackingObj.getActualType().startsWith("[L")) {
					// Last, we conjecture that it comes from an element of an array object created in this method execution.
					isResolved = true;
					objList.set(index, thisObjectId);	// Change the tracking object from the array element to this object.
					if (isSrcSide) {
						eStructure.addSrcSide(new Reference(thisObjectId, trackingObj.getId(),
								methodExecution.getThisClassName(), trackingObj.getActualType()));
						srcObject = thisObj;
					} else {
						eStructure.addDstSide(new Reference(thisObjectId, trackingObj.getId(),
								methodExecution.getThisClassName(), trackingObj.getActualType()));
						dstObject = thisObj;
					}
					aliasCollector.addAlias(new Alias(Alias.AliasType.ARRAY_CREATE, 0, trackingObj.getId(), methodExecution.getEntryPoint()));	// We assume that the array is created at the entry of the method.
					aliasCollector.changeTrackingObject(trackingObj.getId(), thisObjectId, isSrcSide); // Change the tracking object from the array to this object.
					aliasCollector.addAlias(new Alias(Alias.AliasType.THIS, 0, thisObjectId, methodExecution.getEntryPoint()));		// We assume that the array is created at the entry of the method.
				}
			}
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
	
	/**
	 * Extract the delta that relates the source and the destination objects of an object reference by specifying the reference.
	 * @param targetRef an object reference (including the relation between an array object and its element, and that between a collection object and its element)
	 * @param before execution point to start the backward traverse
	 * @return the result of the extraction
	 */
	public ExtractedStructure extract(Reference targetRef, TracePoint before) {
		return extract(targetRef, before, defaultAliasCollector);
	}
	
	/**
	 * Extract the delta that relates the source and the destination objects of an object reference by specifying the reference.
	 * @param targetRef an object reference (including the relation between an array object and its element, and that between a collection object and its element)
	 * @param before execution point to start the backward traverse
	 * @param aliasCollector collector of the aliases that are traversed in this delta extraction
	 * @return the result of the extraction
	 */
	public ExtractedStructure extract(Reference targetRef, TracePoint before, IAliasTracker aliasCollector) {
		TracePoint creationTracePoint;
		if (targetRef.isArray()) {
			// Search the execution point where the object specified by dstId was stored to an element of the array object specified by srcId.
			creationTracePoint = trace.getArraySetTracePoint(targetRef, before);
		} else if (targetRef.isCollection()) {
			// Search the execution point where the object specified by dstId was set to the collection object specified by srcId.
			creationTracePoint = trace.getCollectionAddTracePoint(targetRef, before);
		} else if (targetRef.isFinalLocal()) {
			// Search the execution point where the object stored in a final local variable and specified by dstId was passed 
			// to the anonymous or inner object specified by srcId.
			creationTracePoint = trace.getCreationTracePoint(targetRef.getSrcObject(), before);
			targetRef = new Reference(creationTracePoint.getMethodExecution().getThisObjId(), targetRef.getDstObjectId(), creationTracePoint.getMethodExecution().getThisClassName(), targetRef.getDstClassName());	
		} else {
			// Search the execution point where the object reference was created. (The most typical case.)
			creationTracePoint = trace.getFieldUpdateTracePoint(targetRef, before);
		}
		if (creationTracePoint == null) {
			return null;
		}
		return extractSub(creationTracePoint, targetRef, aliasCollector);
	}
	
	/**
	 * Extract the delta that relates the source and the destination objects of an object reference by specifying the execution point where the reference is created.
	 * @param creationTracePoint execution point where the reference is created (an update of a field)
	 * @return the result of the extraction
	 */
	public ExtractedStructure extract(TracePoint creationTracePoint) {
		creationTracePoint = creationTracePoint.duplicate();
		Statement statement = creationTracePoint.getStatement();
		if (statement instanceof FieldUpdate) {
			Reference targetRef = ((FieldUpdate)statement).getReference();
			return extractSub(creationTracePoint, targetRef, defaultAliasCollector);
		} else {
			return null;
		}
	}
	
	/**
	 * Extract the delta that relates the source and the destination objects of an object reference by specifying the execution point where the reference is created.
	 * @param creationTracePoint execution point where the reference is created (an update of a field)
	 * @param aliasCollector collector of the aliases that are traversed in this delta extraction
	 * @return the result of the extraction
	 */
	public ExtractedStructure extract(TracePoint creationTracePoint, IAliasTracker aliasCollector) {
		creationTracePoint = creationTracePoint.duplicate();
		Statement statement = creationTracePoint.getStatement();
		if (statement instanceof FieldUpdate) {
			Reference targetRef = ((FieldUpdate)statement).getReference();
			return extractSub(creationTracePoint, targetRef, aliasCollector);
		} else {
			return null;
		}
	}
 
	private ExtractedStructure extractSub(TracePoint creationTracePoint, Reference targetRef, IAliasTracker aliasCollector) {
		eStructure = new ExtractedStructure();
		eStructure.setRelatedTracePoint(creationTracePoint.duplicate());
		ArrayList<String> objList = new ArrayList<String>(); 
		srcObject = targetRef.getSrcObject();
		dstObject = targetRef.getDstObject();
if (DEBUG1) {
		System.out.println("extract delta of:" + targetRef.getSrcObject().getActualType() + "(" + targetRef.getSrcObjectId() + ")" + " -> " + targetRef.getDstObject().getActualType()  + "(" + targetRef.getDstObjectId() + ")");
}
		if (!Trace.isNull(targetRef.getSrcObjectId())) {
			objList.add(targetRef.getSrcObjectId());
		} else {
			objList.add(null);
		}
		if (!Trace.isNull(targetRef.getDstObjectId())) {
			objList.add(targetRef.getDstObjectId());
		} else {
			objList.add(null);
		}
		return extractSub2(creationTracePoint, objList, aliasCollector);
	}
	
	/**
	 * Extract the delta that relates the caller object and the callee object by specifying the called method execution.
	 * @param calledMethodExecution called method execution
	 * @return the result of the extraction
	 */
	public ExtractedStructure extract(MethodExecution calledMethodExecution) {
		return extract(calledMethodExecution, defaultAliasCollector);
	}	
	
	/**
	 * Extract the delta that relates the caller object and the callee object by specifying the called method execution.
	 * @param calledMethodExecution called method execution
	 * @param aliasCollector collector of the aliases that are traversed in this delta extraction
	 * @return the result of the extraction
	 */
	public ExtractedStructure extract(MethodExecution calledMethodExecution, IAliasTracker aliasCollector) {
		ObjectReference callee = new ObjectReference(calledMethodExecution.getThisObjId(), calledMethodExecution.getThisClassName());
		return extract(calledMethodExecution.getCallerTracePoint(), callee, aliasCollector);
	}
 
	/**
	 *  Extract the delta that relates 'this' object and another object that is referred to locally in the method execution.
	 * @param thisTracePoint execution point where another object is referred to
	 * @param anotherObj another object
	 * @return the result of the extraction
	 */
	public ExtractedStructure extract(TracePoint thisTracePoint, ObjectReference anotherObj) {
		return extract(thisTracePoint, anotherObj, defaultAliasCollector);
	}
 
	/**
	 * Extract the delta that relates 'this' object and another object that is referred to locally in the method execution.
	 * @param thisTracePoint execution point where another object is referred to
	 * @param anotherObj another object
	 * @param aliasCollector collector of the aliases that are traversed in this delta extraction
	 * @return the result of the extraction
	 */
	public ExtractedStructure extract(TracePoint thisTracePoint, ObjectReference anotherObj, IAliasTracker aliasCollector) {
		eStructure = new ExtractedStructure();
		eStructure.setRelatedTracePoint(thisTracePoint.duplicate());
		MethodExecution methodExecution = thisTracePoint.getMethodExecution();
		thisTracePoint.stepNext();
		ArrayList<String> objList = new ArrayList<String>();
		String thisObjectId = methodExecution.getThisObjId();
		objList.add(thisObjectId);
		objList.add(anotherObj.getId());
		srcObject = new ObjectReference(thisObjectId, methodExecution.getThisClassName(), 
				Trace.getDeclaringType(methodExecution.getSignature(), methodExecution.isConstructor()), Trace.getDeclaringType(methodExecution.getCallerSideSignature(), methodExecution.isConstructor()));
		dstObject = anotherObj;
if (DEBUG1) {
		System.out.println("extract delta of:" + methodExecution.getSignature() + " -> " + anotherObj.getActualType()  + "(" + anotherObj.getId() + ")");
}
		return extractSub2(thisTracePoint, objList, aliasCollector);
	}
	
	private ExtractedStructure extractSub2(TracePoint tracePoint, ArrayList<String> objList, IAliasTracker aliasCollector) {
		eStructure.setCreationMethodExecution(tracePoint.getMethodExecution());
		MethodExecution coordinator = callerSearch(trace, tracePoint, objList, null, aliasCollector);
		eStructure.setCoordinator(coordinator);
if (DEBUG2) {
		if (((DeltaAugmentationInfo)coordinator.getAugmentation()).isCoodinator()) {
			System.out.println("Coordinator");
		} else {
			System.out.println("Warning");
		}
		System.out.println("coordinator:" + coordinator.getSignature());
		System.out.println("srcSide:");
		for (int i = 0; i < eStructure.getDelta().getSrcSide().size(); i++) {
			Reference ref = eStructure.getDelta().getSrcSide().get(i);
			if (!ref.isCreation() || !ref.getSrcObjectId().equals(ref.getDstObjectId())) {
				System.out.println("\t" + ref.getSrcClassName() + "(" + ref.getSrcObjectId() + ")" + " -> " + ref.getDstClassName() + "(" + ref.getDstObjectId() + ")");
			}
		}
		System.out.println("dstSide:");
		for (int i = 0; i < eStructure.getDelta().getDstSide().size(); i++) {
			Reference ref = eStructure.getDelta().getDstSide().get(i);
			if (!ref.isCreation() || !ref.getSrcObjectId().equals(ref.getDstObjectId())) {
				System.out.println("\t" + ref.getSrcClassName() + "(" + ref.getSrcObjectId() + ")" + " -> " + ref.getDstClassName() + "(" + ref.getDstObjectId() + ")");
			}
		}
		System.out.println("overCoordinator:");
		MethodExecution parent = coordinator.getParent();
		while (parent != null) {
			System.out.println("\t" + parent.getSignature());
			parent = parent.getParent();
		}
}
		return eStructure;
	}
	
	/**
	 * Extract the delta that relates the 'real' referring object and referred object. (for online analysis)
	 * @param srcObj referring object in the memory
	 * @param dstObj referred object in the memory
	 * @param before execution point to start the backward traverse
	 * @return the result of the extraction
	 */
	public ExtractedStructure extract(Object srcObj, Object dstObj, TracePoint before) {
		return extract(srcObj, dstObj, before, defaultAliasCollector);
	}
	
	/**
	 * Extract the delta that relates the 'real' referring object and referred object. (for online analysis)
	 * @param srcObj referring object in the memory
	 * @param dstObj referred object in the memory
	 * @param before execution point to start the backward traverse
	 * @param aliasCollector collector of the aliases that are traversed in this delta extraction
	 * @return the result of the extraction
	 */
	public ExtractedStructure extract(Object srcObj, Object dstObj, TracePoint before, IAliasTracker aliasCollector) {
		Reference targetRef = new Reference(Integer.toString(System.identityHashCode(srcObj)), Integer.toString(System.identityHashCode(dstObj)), null, null);
		return extract(targetRef, before, aliasCollector);
	}
	
	/**
	 * Extract the delta that relates 'this' object and another 'real' object that is referred to locally at the specified execution point. (for online analysis)
	 * @param tracePoint execution point
	 * @param arg another 'real' object that is referred to at the execution point (by a local variable or a formal parameter)
	 * @return the result of the extraction
	 */
	public ExtractedStructure extract(TracePoint tracePoint, Object arg) {
		return extract(tracePoint, arg, defaultAliasCollector);
	}
	
	/**
	 * Extract the delta that relates 'this' object and another 'real' object that is referred to locally at the specified execution point. (for online analysis)
	 * @param tracePoint execution point
	 * @param arg another 'real' object that is referred to at the execution point (by a local variable or a formal parameter)
	 * @param aliasCollector collector of the aliases that are traversed in this delta extraction
	 * @return the result of the extraction
	 */
	public ExtractedStructure extract(TracePoint tracePoint, Object arg, IAliasTracker aliasCollector) {
		ObjectReference argObj = new ObjectReference(Integer.toString(System.identityHashCode(arg)));
		return extract(tracePoint, argObj, aliasCollector);
	}
	
	/**
	 * Get the current method execution on the specified 'real' thread (for online analysis)
	 * @param thread 'real' thread
	 * @return the currently executing method execution on thread
	 */
	public MethodExecution getCurrentMethodExecution(Thread thread) {
		return trace.getCurrentMethodExecution(thread);
	}
 
	/**
	 * Get the last execution of the method whose name starts with methodSignature.
	 * @param methodSignature method name (prefix search)
	 * @return the last execution of the method
	 */
	public MethodExecution getLastMethodExecution(String methodSignature) {
		return trace.getLastMethodExecution(methodSignature);
	}
 
	/**
	 * Get the last execution of the method whose name starts with methodSignature before the specified execution point.
	 * @param methodSignature method name (prefix search)
	 * @param before execution point to start the backward traverse
	 * @return the last execution of the method
	 */
	public MethodExecution getLastMethodExecution(String methodSignature, TracePoint before) {
		return trace.getLastMethodExecution(methodSignature, before);
	}
 
	public ArrayList<MethodExecution> getMethodExecutions(String methodSignature) {
		return trace.getMethodExecutions(methodSignature);
	}
}