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

package org.red5.io;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.commons.beanutils.BeanMap;
import org.red5.io.model.CircularRefBean;
import org.red5.io.model.SimpleJavaBean;
import org.red5.io.object.Deserializer;
import org.red5.io.object.Input;
import org.red5.io.object.Output;
import org.red5.io.object.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 */
public abstract class AbstractIOTest extends TestCase {

	protected Logger log = LoggerFactory.getLogger(AbstractIOTest.class);

	protected Input in;

	protected Output out;

	abstract void dumpOutput();

	abstract void resetOutput();

	/** {@inheritDoc} */
	@Override
	protected void setUp() {
		setupIO();
	}

	abstract void setupIO();

	public void testArray() {
		log.debug("Testing array");
		String[] strArrIn = new String[] { "This", "Is", "An", "Array", "Of", "Strings" };
		Serializer.serialize(out, strArrIn);
		dumpOutput();
		Object[] objArrayOut = Deserializer.deserialize(in, Object[].class);
		for (int i = 0; i < strArrIn.length; i++) {
			Assert.assertEquals(strArrIn[i], objArrayOut[i]);
		}
		resetOutput();
	}

	public void testArrayReference() {
		log.debug("Testing array reference");
		TestVO mytest = new TestVO();
		TestVO[] strArrIn = new TestVO[] { mytest, mytest };
		Serializer.serialize(out, strArrIn);
		dumpOutput();
		TestVO[] objArrayOut = Deserializer.deserialize(in, TestVO[].class);
		for (int i = 0; i < strArrIn.length; i++) {
			Assert.assertEquals(strArrIn[i], objArrayOut[i]);
		}
		resetOutput();
	}

	public void testBoolean() {
		log.debug("Testing boolean");
		Serializer.serialize(out, Boolean.TRUE);
		dumpOutput();
		Boolean val = Deserializer.deserialize(in, Boolean.class);
		Assert.assertEquals(Boolean.TRUE, val);
		resetOutput();
		Serializer.serialize(out, Boolean.FALSE);
		dumpOutput();
		val = Deserializer.deserialize(in, Boolean.class);
		Assert.assertEquals(Boolean.FALSE, val);
		resetOutput();
	}

	public void testCircularReference() {
		CircularRefBean beanIn = new CircularRefBean();
		beanIn.setRefToSelf(beanIn);
		Serializer.serialize(out, beanIn);

		dumpOutput();
		CircularRefBean beanOut = Deserializer.deserialize(in, CircularRefBean.class);
		Assert.assertNotNull(beanOut);
		Assert.assertEquals(beanOut, beanOut.getRefToSelf());
		Assert.assertEquals(beanIn.getNameOfBean(), beanOut.getNameOfBean());
		resetOutput();

	}

	public void testDate() {
		log.debug("Testing date");
		Date dateIn = new Date();
		Serializer.serialize(out, dateIn);
		dumpOutput();
		Date dateOut = Deserializer.deserialize(in, Date.class);
		Assert.assertEquals(dateIn, dateOut);
		resetOutput();
	}

	@SuppressWarnings({ "rawtypes" })
	public void testJavaBean() {
		log.debug("Testing list");
		TestJavaBean beanIn = new TestJavaBean();
		beanIn.setTestString("test string here");
		beanIn.setTestBoolean((System.currentTimeMillis() % 2 == 0) ? true : false);
		beanIn.setTestBooleanObject((System.currentTimeMillis() % 2 == 0) ? Boolean.TRUE : Boolean.FALSE);
		beanIn.setTestNumberObject(Integer.valueOf((int) System.currentTimeMillis() / 1000));
		Serializer.serialize(out, beanIn);
		dumpOutput();
		Object mapOrBean = Deserializer.deserialize(in, Object.class);
		Assert.assertEquals(beanIn.getClass().getName(), mapOrBean.getClass().getName());
		Map<?, ?> map = (mapOrBean instanceof Map) ? (Map<?, ?>) mapOrBean : new BeanMap(mapOrBean);
		Set<?> entrySet = map.entrySet();
		Iterator<?> it = entrySet.iterator();
		Map beanInMap = new BeanMap(beanIn);
		Assert.assertEquals(beanInMap.size(), map.size());
		while (it.hasNext()) {
			Map.Entry entry = (Map.Entry) it.next();
			String propOut = (String) entry.getKey();
			Object valueOut = entry.getValue();
			Assert.assertTrue(beanInMap.containsKey(propOut));
			Assert.assertEquals(valueOut, beanInMap.get(propOut));
		}
		resetOutput();
	}

