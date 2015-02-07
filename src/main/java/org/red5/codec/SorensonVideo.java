/*
 * RED5 Open Source Flash Server - https://github.com/Red5/
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

package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;

/**
 * Red5 video codec for the sorenson video format.
 *
 * VERY simple implementation, just stores last keyframe.
 *
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 * @author Paul Gregoire (mondain@gmail.com) 
 */
public class SorensonVideo implements IVideoStreamCodec {

    /**
     * Sorenson video codec constant
     */
	static final String CODEC_NAME = "SorensonVideo";

    /**
     * Block of data
     */
	private byte[] blockData;
    /**
     * Number of data blocks
     */
	private int dataCount;
    /**
     * Data block size
     */
	private int blockSize;

	/** Constructs a new SorensonVideo. */
    public SorensonVideo() {
		this.reset();
	}

	/** {@inheritDoc} */
    public String getName() {
		return CODEC_NAME;
	}

	/** {@inheritDoc} */
    public boolean canDropFrames() {
		return true;
	}

	/** {@inheritDoc} */
    public void reset() {
		this.blockData = null;
		this.blockSize = 0;
		this.dataCount = 0;
	}

	/** {@inheritDoc} */
    public boolean canHandleData(IoBuffer data) {
		if (data.limit() == 0) {
			// Empty buffer
			return false;
		}

		byte first = data.get();
		boolean result = ((first & 0x0f) == VideoCodec.H263.getId());
		data.rewind();
		return result;
	}

	/** {@inheritDoc} */
    public boolean addData(IoBuffer data) {
		if (data.limit() == 0) {
			// Empty buffer
			return true;
		}

		if (!this.canHandleData(data)) {
			return false;
		}

		byte first = data.get();
		data.rewind();
		if ((first & 0xf0) != FLV_FRAME_KEY) {
			// Not a keyframe
			return true;
		}

		// Store last keyframe
		this.dataCount = data.limit();
		if (this.blockSize < this.dataCount) {
			this.blockSize = this.dataCount;
			this.blockData = new byte[this.blockSize];
		}

		data.get(this.blockData, 0, this.dataCount);
		data.rewind();
		return true;
	}

	/** {@inheritDoc} */
    public IoBuffer getKeyframe() {
		if (this.dataCount == 0) {
			return null;
		}

		IoBuffer result = IoBuffer.allocate(this.dataCount);
		result.put(this.blockData, 0, this.dataCount);
		result.rewind();
		return result;
	}
    
	public IoBuffer getDecoderConfiguration() {
		return null;
	}    
    
}
