/*
 * RED5 Open Source Flash Server - http://code.google.com/p/red5/
 * 
 * Copyright 2006-2013 by respective authors (see below). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.io.object;

import java.util.HashMap;
import java.util.Map;

/**
 * BaseInput represents a way to map input to a HashMap.  This class
 * is meant to be extended.
 * 
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 */
public class BaseInput {
	
	/**
     * References map
     */
	protected Map<Integer, Object> refMap = new HashMap<Integer, Object>();

    /**
     * References id
     */
    protected int refId;
    
	/**
	 * Store an object into a map
	 * @param obj  Object to store
	 */
	protected int storeReference(Object obj) {
		int newRefId = refId++;
		refMap.put(Integer.valueOf(newRefId), obj);
		return newRefId;
	}

	/**
	 * Replace a referenced object with another one. This is used
	 * by the AMF3 deserializer to handle circular references.
	 * 
	 * @param refId
	 * @param newRef
	 */
	protected void storeReference(int refId, Object newRef) {
		refMap.put(Integer.valueOf(refId), newRef);
	}
	
	/**
	 * Clears the map
	 */
	public void clearReferences() {
		refMap.clear();
		refId = 0;
	}

	/**
	 * Returns the object with the parameters id
	 * @param id        Object reference id
	 * @return Object   Object reference with given id
	 */
	protected Object getReference(int id) {
		return refMap.get(Integer.valueOf(id));
	}

}
