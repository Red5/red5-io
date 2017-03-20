package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IoConstants;

public class AbstractVideo implements IVideoStreamCodec, IoConstants {

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void reset() {
    }

    @Override
    public boolean canDropFrames() {
        return false;
    }

    @Override
    public boolean canHandleData(IoBuffer data) {
        return false;
    }

    @Override
    public boolean addData(IoBuffer data) {
        return false;
    }

    @Override
    public boolean addData(IoBuffer data, int timestamp) {
        return false;
    }

    @Override
    public IoBuffer getDecoderConfiguration() {
        return null;
    }

    @Override
    public IoBuffer getKeyframe() {
        return null;
    }

    @Override
    public FrameData[] getKeyframes() {
        return null;
    }

    @Override
    public int getNumInterframes() {
        return 0;
    }

    @Override
    public FrameData getInterframe(int idx) {
        return null;
    }

}
