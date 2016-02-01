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

package org.red5.io.flv.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;
import java.nio.channels.ClosedChannelException;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.codec.AudioCodec;
import org.red5.io.IStreamableFile;
import org.red5.io.ITag;
import org.red5.io.ITagWriter;
import org.red5.io.amf.Input;
import org.red5.io.amf.Output;
import org.red5.io.flv.FLVHeader;
import org.red5.io.flv.IFLV;
import org.red5.io.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Writer is used to write the contents of a FLV file
 *
 * @author The Red5 Project
 * @author Dominick Accattato (daccattato@gmail.com)
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 * @author Tiago Jacobs (tiago@imdt.com.br)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class FLVWriter implements ITagWriter {

    private static Logger log = LoggerFactory.getLogger(FLVWriter.class);

    /**
     * Length of the flv header in bytes
     */
    private final static int HEADER_LENGTH = 9;

    /**
     * Length of the flv tag in bytes
     */
    private final static int TAG_HEADER_LENGTH = 11;

    /**
     * Position of the meta data tag in our file.
     */
    private final static int META_POSITION = 13;

    /**
     * For now all recorded streams carry a stream id of 0.
     */
    private final static byte[] DEFAULT_STREAM_ID = new byte[] { (byte) (0 & 0xff), (byte) (0 & 0xff), (byte) (0 & 0xff) };

    /**
     * FLV object
     */
    private IFLV flv;

    /**
     * Number of bytes written
     */
    private volatile long bytesWritten;

    /**
     * Position in file
     */
    private int offset;

    /**
     * Position in file
     */
    private int timeOffset;

    /**
     * Id of the video codec used.
     */
    private volatile int videoCodecId = -1;

    /**
     * Id of the audio codec used.
     */
    private volatile int audioCodecId = -1;

    /**
     * Sampling rate
     */
    private volatile int soundRate;

    /**
     * Size of each audio sample
     */
    private volatile int soundSize;

    /**
     * Mono (0) or stereo (1) sound
     */
    private volatile boolean soundType;

    /**
     * Are we appending to an existing file?
     */
    private boolean append;

    /**
     * Duration of the file.
     */
    private int duration;

    /**
     * Size of video data
     */
    private int videoDataSize = 0;

    /**
     * Size of audio data
     */
    private int audioDataSize = 0;

    /**
     * Flv file.
     */
    private RandomAccessFile file;

    /**
     * File to which stream data is stored without an flv header or metadata.
     */
    private RandomAccessFile dataFile;

    private Map<Long, ITag> metaTags = new HashMap<Long, ITag>();

    // path to the original file passed to the writer
    private String filePath;

    private final Semaphore lock = new Semaphore(1, true);

    // the size of the last tag written, which includes the tag header length
    private volatile int lastTagSize;

    /**
     * Creates writer implementation with for a given file
     * 
     * @param filePath
     *            path to existing file
     */
    public FLVWriter(String filePath) {
        this.filePath = filePath;
        log.debug("Writing to: {}", filePath);
        try {
            // temporary data file for storage of stream data
            File dat = new File(filePath + ".ser");
            this.dataFile = new RandomAccessFile(dat, "rw");
        } catch (Exception e) {
            log.error("Failed to create FLV writer", e);
        }
    }

    /**
     * Creates writer implementation with given file and flag indicating whether or not to append.
     *
     * FLV.java uses this constructor so we have access to the file object
     *
     * @param file
     *            File output stream
     * @param append
     *            true if append to existing file
     */
    public FLVWriter(File file, boolean append) {
        filePath = file.getAbsolutePath();
        log.debug("Writing to: {}", filePath);
        try {
            this.append = append;
            if (append) {
                // if we are appending get the last tags timestamp to use as offset
                timeOffset = FLVReader.getDuration(file);
                // set duration to last timestamp value
                duration = timeOffset;
                log.debug("Duration: {}", timeOffset);
                // grab the file we will append to
                this.dataFile = new RandomAccessFile(file, "rw");
                if (!file.exists() || !file.canRead() || !file.canWrite()) {
                    log.warn("File does not exist or cannot be accessed");
                } else {
                    log.trace("File size: {} last modified: {}", file.length(), file.lastModified());
                    // update the bytes written so we write to the correct starting position
                    bytesWritten = file.length();
                }
                // if duration is 0 then we probably have larger issues with this file
                if (duration == 0) {
                    // seek to where metadata would normally start
                    dataFile.seek(META_POSITION);
                }
            } else {
                // temporary data file for storage of stream data
                File dat = new File(filePath + ".ser");
                if (dat.exists()) {
                    dat.delete();
                    dat.createNewFile();
                }
                this.dataFile = new RandomAccessFile(dat, "rw");
            }
        } catch (Exception e) {
            log.error("Failed to create FLV writer", e);
        }
    }

    /**
     * Writes the header bytes
     *
     * @throws IOException
     *             Any I/O exception
     */
    public void writeHeader() throws IOException {
        FLVHeader flvHeader = new FLVHeader();
        flvHeader.setFlagAudio(audioCodecId != -1 ? true : false);
        flvHeader.setFlagVideo(videoCodecId != -1 ? true : false);
        // create a buffer
        ByteBuffer header = ByteBuffer.allocate(HEADER_LENGTH + 4); // FLVHeader (9 bytes) + PreviousTagSize0 (4 bytes)
        flvHeader.write(header);
        // the final version of the file will go here
        this.file = new RandomAccessFile(filePath, "rw");
        // write header to output channel
        file.setLength(HEADER_LENGTH + 4);
        if (header.hasArray()) {
            log.debug("Header bytebuffer has a backing array");
            file.write(header.array());
        } else {
            log.debug("Header bytebuffer does not have a backing array");
            byte[] tmp = new byte[HEADER_LENGTH + 4];
            header.get(tmp);
            file.write(tmp);
        }
        bytesWritten = file.length();
        assert ((HEADER_LENGTH + 4) - bytesWritten == 0);
        log.debug("Header size: {} bytes written: {}", (HEADER_LENGTH + 4), bytesWritten);
        header.clear();
        header = null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean writeTag(ITag tag) throws IOException {
        try {
            lock.acquire();
            /*
             * Tag header = 11 bytes
             * |-|---|----|---|
             *    0 = type
             *  1-3 = data size
             *  4-7 = timestamp
             * 8-10 = stream id (always 0)
             * Tag data = variable bytes
             * Previous tag = 4 bytes (tag header size + tag data size)
             */
            log.trace("writeTag: {}", tag);
            long prevBytesWritten = bytesWritten;
            log.trace("Previous bytes written: {}", prevBytesWritten);
            // skip tags with no data
            int bodySize = tag.getBodySize();
            log.debug("Tag body size: {}", bodySize);
            // verify previous tag size stored in incoming tag
            int previousTagSize = tag.getPreviousTagSize();
            if (previousTagSize != lastTagSize) {
                // use the last tag size
                log.debug("Incoming previous tag size: {} does not match current value for last tag size: {}", previousTagSize, lastTagSize);
            }
            // ensure that the channel is still open
            if (dataFile != null) {
                log.debug("Current file position: {}", dataFile.getChannel().position());
                // get the data type
                byte dataType = tag.getDataType();
                // when tag is ImmutableTag which is in red5-server-common.jar, tag.getBody().reset() will throw InvalidMarkException because 
                // ImmutableTag.getBody() returns a new IoBuffer instance everytime.
                IoBuffer tagBody = tag.getBody();
                // if we're writing non-meta tags do seeking and tag size update
                if (dataType != ITag.TYPE_METADATA) {
                    // get the current file offset
                    long fileOffset = dataFile.getFilePointer();
                    log.debug("Current file offset: {} expected offset: {}", fileOffset, prevBytesWritten);
                    if (fileOffset < prevBytesWritten) {
                        log.debug("Seeking to expected offset");
                        // it's necessary to seek to the length of the file
                        // so that we can append new tags
                        dataFile.seek(prevBytesWritten);
                        log.debug("New file position: {}", dataFile.getChannel().position());
                    }
                } else {
                    tagBody.mark();
                    // get input data
                    Input metadata = new Input(tagBody);
                    // initialize type so that readString knows what to do
                    metadata.readDataType();
                    String metaType = metadata.readString();
                    log.debug("Metadata tag type: {}", metaType);
                    try {
                        tagBody.reset();
                    } catch (InvalidMarkException e) {
                        //TDJ: this error is probably caused by the setter of limit on readString method
                        log.debug("Exception reseting position of buffer: {}", e.getMessage(), e);
                    }
                    if (!"onCuePoint".equals(metaType)) {
                        // store any incoming onMetaData tags until we close the file, allow onCuePoint tags to continue
                        metaTags.put(System.currentTimeMillis(), tag);
                        return true;
                    }
                }
                // set a var holding the entire tag size including the previous tag length
                int totalTagSize = TAG_HEADER_LENGTH + bodySize + 4;
                // resize
                dataFile.setLength(dataFile.length() + totalTagSize);
                // create a buffer for this tag
                ByteBuffer tagBuffer = ByteBuffer.allocate(totalTagSize);
                // get the timestamp
                int timestamp = tag.getTimestamp() + timeOffset;
                // allow for empty tag bodies
                byte[] bodyBuf = null;
                if (bodySize > 0) {
                    // create an array big enough
                    bodyBuf = new byte[bodySize];
                    // put the bytes into the array
                    tagBody.get(bodyBuf);
                    // get the audio or video codec identifier
                    if (dataType == ITag.TYPE_AUDIO) {
                        audioDataSize += bodySize;
                        if (audioCodecId == -1) {
                            int id = bodyBuf[0] & 0xff; // must be unsigned
                            audioCodecId = (id & ITag.MASK_SOUND_FORMAT) >> 4;
                            log.debug("Audio codec id: {}", audioCodecId);
                            // if aac use defaults
                            if (audioCodecId == AudioCodec.AAC.getId()) {
                                log.trace("AAC audio type");
                                // Flash Player ignores	these values and extracts the channel and sample rate data encoded in the AAC bit stream
                                soundRate = 44100;
                                soundSize = 16;
                                soundType = true;
                            } else if (audioCodecId == AudioCodec.SPEEX.getId()) {
                                log.trace("Speex audio type");
                                soundRate = 5500; // actually 16kHz
                                soundSize = 16;
                                soundType = false; // mono								
                            } else {
                                switch ((id & ITag.MASK_SOUND_RATE) >> 2) {
                                    case ITag.FLAG_RATE_5_5_KHZ:
                                        soundRate = 5500;
                                        break;
                                    case ITag.FLAG_RATE_11_KHZ:
                                        soundRate = 11000;
                                        break;
                                    case ITag.FLAG_RATE_22_KHZ:
                                        soundRate = 22000;
                                        break;
                                    case ITag.FLAG_RATE_44_KHZ:
                                        soundRate = 44100;
                                        break;
                                }
                                log.debug("Sound rate: {}", soundRate);
                                switch ((id & ITag.MASK_SOUND_SIZE) >> 1) {
                                    case ITag.FLAG_SIZE_8_BIT:
                                        soundSize = 8;
                                        break;
                                    case ITag.FLAG_SIZE_16_BIT:
                                        soundSize = 16;
                                        break;
                                }
                                log.debug("Sound size: {}", soundSize);
                                // mono == 0 // stereo == 1
                                soundType = (id & ITag.MASK_SOUND_TYPE) > 0;
                                log.debug("Sound type: {}", soundType);
                            }
                        }
                        // XXX is AACPacketType needed here?
                    } else if (dataType == ITag.TYPE_VIDEO) {
                        videoDataSize += bodySize;
                        if (videoCodecId == -1) {
                            int id = bodyBuf[0] & 0xff; // must be unsigned
                            videoCodecId = id & ITag.MASK_VIDEO_CODEC;
                            log.debug("Video codec id: {}", videoCodecId);
                        }
                    }
                }
                // Data Type
                IOUtils.writeUnsignedByte(tagBuffer, dataType); //1
                // Body Size - Length of the message. Number of bytes after StreamID to end of tag 
                // (Equal to length of the tag - 11) 
                IOUtils.writeMediumInt(tagBuffer, bodySize); //3
                // Timestamp
                IOUtils.writeExtendedMediumInt(tagBuffer, timestamp); //4
                // Stream id
                tagBuffer.put(DEFAULT_STREAM_ID); //3
                if (log.isTraceEnabled()) {
                    log.trace("Tag buffer (after tag header) limit: {} remaining: {}", tagBuffer.limit(), tagBuffer.remaining());
                }
                // get the body if we have one
                if (bodyBuf != null) {
                    tagBuffer.put(bodyBuf);
                    if (log.isTraceEnabled()) {
                        log.trace("Tag buffer (after body) limit: {} remaining: {}", tagBuffer.limit(), tagBuffer.remaining());
                    }
                }
                // we add the tag size
                tagBuffer.putInt(TAG_HEADER_LENGTH + bodySize);
                if (log.isTraceEnabled()) {
                    log.trace("Tag buffer (after prev tag size) limit: {} remaining: {}", tagBuffer.limit(), tagBuffer.remaining());
                }
                // flip so we can process from the beginning
                tagBuffer.flip();
                if (log.isDebugEnabled()) {
                    //StringBuilder sb = new StringBuilder();
                    //HexDump.dumpHex(sb, tagBuffer.array());
                    //log.debug("\n{}", sb);
                }
                // write the tag
                dataFile.write(tagBuffer.array());
                bytesWritten = dataFile.length();
                if (log.isTraceEnabled()) {
                    log.trace("Tag written, check value: {} (should be 0)", (bytesWritten - prevBytesWritten) - totalTagSize);
                }
                // store new previous tag size
                lastTagSize = TAG_HEADER_LENGTH + bodySize;
                tagBuffer.clear();
                // update the duration
                duration = Math.max(duration, timestamp);
                log.debug("Writer duration: {}", duration);
                // validate written amount
                if ((bytesWritten - prevBytesWritten) != totalTagSize) {
                    log.debug("Not all of the bytes appear to have been written, prev-current: {}", (bytesWritten - prevBytesWritten));
                }
                return true;
            } else {
                // throw an exception and let them know the cause
                throw new IOException("FLV write channel has been closed", new ClosedChannelException());
            }
        } catch (InterruptedException e) {
            log.warn("Exception acquiring lock", e);
        } finally {
            // update the file information
            updateInfoFile();
            // release lock
            lock.release();
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean writeTag(byte dataType, IoBuffer data) throws IOException {
        if (timeOffset == 0) {
            timeOffset = (int) System.currentTimeMillis();
        }
        try {
            lock.acquire();
            /*
             * Tag header = 11 bytes
             * |-|---|----|---|
             *    0 = type
             *  1-3 = data size
             *  4-7 = timestamp
             * 8-10 = stream id (always 0)
             * Tag data = variable bytes
             * Previous tag = 4 bytes (tag header size + tag data size)
             */
            if (log.isTraceEnabled()) {
                log.trace("writeTag - type: {} data: {}", dataType, data);
            }
            long prevBytesWritten = bytesWritten;
            log.trace("Previous bytes written: {}", prevBytesWritten);
            // skip tags with no data
            int bodySize = data.limit();
            log.debug("Tag body size: {}", bodySize);
            // ensure that the channel is still open
            if (dataFile != null) {
                log.debug("Current file position: {}", dataFile.getChannel().position());
                // if we're writing non-meta tags do seeking and tag size update
                if (dataType != ITag.TYPE_METADATA) {
                    // get the current file offset
                    long fileOffset = dataFile.getFilePointer();
                    log.debug("Current file offset: {} expected offset: {}", fileOffset, prevBytesWritten);
                    if (fileOffset < prevBytesWritten) {
                        log.debug("Seeking to expected offset");
                        // it's necessary to seek to the length of the file
                        // so that we can append new tags
                        dataFile.seek(prevBytesWritten);
                        log.debug("New file position: {}", dataFile.getChannel().position());
                    }
                } else {
                    data.mark();
                    // get input data
                    Input metadata = new Input(data);
                    // initialize type so that readString knows what to do
                    metadata.readDataType();
                    String metaType = metadata.readString();
                    log.debug("Metadata tag type: {}", metaType);
                    data.reset();
                    if (!"onCuePoint".equals(metaType)) {
                        // store any incoming onMetaData tags until we close the file, allow onCuePoint tags to continue
                        //metaTags.put(System.currentTimeMillis(), tag);
                        return true;
                    }
                }
                // set a var holding the entire tag size including the previous tag length
                int totalTagSize = TAG_HEADER_LENGTH + bodySize + 4;
                // resize
                dataFile.setLength(dataFile.length() + totalTagSize);
                // create a buffer for this tag
                ByteBuffer tagBuffer = ByteBuffer.allocate(totalTagSize);
                // Data Type
                IOUtils.writeUnsignedByte(tagBuffer, dataType); //1
                // Body Size - Length of the message. Number of bytes after StreamID to end of tag 
                // (Equal to length of the tag - 11) 
                IOUtils.writeMediumInt(tagBuffer, bodySize); //3
                // Timestamp
                int timestamp = (int) (System.currentTimeMillis() - timeOffset);
                IOUtils.writeExtendedMediumInt(tagBuffer, timestamp); //4
                // Stream id
                tagBuffer.put(DEFAULT_STREAM_ID); //3
                log.trace("Tag buffer (after tag header) limit: {} remaining: {}", tagBuffer.limit(), tagBuffer.remaining());
                // get the body if we have one
                if (data.hasArray()) {
                    tagBuffer.put(data.array());
                    log.trace("Tag buffer (after body) limit: {} remaining: {}", tagBuffer.limit(), tagBuffer.remaining());
                }
                // we add the tag size
                tagBuffer.putInt(TAG_HEADER_LENGTH + bodySize);
                log.trace("Tag buffer (after prev tag size) limit: {} remaining: {}", tagBuffer.limit(), tagBuffer.remaining());
                // flip so we can process from the beginning
                tagBuffer.flip();
                if (log.isDebugEnabled()) {
                    //StringBuilder sb = new StringBuilder();
                    //HexDump.dumpHex(sb, tagBuffer.array());
                    //log.debug("\n{}", sb);
                }
                // write the tag
                dataFile.write(tagBuffer.array());
                bytesWritten = dataFile.length();
                if (log.isTraceEnabled()) {
                    log.trace("Tag written, check value: {} (should be 0)", (bytesWritten - prevBytesWritten) - totalTagSize);
                }
                // store new previous tag size
                lastTagSize = TAG_HEADER_LENGTH + bodySize;
                tagBuffer.clear();
                // update the duration
                duration = Math.max(duration, timestamp);
                log.debug("Writer duration: {}", duration);
                // validate written amount
                if ((bytesWritten - prevBytesWritten) != totalTagSize) {
                    log.debug("Not all of the bytes appear to have been written, prev-current: {}", (bytesWritten - prevBytesWritten));
                }
                return true;
            } else {
                // throw an exception and let them know the cause
                throw new IOException("FLV write channel has been closed", new ClosedChannelException());
            }
        } catch (InterruptedException e) {
            log.warn("Exception acquiring lock", e);
        } finally {
            // update the file information
            updateInfoFile();
            // release lock
            lock.release();
        }
        return false;
    }

    /** {@inheritDoc} */
    public boolean writeStream(byte[] b) {
        try {
            dataFile.write(b);
            return true;
        } catch (IOException e) {
            log.error("", e);
        }
        return false;
    }

    /**
     * Write "onMetaData" tag to the file.
     *
     * @param duration
     *            Duration to write in milliseconds.
     * @param videoCodecId
     *            Id of the video codec used while recording.
     * @param audioCodecId
     *            Id of the audio codec used while recording.
     * @throws IOException
     *             if the tag could not be written
     */
    private void writeMetadataTag(double duration, int videoCodecId, int audioCodecId) throws IOException {
        log.debug("writeMetadataTag - duration: {} video codec: {} audio codec: {}", new Object[] { duration, videoCodecId, audioCodecId });
        IoBuffer buf = IoBuffer.allocate(1024);
        buf.setAutoExpand(true);
        Output out = new Output(buf);
        out.writeString("onMetaData");
        Map<Object, Object> params = new HashMap<Object, Object>();
        params.put("server", "Red5");
        params.put("creationdate", GregorianCalendar.getInstance().getTime().toString());
        params.put("duration", (Number) duration);
        if (videoCodecId != -1) {
            params.put("videocodecid", (videoCodecId == 7 ? "avc1" : videoCodecId));
            if (videoDataSize > 0) {
                params.put("videodatarate", 8 * videoDataSize / 1024 / duration); //from bytes to kilobits
            }
        } else {
            // place holder
            params.put("novideocodec", 0);
        }
        if (audioCodecId != -1) {
            params.put("audiocodecid", (audioCodecId == 10 ? "mp4a" : audioCodecId));
            if (audioCodecId == AudioCodec.AAC.getId()) {
                params.put("audiosamplerate", 44100);
                params.put("audiosamplesize", 16);
            } else if (audioCodecId == AudioCodec.SPEEX.getId()) {
                params.put("audiosamplerate", 16000);
                params.put("audiosamplesize", 16);
            } else {
                params.put("audiosamplerate", soundRate);
                params.put("audiosamplesize", soundSize);
            }
            params.put("stereo", soundType);
            if (audioDataSize > 0) {
                params.put("audiodatarate", 8 * audioDataSize / 1024 / duration); //from bytes to kilobits		
            }
        } else {
            // place holder
            params.put("noaudiocodec", 0);
        }
        // this is actual only supposed to be true if the last video frame is a keyframe
        params.put("canSeekToEnd", true);
        out.writeMap(params);
        buf.flip();
        int bodySize = buf.limit();
        log.debug("Metadata size: {}", bodySize);
        // set a var holding the entire tag size including the previous tag length
        int totalTagSize = TAG_HEADER_LENGTH + bodySize + 4;
        // resize
        file.setLength(file.length() + totalTagSize);
        // create a buffer for this tag
        ByteBuffer tagBuffer = ByteBuffer.allocate(totalTagSize);
        // get the timestamp
        int timestamp = 0;
        // create an array big enough
        byte[] bodyBuf = new byte[bodySize];
        // put the bytes into the array
        buf.get(bodyBuf);
        // Data Type
        IOUtils.writeUnsignedByte(tagBuffer, ITag.TYPE_METADATA); //1
        // Body Size - Length of the message. Number of bytes after StreamID to end of tag 
        // (Equal to length of the tag - 11) 
        IOUtils.writeMediumInt(tagBuffer, bodySize); //3
        // Timestamp
        IOUtils.writeExtendedMediumInt(tagBuffer, timestamp); //4
        // Stream id
        tagBuffer.put(DEFAULT_STREAM_ID); //3
        if (log.isTraceEnabled()) {
            log.trace("Tag buffer (after tag header) limit: {} remaining: {}", tagBuffer.limit(), tagBuffer.remaining());
        }
        // get the body
        tagBuffer.put(bodyBuf);
        if (log.isTraceEnabled()) {
            log.trace("Tag buffer (after body) limit: {} remaining: {}", tagBuffer.limit(), tagBuffer.remaining());
        }
        // we add the tag size
        tagBuffer.putInt(TAG_HEADER_LENGTH + bodySize);
        if (log.isTraceEnabled()) {
            log.trace("Tag buffer (after prev tag size) limit: {} remaining: {}", tagBuffer.limit(), tagBuffer.remaining());
        }
        // flip so we can process from the beginning
        tagBuffer.flip();
        // write the tag
        file.write(tagBuffer.array());
        bytesWritten = file.length();
        tagBuffer.clear();
        buf.clear();
    }

    /**
     * update flv file meta data tag
     * 
     * @param flvFile
     * @param duration
     * @param videoCodecId
     * @param audioCodecId
     * @throws IOException
     */
    private void updateMetadataTag(RandomAccessFile flvFile, double duration, int videoCodecId, int audioCodecId) throws IOException {
        log.debug("updateMetadataTag - duration: {} video codec: {} audio codec: {}", new Object[] { duration, videoCodecId, audioCodecId });
        IoBuffer buf = IoBuffer.allocate(512);
        buf.setAutoExpand(true);
        Output out = new Output(buf);
        out.writeString("onMetaData");
        Map<Object, Object> params = new HashMap<Object, Object>();
        params.put("server", "Red5");
        params.put("creationdate", GregorianCalendar.getInstance().getTime().toString());
        params.put("duration", (Number) duration);
        if (videoCodecId != -1) {
            params.put("videocodecid", (videoCodecId == 7 ? "avc1" : videoCodecId));
            if (videoDataSize > 0) {
                params.put("videodatarate", 8 * videoDataSize / 1024 / duration); //from bytes to kilobits
            }
        } else {
            // place holder
            params.put("novideocodec", 0);
        }
        if (audioCodecId != -1) {
            params.put("audiocodecid", (audioCodecId == 10 ? "mp4a" : audioCodecId));
            if (audioCodecId == AudioCodec.AAC.getId()) {
                params.put("audiosamplerate", 44100);
                params.put("audiosamplesize", 16);
            } else if (audioCodecId == AudioCodec.SPEEX.getId()) {
                params.put("audiosamplerate", 16000);
                params.put("audiosamplesize", 16);
            } else {
                params.put("audiosamplerate", soundRate);
                params.put("audiosamplesize", soundSize);
            }
            params.put("stereo", soundType);
            if (audioDataSize > 0) {
                params.put("audiodatarate", 8 * audioDataSize / 1024 / duration); //from bytes to kilobits		
            }
        } else {
            // place holder
            params.put("noaudiocodec", 0);
        }
        // this is actual only supposed to be true if the last video frame is a keyframe
        params.put("canSeekToEnd", true);
        out.writeMap(params);
        buf.flip();
        int bodySize = buf.limit();
        log.debug("Metadata size: {}", bodySize);
        // set a var holding the entire tag size including the previous tag length
        int totalTagSize = TAG_HEADER_LENGTH + bodySize + 4;

        // create a buffer for this tag
        ByteBuffer tagBuffer = ByteBuffer.allocate(totalTagSize);
        // get the timestamp
        int timestamp = 0;
        // create an array big enough
        byte[] bodyBuf = new byte[bodySize];
        // put the bytes into the array
        buf.get(bodyBuf);
        // Data Type
        IOUtils.writeUnsignedByte(tagBuffer, ITag.TYPE_METADATA); //1
        // Body Size - Length of the message. Number of bytes after StreamID to end of tag 
        // (Equal to length of the tag - 11) 
        IOUtils.writeMediumInt(tagBuffer, bodySize); //3
        // Timestamp
        IOUtils.writeExtendedMediumInt(tagBuffer, timestamp); //4
        // Stream id
        tagBuffer.put(DEFAULT_STREAM_ID); //3
        if (log.isTraceEnabled()) {
            log.trace("Tag buffer (after tag header) limit: {} remaining: {}", tagBuffer.limit(), tagBuffer.remaining());
        }
        // get the body
        tagBuffer.put(bodyBuf);
        if (log.isTraceEnabled()) {
            log.trace("Tag buffer (after body) limit: {} remaining: {}", tagBuffer.limit(), tagBuffer.remaining());
        }
        // we add the tag size
        tagBuffer.putInt(TAG_HEADER_LENGTH + bodySize);
        if (log.isTraceEnabled()) {
            log.trace("Tag buffer (after prev tag size) limit: {} remaining: {}", tagBuffer.limit(), tagBuffer.remaining());
        }
        // flip so we can process from the beginning
        tagBuffer.flip();
        // override the meta tag
        flvFile.seek(META_POSITION);
        flvFile.write(tagBuffer.array());
        tagBuffer.clear();
        buf.clear();
    }

    /**
     * Finalizes the FLV file.
     * 
     * @return bytes transferred
     */
    private long finalizeFlv() {
        log.debug("Finalizing {}", filePath);
        long bytesTransferred = 0L;
        try {
            // read file info if it exists
            File tmpFile = new File(filePath + ".info");
            if (tmpFile.exists()) {
                int[] info = readInfoFile(tmpFile);
                if (audioCodecId == -1 && info[0] > 0) {
                    audioCodecId = info[0];
                }
                if (videoCodecId == -1 && info[1] > 0) {
                    videoCodecId = info[1];
                }
                if (duration == 0 && info[2] > 0) {
                    duration = info[2];
                }
                if (audioDataSize == 0 && info[3] > 0) {
                    audioDataSize = info[3];
                }
                if (soundRate == 0 && info[4] > 0) {
                    soundRate = info[4];
                }
                if (soundSize == 0 && info[5] > 0) {
                    soundSize = info[5];
                }
                if (!soundType && info[6] > 0) {
                    soundType = true;
                }
                if (videoDataSize == 0 && info[7] > 0) {
                    videoDataSize = info[7];
                }
            } else {
                log.debug("Flv info file not found");
            }
            tmpFile = null;
            if (!append) {
                // write the file header
                writeHeader();
                // write the metadata with the final duration
                writeMetadataTag(duration * 0.001d, videoCodecId, audioCodecId);
                // ensure we have the data file (*.ser)
                if (dataFile == null) {
                    dataFile = new RandomAccessFile(filePath + ".ser", "r");
                }
                // set the data file the beginning 
                dataFile.seek(0);
                // transfer / write data file into final flv
                bytesTransferred = file.getChannel().transferFrom(dataFile.getChannel(), bytesWritten, dataFile.length());
            } else {
                updateMetadataTag(dataFile, duration * 0.001d, videoCodecId, audioCodecId);
                bytesTransferred = 1;//avoid to start a job to finish up on close() method 
            }
            // close and remove the ser file if write was successful
            if (dataFile != null && bytesTransferred > 0) {
                // close the file
                dataFile.close();
                dataFile = null;
                // delete the data files only if the bytes were transferred to the flv destination
                if (bytesTransferred > 0) {
                    File dat = new File(filePath + ".ser");
                    if (dat.exists()) {
                        dat.delete();
                    }
                    File inf = new File(filePath + ".info");

                    if (inf.exists()) {
                        inf.delete();
                    }
                } else {
                    log.warn("FLV serial file not deleted due to transfer error");
                }
            }
        } catch (Exception e) {
            log.warn("Finalization of flv file failed; new finalize job will be spawned", e);
        } finally {
            if (file != null) {
                // close the file
                try {
                    file.close();
                } catch (Exception e) {
                }
            }
        }
        return bytesTransferred;
    }

    /**
     * Read flv file information from pre-finalization file.
     * 
     * @param tmpFile
     * @return array containing audio codec id, video codec id, and duration
     */
    private int[] readInfoFile(File tmpFile) {
        int[] info = new int[8];
        RandomAccessFile infoFile = null;
        try {
            infoFile = new RandomAccessFile(tmpFile, "r");
            // audio codec id
            info[0] = infoFile.readInt();
            // video codec id
            info[1] = infoFile.readInt();
            // duration
            info[2] = infoFile.readInt();
            // audio data size
            info[3] = infoFile.readInt();
            // audio sound rate
            info[4] = infoFile.readInt();
            // audio sound size
            info[5] = infoFile.readInt();
            // audio type
            info[6] = infoFile.readInt();
            // video data size
            info[7] = infoFile.readInt();
        } catch (Exception e) {
            log.warn("Exception reading flv file information data", e);
        } finally {
            if (infoFile != null) {
                try {
                    infoFile.close();
                } catch (IOException e) {
                }
            }
        }
        return info;
    }

    /**
     * Write or update flv file information into the pre-finalization file.
     */
    private void updateInfoFile() {
        RandomAccessFile infoFile = null;
        try {
            infoFile = new RandomAccessFile(filePath + ".info", "rw");
            infoFile.writeInt(audioCodecId);
            infoFile.writeInt(videoCodecId);
            infoFile.writeInt(duration);
            // additional props
            infoFile.writeInt(audioDataSize);
            infoFile.writeInt(soundRate);
            infoFile.writeInt(soundSize);
            infoFile.writeInt(soundType ? 1 : 0);
            infoFile.writeInt(videoDataSize);
        } catch (Exception e) {
            log.warn("Exception writing flv file information data", e);
        } finally {
            if (infoFile != null) {
                try {
                    infoFile.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * Ends the writing process, then merges the data file with the flv file header and metadata.
     */
    public void close() {
        log.debug("close");
        // spawn a thread to finish up our flv writer work
        log.debug("Meta tags: {}", metaTags);
        boolean locked = false;
        long bytesTransferred = 0L;
        try {
            // try to get a lock within x time
            locked = lock.tryAcquire(500L, TimeUnit.MILLISECONDS);
            if (locked) {
                bytesTransferred = finalizeFlv();
            }
        } catch (InterruptedException e) {
            log.warn("Exception acquiring lock", e);
        } finally {
            if (locked) {
                lock.release();
            }
            log.debug("{} closed", filePath);
            // if write failed, spawn a job to finish up
            if (bytesTransferred == 0L) {
                log.info("Spawning flv finalizer for {}", filePath);
                // spawn job to write the finalized FLV file based on the ser file
                spawnFinalizer().start();
            }
        }
    }

    /**
     * Spawn a finalizer thread and return it (unstarted).
     * 
     * @return Thread containing finalizer
     */
    Thread spawnFinalizer() {
        return new Thread(new FLVFinalizer(), "FLVFinalizer#" + System.currentTimeMillis());
    }

    /**
     * {@inheritDoc}
     */
    public IStreamableFile getFile() {
        return flv;
    }

    /**
     * Setter for FLV object
     *
     * @param flv
     *            FLV source
     *
     */
    public void setFLV(IFLV flv) {
        this.flv = flv;
    }

    /**
     * {@inheritDoc}
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Setter for offset
     *
     * @param offset
     *            Value to set for offset
     */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    /**
     * {@inheritDoc}
     */
    public long getBytesWritten() {
        return bytesWritten;
    }

    public void setVideoCodecId(int videoCodecId) {
        this.videoCodecId = videoCodecId;
    }

    public void setAudioCodecId(int audioCodecId) {
        this.audioCodecId = audioCodecId;
    }

    public void setSoundRate(int soundRate) {
        this.soundRate = soundRate;
    }

    public void setSoundSize(int soundSize) {
        this.soundSize = soundSize;
    }

    public void setSoundType(boolean soundType) {
        this.soundType = soundType;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setVideoDataSize(int videoDataSize) {
        this.videoDataSize = videoDataSize;
    }

    public void setAudioDataSize(int audioDataSize) {
        this.audioDataSize = audioDataSize;
    }

    private final class FLVFinalizer implements Runnable {

        public void run() {
            log.debug("Finalizer run");
            try {
                // clear incomplete files ref
                file = null;
                // create an instance of the incomplete file
                File tmp = new File(filePath);
                // delete the file
                boolean deleted = tmp.delete();
                log.info("Deleted ({}) incomplete file: {}", deleted, filePath);
                // clear ref
                tmp = null;
                // quick sleep, cheap delay
                Thread.sleep(2000L);
            } catch (Exception e) {
                log.error("Error on cleanup of flv", e);
            }
            // attempt to finalize the flv
            finalizeFlv();
            log.debug("Finalizer exit");
        }

    }

    /**
     * Allows repair of flv files if .info and .ser files still exist.
     * 
     * @param path
     *            path to .ser file
     * @param audioId
     *            audio codec id
     * @param videoId
     *            video codec id
     * @return true if conversion was successful
     * @throws InterruptedException
     *             Exception on interruption
     */
    public static boolean repair(String path, Integer audioId, Integer videoId) throws InterruptedException {
        boolean result = false;
        FLVWriter writer = null;
        log.debug("Serial file path: " + path);
        System.out.println("Serial file path: " + path);
        if (path.endsWith(".ser")) {
            File ser = new File(path);
            if (ser.exists() && ser.canRead()) {
                ser = null;
                String flvPath = path.substring(0, path.lastIndexOf('.'));
                log.debug("Flv file path: " + flvPath);
                System.out.println("Flv file path: " + flvPath);
                // check for .info and if it does not exist set dummy data
                File inf = new File(flvPath + ".info");
                if (inf.exists() && inf.canRead()) {
                    inf = null;
                    // create a writer
                    writer = new FLVWriter(flvPath);
                } else {
                    log.debug("Info file was not found or could not be read, using dummy data");
                    System.err.println("Info file was not found or could not be read, using dummy data");
                    // create a writer
                    writer = new FLVWriter(flvPath);
                    int acid = audioId == null ? 11 : audioId, vcid = videoId == null ? 7 : videoId;
                    writer.setAudioCodecId(acid); // default: speex
                    writer.setVideoCodecId(vcid); // default: h.264
                    writer.setDuration(Integer.MAX_VALUE);
                    writer.setSoundRate(16000);
                    writer.setSoundSize(16);
                }
            } else {
                log.error("Serial file was not found or could not be read");
                System.err.println("Serial file was not found or could not be read");
            }
        } else {
            log.error("Provide the path to your .ser file");
            System.err.println("Serial file was not found or could not be read");
        }
        if (writer != null) {
            // spawn a flv finalizer 
            Thread job = writer.spawnFinalizer();
            // start the work
            job.start();
            // wait for it to finish
            job.join();
            log.debug("File repair completed");
            System.out.println("File repair completed");
            result = true;
        }
        return result;
    }

    /**
     * Exposed to allow repair of flv files if .info and .ser files still exist.
     * 
     * @param args
     *            0: path to .ser file 1: audio codec id 2: video codec id
     * @throws InterruptedException
     *             Exception on interruption
     */
    public static void main(String[] args) throws InterruptedException {
        if (args == null || args[0] == null) {
            System.err.println("Provide the path to your .ser file");
        } else {
            repair(args[0], args.length > 1 && args[1] != null ? Integer.valueOf(args[1]) : null, args.length > 2 && args[2] != null ? Integer.valueOf(args[2]) : null);
        }
        System.exit(0);
    }

}
