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

import java.util.Iterator;
import java.util.List;

import org.red5.io.object.DataTypes;

public class Mock {

	public static final byte TYPE_END_OF_OBJECT = (byte) (DataTypes.CUSTOM_MOCK_MASK + 0x01);

	public static final byte TYPE_END_OF_ARRAY = (byte) (DataTypes.CUSTOM_MOCK_MASK + 0x02);

	public static final byte TYPE_ELEMENT_SEPARATOR = (byte) (DataTypes.CUSTOM_MOCK_MASK + 0x03);

	public static final byte TYPE_PROPERTY_SEPARATOR = (byte) (DataTypes.CUSTOM_MOCK_MASK + 0x04);

	public static final byte TYPE_ITEM_SEPARATOR = (byte) (DataTypes.CUSTOM_MOCK_MASK + 0x05);

	public static final byte TYPE_END_OF_MAP = (byte) (DataTypes.CUSTOM_MOCK_MASK + 0x06);

	public static String toStringValue(byte dataType) {

		switch (dataType) {
			case TYPE_END_OF_OBJECT:
				return "End of Object";
			case TYPE_END_OF_ARRAY:
				return "End of Array";
			case TYPE_ELEMENT_SEPARATOR:
			case TYPE_ITEM_SEPARATOR:
				return ",";
			case TYPE_PROPERTY_SEPARATOR:
				return "::";
			default:
				return "MOCK[" + (dataType - DataTypes.CUSTOM_MOCK_MASK) + ']';
		}
	}

	public static String listToString(List<Object> list) {
		StringBuffer sb = new StringBuffer();
		Iterator<Object> it = list.iterator();
		while (it.hasNext()) {
			Object val = it.next();
			if (val instanceof Byte) {
				byte type = ((Byte) val).byteValue();
				if (type < DataTypes.CUSTOM_MOCK_MASK) {
					sb.append(DataTypes.toStringValue(type));
				} else {
					sb.append(toStringValue(type));
				}
			} else {
				if (val != null) {
					sb.append(val.getClass().getName());
				}
				sb.append(" { ");
				sb.append(val == null ? null : val.toString());
				sb.append(" } ");
			}
			sb.append(" | ");
		}
		return sb.toString();
	}
}
