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

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.red5.io.amf3.ByteArray;
import org.red5.io.object.BaseOutput;
import org.red5.io.object.DataTypes;
import org.red5.io.object.RecordSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class Output extends BaseOutput implements org.red5.io.object.Output {

	protected static Logger log = LoggerFactory.getLogger(Output.class);

	protected List<Object> list;

	public Output(List<Object> list) {
		super();
		this.list = list;
	}

	/** {@inheritDoc} */
	public boolean isCustom(Object custom) {
		// No custom types supported
		return false;
	}

	/** {@inheritDoc} */
	public void putString(String string) {
		list.add(string);
	}

	/** {@inheritDoc} */
	public boolean supportsDataType(byte type) {
		// does not yet support references
		return type <= DataTypes.OPT_REFERENCE;
	}

	/** {@inheritDoc} */
	public void writeBoolean(Boolean bol) {
		list.add(Byte.valueOf(DataTypes.CORE_BOOLEAN));
		list.add(bol);
	}

	/** {@inheritDoc} */
	public void writeCustom(Object custom) {
		// Customs not supported by this version
	}

	/** {@inheritDoc} */
	public void writeDate(Date date) {
		list.add(Byte.valueOf(DataTypes.CORE_DATE));
		list.add(date);
	}

	/** {@inheritDoc} */
	// DONE
	public void writeNull() {
		list.add(Byte.valueOf(DataTypes.CORE_NULL));
	}

	/** {@inheritDoc} */
	// DONE
	public void writeNumber(Number num) {
		list.add(Byte.valueOf(DataTypes.CORE_NUMBER));
		list.add(num);
	}

	/** {@inheritDoc} */
	public void writeReference(Object obj) {
		list.add(Byte.valueOf(DataTypes.OPT_REFERENCE));
		list.add(Short.valueOf(getReferenceId(obj)));
	}

	/** {@inheritDoc} */
	public void writeString(String string) {
		list.add(Byte.valueOf(DataTypes.CORE_STRING));
		list.add(string);
	}

	/** {@inheritDoc} */
	public void writeXML(Document xml) {
		list.add(Byte.valueOf(DataTypes.CORE_XML));
		list.add(xml);
	}

	/** {@inheritDoc} */
	public void writeArray(Collection<?> array) {
		list.add(Byte.valueOf(DataTypes.CORE_ARRAY));
		list.add(array);
	}

	/** {@inheritDoc} */
	public void writeArray(Object[] array) {
		list.add(Byte.valueOf(DataTypes.CORE_ARRAY));
		list.add(array);
	}

	/** {@inheritDoc} */
	public void writeArray(Object array) {
		list.add(Byte.valueOf(DataTypes.CORE_ARRAY));
		list.add(array);
	}

	/** {@inheritDoc} */
	public void writeMap(Map<Object, Object> map) {
		list.add(Byte.valueOf(DataTypes.CORE_MAP));
		list.add(map);
	}

	/** {@inheritDoc} */
	public void writeMap(Collection<?> array) {
		list.add(Byte.valueOf(DataTypes.CORE_MAP));
		list.add(array);
	}

	/** {@inheritDoc} */
	public void writeObject(Object object) {
		list.add(Byte.valueOf(DataTypes.CORE_OBJECT));
		list.add(object);
	}

	/** {@inheritDoc} */
	public void writeObject(Map<Object, Object> map) {
		list.add(Byte.valueOf(DataTypes.CORE_OBJECT));
		list.add(map);
	}

	/** {@inheritDoc} */
	public void writeRecordSet(RecordSet recordset) {
		list.add(Byte.valueOf(DataTypes.CORE_OBJECT));
		list.add(recordset);
	}

	/** {@inheritDoc} */
	public void writeByteArray(ByteArray array) {
		list.add(Byte.valueOf(DataTypes.CORE_BYTEARRAY));
		list.add(array);
	}

	/** {@inheritDoc} */
	public void writeVectorInt(Vector<Integer> vector) {
		list.add(Byte.valueOf(DataTypes.CORE_VECTOR_INT));
		list.add(vector);
	}

	/** {@inheritDoc} */
	public void writeVectorUInt(Vector<Long> vector) {
		list.add(Byte.valueOf(DataTypes.CORE_VECTOR_UINT));
		list.add(vector);
	}

	/** {@inheritDoc} */
	public void writeVectorNumber(Vector<Double> vector) {
		list.add(Byte.valueOf(DataTypes.CORE_VECTOR_NUMBER));
		list.add(vector);
	}

	/** {@inheritDoc} */
	public void writeVectorObject(Vector<Object> vector) {
		list.add(Byte.valueOf(DataTypes.CORE_VECTOR_OBJECT));
		list.add(vector);
	}

}
