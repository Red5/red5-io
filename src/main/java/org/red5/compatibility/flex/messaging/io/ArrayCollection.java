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

package org.red5.compatibility.flex.messaging.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.red5.io.amf3.IDataInput;
import org.red5.io.amf3.IDataOutput;
import org.red5.io.amf3.IExternalizable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flex <code>ArrayCollection</code> compatibility class.
 *  
 * @see <a href="http://help.adobe.com/en_US/FlashPlatform/reference/actionscript/3/mx/collections/ArrayCollection.html">ArrayCollection</a>
 *  
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 * @author Paul Gregoire (mondain@gmail.com)
 * @param <T> type of collection elements
 */
public class ArrayCollection<T> implements Collection<T>, List<T>, IExternalizable {

	private static final Logger log = LoggerFactory.getLogger(ArrayCollection.class);

	private ArrayList<T> source;

	public ArrayCollection() {
		this.source = new ArrayList<T>();
	}

	public ArrayCollection(T[] source) {
		this.source = new ArrayList<T>(source.length);
		this.source.addAll(Arrays.asList(source));
	}

	public void setSource(T[] source) {
		this.source = new ArrayList<T>(source.length);
		this.source.addAll(Arrays.asList(source));		
	}
	
	public int size() {
		return source.size();
	}

	public boolean isEmpty() {
		return source == null ? true : source.isEmpty();
	}

	public boolean contains(Object o) {
		return source.contains(o);
	}

	public Iterator<T> iterator() {
		return source.iterator();
	}

	@SuppressWarnings("unchecked")
	public T[] toArray() {
		return (T[]) source.toArray();
	}

	@SuppressWarnings("hiding")
	public <T> T[] toArray(T[] a) {
		return source.toArray(a);
	}

	public boolean add(T e) {
		return source.add(e);
	}

	public boolean remove(Object o) {
		return source.remove(o);
	}

	public boolean containsAll(Collection<?> c) {
		return source.containsAll(c);
	}

	public boolean addAll(Collection<? extends T> c) {
		return source.addAll(c);
	}

	public boolean removeAll(Collection<?> c) {
		return source.removeAll(c);
	}

	public boolean retainAll(Collection<?> c) {
		return source.retainAll(c);
	}

	public void clear() {
		if (source != null) {
			source.clear();
		}
	}

	public boolean addAll(int index, Collection<? extends T> c) {
		return source.addAll(index, c);
	}

	public T get(int index) {
		return source.get(index);
	}

	public T set(int index, T element) {
		return source.set(index, element);
	}

	public void add(int index, T element) {
		source.add(index, element);
	}

	public T remove(int index) {
		return source.remove(index);
	}

	public int indexOf(Object o) {
		return source.indexOf(o);
	}

	public int lastIndexOf(Object o) {
		return source.lastIndexOf(o);
	}

	public ListIterator<T> listIterator() {
		return source.listIterator();
	}

	public ListIterator<T> listIterator(int index) {
		return source.listIterator(index);
	}

	public List<T> subList(int fromIndex, int toIndex) {
		return source.subList(fromIndex, toIndex);
	}	

	@SuppressWarnings("unchecked")
	public void readExternal(IDataInput input) {
		log.debug("readExternal");
		if (source == null) {
			source = (ArrayList<T>) input.readObject();
		} else {
			source.clear();
			source.addAll((ArrayList<T>) input.readObject());
		}
	}

	public void writeExternal(IDataOutput output) {
		log.debug("writeExternal");
		output.writeObject(source);
	}

}
