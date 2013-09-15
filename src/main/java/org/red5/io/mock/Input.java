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

package org.red5.io.mock;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.red5.io.amf3.ByteArray;
import org.red5.io.object.BaseInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class Input extends BaseInput implements org.red5.io.object.Input {

	protected static Logger log = LoggerFactory.getLogger(Input.class);

	protected List<Object> list;

	protected int idx;

	public Input(List<Object> list) {
		super();
		this.list = list;
		this.idx = 0;
	}

	/**
	 * Getter for property 'next'.
	 *
	 * @return Value for property 'next'.
	 */
	protected Object getNext() {
		return list.get(idx++);
	}

	/** {@inheritDoc} */
	public byte readDataType() {
		Byte b = (Byte) getNext();
		return b.byteValue();
	}

	// Basic

	/** {@inheritDoc} */
	public Object readNull(Type target) {
		return null;
	}

	/** {@inheritDoc} */
	public Boolean readBoolean(Type target) {
		return (Boolean) getNext();
	}

	/** {@inheritDoc} */
	public Number readNumber(Type target) {
		return (Number) getNext();
	}

	/** {@inheritDoc} */
	public String getString() {
		return (String) getNext();
	}

	/** {@inheritDoc} */
	public String readString(Type target) {
		return (String) getNext();
	}

	/** {@inheritDoc} */
	public Date readDate(Type target) {
		return (Date) getNext();
	}

	// Array

	/** {@inheritDoc} */
	public Object readArray(Type target) {
		return getNext();
	}

	/** {@inheritDoc} */
	public Object readMap(Type target) {
		return getNext();
	}

	/** {@inheritDoc} */
	@SuppressWarnings("unchecked")
	public Map<String, Object> readKeyValues() {
		return (Map<String, Object>) getNext();
	}

	// Object

	/** {@inheritDoc} */
	public Object readObject(Type target) {
		return getNext();
	}

	/** {@inheritDoc} */
	public Document readXML(Type target) {
		return (Document) getNext();
	}

	/** {@inheritDoc} */
	public Object readCustom(Type target) {
		// Not supported
		return null;
	}

	/** {@inheritDoc} */
	public ByteArray readByteArray(Type target) {
		return (ByteArray) getNext();
	}

	@SuppressWarnings("unchecked")
	public Vector<Integer> readVectorInt() {
		return (Vector<Integer>) getNext();
	}

	@SuppressWarnings("unchecked")
	public Vector<Long> readVectorUInt() {
		return (Vector<Long>) getNext();
	}

	@SuppressWarnings("unchecked")
	public Vector<Double> readVectorNumber() {
		return (Vector<Double>) getNext();
	}

	@SuppressWarnings("unchecked")
	public Vector<Object> readVectorObject() {
		return (Vector<Object>) getNext();
	}

	/** {@inheritDoc} */
	public Object readReference(Type target) {
		final Short num = (Short) getNext();
		return getReference(num.shortValue());
	}

}