	public void testList() {
		log.debug("Testing list");
		List<Comparable<?>> listIn = new LinkedList<Comparable<?>>();
		listIn.add(null);
		listIn.add(Boolean.FALSE);
		listIn.add(Boolean.TRUE);
		listIn.add(Integer.valueOf(1));
		listIn.add("This is a test string");
		listIn.add(new Date());
		Serializer.serialize(out, listIn);
		dumpOutput();
		List<?> listOut = Deserializer.deserialize(in, List.class);
		Assert.assertNotNull(listOut);
		Assert.assertEquals(listIn.size(), listOut.size());
		for (int i = 0; i < listIn.size(); i++) {
			Assert.assertEquals(listOut.get(i), listIn.get(i));
		}
		resetOutput();
	}

	public void testMap() {
		Map<String, Object> mapIn = new HashMap<String, Object>();
		mapIn.put("testNumber", Integer.valueOf(34));
		mapIn.put("testString", "wicked");
		mapIn.put("testBean", new SimpleJavaBean());
		Serializer.serialize(out, mapIn);

		dumpOutput();
		Map<?, ?> mapOut = Deserializer.deserialize(in, Map.class);
		Assert.assertNotNull(mapOut);
		Assert.assertEquals(mapIn.size(), mapOut.size());

		Set<?> entrySet = mapOut.entrySet();
		Iterator<?> it = entrySet.iterator();
		while (it.hasNext()) {
			Map.Entry<?, ?> entry = (Map.Entry<?, ?>) it.next();
			String propOut = (String) entry.getKey();
			Object valueOut = entry.getValue();

			Assert.assertTrue(mapIn.containsKey(propOut));
			Object valueIn = mapIn.get(propOut);
			Assert.assertEquals(valueOut, valueIn);
		}
		resetOutput();

	}

	public void testNull() {
		log.debug("Testing null");
		Serializer.serialize(out, null);
		dumpOutput();
		Object val = Deserializer.deserialize(in, Object.class);
		Assert.assertEquals(val, null);
		resetOutput();
	}

	public void testNumber() {
		log.debug("Testing number");
		int num = 1000;
		Serializer.serialize(out, Integer.valueOf(num));
		dumpOutput();
		Number n = Deserializer.deserialize(in, Number.class);
		Assert.assertEquals(n.intValue(), num);
		resetOutput();
	}

	public void testInteger() {
		log.debug("Testing integer");
		int num = 129;
		Serializer.serialize(out, Integer.valueOf(num));
		dumpOutput();
		Integer n = Deserializer.deserialize(in, Integer.class);
		Assert.assertEquals(n.intValue(), num);
		resetOutput();
	}

	public void testNegativeInteger() {
		log.debug("Testing negative integer");
		int num = -129;
		Serializer.serialize(out, Integer.valueOf(num));
		dumpOutput();
		Integer n = Deserializer.deserialize(in, Integer.class);
		log.debug("Integer: {} {}", n, num);
		Assert.assertEquals(n.intValue(), num);
		resetOutput();
	}

	@SuppressWarnings({})
	public void testSimpleReference() {
		Map<String, Object> mapIn = new HashMap<String, Object>();
		Object bean = new SimpleJavaBean();
		mapIn.put("thebean", bean);
		mapIn.put("thesamebeanagain", bean);
		// mapIn.put("thismap",mapIn);
		Serializer.serialize(out, mapIn);

		dumpOutput();
		Map<?, ?> mapOut = Deserializer.deserialize(in, Map.class);
		Assert.assertNotNull(mapOut);
		Assert.assertEquals(mapIn.size(), mapOut.size());

		Set<?> entrySet = mapOut.entrySet();
		Iterator<?> it = entrySet.iterator();
		while (it.hasNext()) {
			Map.Entry<?, ?> entry = (Map.Entry<?, ?>) it.next();
			String propOut = (String) entry.getKey();
			SimpleJavaBean valueOut = (SimpleJavaBean) entry.getValue();
			Assert.assertNotNull("couldn't get output bean", valueOut);

			Assert.assertTrue(mapIn.containsKey(propOut));
			SimpleJavaBean valueIn = (SimpleJavaBean) mapIn.get(propOut);
			Assert.assertNotNull("couldn't get input bean", valueIn);
			Assert.assertEquals(valueOut.getNameOfBean(), valueIn.getNameOfBean());
		}
		resetOutput();

	}

	public void testString() {
		log.debug("Testing string");
		String inStr = "hello world";
		Serializer.serialize(out, inStr);
		dumpOutput();
		String outStr = Deserializer.deserialize(in, String.class);
		Assert.assertEquals(inStr, outStr);
		resetOutput();
	}

}
