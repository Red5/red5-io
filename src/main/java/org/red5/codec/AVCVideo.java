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
 * Red5 video codec for the AVC (h264) video format. Stores DecoderConfigurationRecord and last keyframe.
 *
 * @author Tiago Jacobs (tiago@imdt.com.br)
 * @author Paul Gregoire (mondain@gmail.com) 
 */
public class AVCVideo implements IVideoStreamCodec {

	private static Logger log = LoggerFactory.getLogger(AVCVideo.class);

	/**
	 * AVC video codec constant
	 */
	static final String CODEC_NAME = "AVC";

	/** Last keyframe found */
	private FrameData keyframe;
	
	/** Video decoder configuration data */
	private FrameData decoderConfiguration;

	/** Constructs a new AVCVideo. */
	public AVCVideo() {
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
		keyframe = new FrameData();
		decoderConfiguration = new FrameData();
	}

	/** {@inheritDoc} */
	public boolean canHandleData(IoBuffer data) {
		if (data.limit() == 0) {
			// Empty buffer
			return false;
		}
		byte first = data.get();
		boolean result = ((first & 0x0f) == VideoCodec.AVC.getId());
		data.rewind();
		return result;
	}

	/** {@inheritDoc} */
	public boolean addData(IoBuffer data) {
		if (data.limit() > 0) {
			//ensure that we can "handle" the data
    		if (!canHandleData(data)) {
    			return false;
    		}
    		// get frame type
    		byte frameType = data.get();
    		// check for keyframe
    		if ((frameType & 0xf0) == FLV_FRAME_KEY) {
    			log.trace("Key frame found");
				byte AVCPacketType = data.get();
				// rewind
				data.rewind();
				// sequence header / here comes a AVCDecoderConfigurationRecord
				log.debug("AVCPacketType: {}", AVCPacketType);
				if (AVCPacketType == 0) {
					log.trace("Decoder configuration found");
					// Store AVCDecoderConfigurationRecord data
					decoderConfiguration.setData(data);
					// rewind
					data.rewind();
				}
   				// store last keyframe
   				keyframe.setData(data);
    		}
    		// finished with the data, rewind one last time
    		data.rewind();
		}
		return true;
	}

	/** {@inheritDoc} */
	public IoBuffer getKeyframe() {
		return keyframe.getFrame();
	}

	/** {@inheritDoc} */
	public IoBuffer getDecoderConfiguration() {
		return decoderConfiguration.getFrame();
	}
	
}
