/*
 * RED5 Open Source Flash Server - https://github.com/Red5/
 * 
 * Copyright 2006-2016 by respective authors (see below). All rights reserved.
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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * Storage for frames buffered since last key frame
     */
    private final CopyOnWriteArrayList<FrameData> interframes = new CopyOnWriteArrayList<FrameData>();

    /**
     * Number of frames buffered since last key frame
     */
    private final AtomicInteger numInterframes = new AtomicInteger(0);

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
        boolean result = false;
        if (data.limit() > 0) {
            // read the first byte and ensure its AVC / h.264 type
            result = ((data.get() & 0x0f) == VideoCodec.AVC.getId());
            data.rewind();
        }
        return result;
    }

    /** {@inheritDoc} */
    public boolean addData(IoBuffer data) {
        if (data.limit() > 0) {
            // get frame type
            byte frameType = data.get();
            if ((frameType & 0x0f) == VideoCodec.AVC.getId()) {
                // check for keyframe
                if ((frameType & 0xf0) == FLV_FRAME_KEY) {
                    log.trace("Key frame found");
                    numInterframes.set(0);
                    interframes.clear();
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
                } else {
                    data.rewind();
                    try {
                        int lastInterframe = numInterframes.getAndIncrement();
                        log.trace("Buffering interframe #{}", lastInterframe);
                        if (lastInterframe < interframes.size()) {
                            interframes.get(lastInterframe).setData(data);
                        } else {
                            interframes.add(new FrameData(data));
                        }
                        //                        if (log.isTraceEnabled()) {
                        //                            log.trace("Interframes size: {} last: {}", interframes.size(), lastInterframe);
                        //                        }
                    } catch (Throwable e) {
                        log.error("Failed to buffer interframe", e);
                    }
                }
                // finished with the data, rewind one last time
                data.rewind();
            } else {
                // not AVC data
                log.debug("Non-AVC data, rejecting");
                return false;
            }
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

    /** {@inheritDoc} */
    public int getNumInterframes() {
        return numInterframes.get();
    }

    /** {@inheritDoc} */
    public FrameData getInterframe(int index) {
        //        if (log.isTraceEnabled()) {
        //            log.trace("getInterframe: {} interframes count: {} has frame: {}", index, numInterframes.get(), (index < numInterframes.get()));
        //        }
        if (index < numInterframes.get()) {
            return interframes.get(index);
        }
        return null;
    }
}
