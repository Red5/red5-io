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

import java.util.LinkedList;
import java.util.List;

import org.red5.io.mock.Input;
import org.red5.io.mock.Mock;
import org.red5.io.mock.Output;

/*
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
*/
public class MockIOTest extends AbstractIOTest {

	protected List<Object> list;

	/** {@inheritDoc} */
	@Override
	void setupIO() {
		list = new LinkedList<Object>();
		in = new Input(list);
		out = new Output(list);
	}

	/** {@inheritDoc} */
	@Override
	void dumpOutput() {
		System.out.println(Mock.listToString(list));
	}

	/** {@inheritDoc} */
	@Override
	void resetOutput() {
		setupIO();
	}

}
