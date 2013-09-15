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

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.amf.Input;
import org.red5.io.amf.Output;
import org.red5.io.utils.HexDump;

/*
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
*/
public class AMFIOTest extends AbstractIOTest {

	IoBuffer buf;

	/** {@inheritDoc} */
	@Override
	void dumpOutput() {
		buf.flip();
		System.err.println(HexDump.formatHexDump(buf.getHexDump()));
	}

	/** {@inheritDoc} */
	@Override
	void resetOutput() {
		setupIO();
	}

	/** {@inheritDoc} */
	@Override
	void setupIO() {
		buf = IoBuffer.allocate(0); // 1kb
		buf.setAutoExpand(true);
		in = new Input(buf);
		out = new Output(buf);
	}

}
