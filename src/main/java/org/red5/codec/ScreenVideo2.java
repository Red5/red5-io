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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Red5 video codec for the screen capture format. 
 * 
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class ScreenVideo2 implements IVideoStreamCodec {

	private Logger log = LoggerFactory.getLogger(ScreenVideo2.class);
	
    /**
     * FLV codec name constant
     */
	static final String CODEC_NAME = "ScreenVideo2";
	
    /**
     * Block data
     */
	private byte[] blockData;
    /**
     * Block size
     */
	private int[] blockSize;
    /**
     * Video width
     */
	private int width;
    /**
     * Video height
     */
	private int height;
    /**
     * Width info
     */
	private int widthInfo;
    /**
     * Height info
     */
	private int heightInfo;
    /**
     * Block width
     */
	private int blockWidth;
    /**
     * Block height
     */
	private int blockHeight;
    /**
     * Number of blocks
     */
	private int blockCount;
    /**
     * Block data size
     */
	private int blockDataSize;
    /**
     * Total block data size
     */
	private int totalBlockDataSize;
	/**
	 * Special Info 1
	 */
	private byte specInfo1;
	/**
	 * Special Info 2
	 */
	private byte specInfo2;

	/** Constructs a new ScreenVideo2. */
    public ScreenVideo2() {
		this.reset();
	}

	/** {@inheritDoc} */
    public String getName() {
		return CODEC_NAME;
	}

	/** {@inheritDoc} */
    public void reset() {
		this.blockData = null;
		this.blockSize = null;
		this.width = 0;
		this.height = 0;
		this.widthInfo = 0;
		this.heightInfo = 0;
		this.blockWidth = 0;
		this.blockHeight = 0;
		this.blockCount = 0;
		this.blockDataSize = 0;
		this.totalBlockDataSize = 0;
	}

	/** {@inheritDoc} */
    public boolean canHandleData(IoBuffer data) {
		byte first = data.get();
		boolean result = ((first & 0x0f) == VideoCodec.SCREEN_VIDEO2.getId());
		data.rewind();
		return result;
	}

	/** {@inheritDoc} */
    public boolean canDropFrames() {
		return false;
	}

	/*
	 * This uses the same algorithm as "compressBound" from zlib
	 */
	private int maxCompressedSize(int size) {
		return size + (size >> 12) + (size >> 14) + 11;
	}

    /**
     * Update total block size
     * @param data      Byte buffer
     */
	private void updateSize(IoBuffer data) {
		this.widthInfo = data.getShort();
		this.heightInfo = data.getShort();
		// extract width and height of the frame
		this.width = this.widthInfo & 0xfff;
		this.height = this.heightInfo & 0xfff;
		// calculate size of blocks
		this.blockWidth = this.widthInfo & 0xf000; 
		this.blockWidth = (this.blockWidth >> 12) + 1;
		this.blockWidth <<= 4;
		
		this.blockHeight = this.heightInfo & 0xf000;
		this.blockHeight = (this.blockHeight >> 12) + 1;
		this.blockHeight <<= 4;

		int xblocks = this.width / this.blockWidth;
		if ((this.width % this.blockWidth) != 0) {
			// partial block
			xblocks += 1;
		}

		int yblocks = this.height / this.blockHeight;
		if ((this.height % this.blockHeight) != 0) {
			// partial block
			yblocks += 1;
		}

		this.blockCount = xblocks * yblocks;

		int blockSize = this.maxCompressedSize(this.blockWidth
				* this.blockHeight * 3);
		int totalBlockSize = blockSize * this.blockCount;
		if (this.totalBlockDataSize != totalBlockSize) {
			log.info("Allocating memory for {} compressed blocks.", this.blockCount);
			this.blockDataSize = blockSize;
			this.totalBlockDataSize = totalBlockSize;
			this.blockData = new byte[blockSize * this.blockCount];
			this.blockSize = new int[blockSize * this.blockCount];
			// Reset the sizes to zero
			for (int idx = 0; idx < this.blockCount; idx++) {
				this.blockSize[idx] = 0;
			}
		}
	}

	/** {@inheritDoc} */
    public boolean addData(IoBuffer data) {
		if (!this.canHandleData(data)) {
			return false;
		}

		data.get();
		this.updateSize(data);
		int idx = 0;
		int pos = 0;
		byte[] tmpData = new byte[this.blockDataSize];
		
		// reserved (6) has iframeimage (1) has palleteinfo (1)
		this.specInfo1 = data.get();

		int countBlocks = this.blockCount;
		while (data.remaining() > 0 && countBlocks > 0) {
			short size = data.getShort();

			countBlocks--;
			if (size == 0) {
				// Block has not been modified
				idx += 1;
				pos += this.blockDataSize;
				continue;
			}
			else
			{
				// imageformat
				this.specInfo2 = data.get();
				size--;
			}

			// Store new block data
			this.blockSize[idx] = size;
			data.get(tmpData, 0, size);
			System.arraycopy(tmpData, 0, this.blockData, pos, size);
			idx += 1;
			pos += this.blockDataSize;
		}

		data.rewind();
		return true;
	}

	/** {@inheritDoc} */
    public IoBuffer getKeyframe() {
		IoBuffer result = IoBuffer.allocate(1024);
		result.setAutoExpand(true);

		// Header
		result.put((byte) (FLV_FRAME_KEY | VideoCodec.SCREEN_VIDEO2.getId()));

		// Frame size
		result.putShort((short) this.widthInfo);
		result.putShort((short) this.heightInfo);
		
		// reserved (6) has iframeimage (1) has palleteinfo (1)
		result.put(this.specInfo1);

		// Get compressed blocks
		byte[] tmpData = new byte[this.blockDataSize];
		int pos = 0;
		for (int idx = 0; idx < this.blockCount; idx++) {
			int size = this.blockSize[idx];
			if (size == 0) {
				// this should not happen: no data for this block
				return null;
			}

			result.putShort((short)(size + 1));
			
			// IMAGEFORMAT
			// reserved(3) color depth(2) has diff blocks(1)
			// ZlibPrimeCompressCurrent(1) ZlibPrimeCompressPrevious (1)
			result.put(this.specInfo2);
			
			System.arraycopy(this.blockData, pos, tmpData, 0, size);
			result.put(tmpData, 0, size);
			pos += this.blockDataSize;
		}

		result.rewind();
		return result;
	}

	public IoBuffer getDecoderConfiguration() {
		return null;
	}
    
}
