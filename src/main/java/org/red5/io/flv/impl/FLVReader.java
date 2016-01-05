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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.BufferType;
import org.red5.io.IKeyFrameMetaCache;
import org.red5.io.IStreamableFile;
import org.red5.io.ITag;
import org.red5.io.ITagReader;
import org.red5.io.IoConstants;
import org.red5.io.amf.Input;
import org.red5.io.amf.Output;
import org.red5.io.flv.FLVHeader;
import org.red5.io.flv.IKeyFrameDataAnalyzer;
import org.red5.io.object.Deserializer;
import org.red5.io.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Reader is used to read the contents of a FLV file. NOTE: This class is not implemented as threading-safe. The caller should make sure the threading-safety.
 *
 * @author The Red5 Project
 * @author Dominick Accattato (daccattato@gmail.com)
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 * @author Paul Gregoire, (mondain@gmail.com)
 */
public class FLVReader implements IoConstants, ITagReader, IKeyFrameDataAnalyzer {

    private static Logger log = LoggerFactory.getLogger(FLVReader.class);

    /**
     * File
     */
    private File file;

    /**
     * File input stream
     */
    private FileInputStream fis;

    /**
     * File channel
     */
    private FileChannel channel;

    private long channelSize;

    /**
     * Keyframe metadata
     */
    private KeyFrameMeta keyframeMeta;

    /**
     * Input byte buffer
     */
    private IoBuffer in;

    /** Set to true to generate metadata automatically before the first tag. */
    private boolean generateMetadata;

    /** Position of first video tag. */
    private long firstVideoTag = -1;

    /** Position of first audio tag. */
    private long firstAudioTag = -1;

    /** metadata sent flag */
    private boolean metadataSent = false;

    /** Duration in milliseconds. */
    private long duration;

    /** Mapping between file position and timestamp in ms. */
    private HashMap<Long, Long> posTimeMap;

    /** Buffer type / style to use **/
    private static BufferType bufferType = BufferType.AUTO;

    private static int bufferSize = 1024;

    /** Use load buffer */
    private boolean useLoadBuf;

    /** Cache for keyframe informations. */
    private static IKeyFrameMetaCache keyframeCache;

    /** The header of this FLV file. */
    private FLVHeader header;

    private final ReentrantLock lock = new ReentrantLock();

    /** Constructs a new FLVReader. */
    FLVReader() {
    }

    /**
     * Creates FLV reader from file input stream.
     *
     * @param f
     *            File
     * @throws IOException
     *             on error
     */
    public FLVReader(File f) throws IOException {
        this(f, false);
    }

    /**
     * Creates FLV reader from file input stream, sets up metadata generation flag.
     *
     * @param f
     *            File input stream
     * @param generateMetadata
     *            <code>true</code> if metadata generation required, <code>false</code> otherwise
     * @throws IOException
     *             on error
     */
    public FLVReader(File f, boolean generateMetadata) throws IOException {
        if (null == f) {
            log.warn("Reader was passed a null file");
            log.debug("{}", org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString(this));
        }
        this.file = f;
        this.fis = new FileInputStream(f);
        this.generateMetadata = generateMetadata;
        channel = fis.getChannel();
        channelSize = channel.size();
        in = null;
        fillBuffer();
        postInitialize();
    }

    /**
     * Creates FLV reader from file channel.
     *
     * @param channel
     *            file channel
     * @throws IOException
     *             on error
     */
    public FLVReader(FileChannel channel) throws IOException {
        if (null == channel) {
            log.warn("Reader was passed a null channel");
            log.debug("{}", org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString(this));
        }
        if (!channel.isOpen()) {
            log.warn("Reader was passed a closed channel");
            return;
        }
        this.channel = channel;
        channelSize = channel.size();
        log.debug("Channel size: {}", channelSize);
        if (channel.position() > 0) {
            log.debug("Channel position: {}", channel.position());
            channel.position(0);
        }
        fillBuffer();
        postInitialize();
    }

    /**
     * Accepts mapped file bytes to construct internal members.
     *
     * @param generateMetadata
     *            <code>true</code> if metadata generation required, <code>false</code> otherwise
     * @param buffer
     *            IoBuffer
     */
    public FLVReader(IoBuffer buffer, boolean generateMetadata) {
        this.generateMetadata = generateMetadata;
        in = buffer;
        postInitialize();
    }

    public void setKeyFrameCache(IKeyFrameMetaCache keyframeCache) {
        FLVReader.keyframeCache = keyframeCache;
    }

    /**
     * Get the remaining bytes that could be read from a file or ByteBuffer.
     *
     * @return Number of remaining bytes
     */
    private long getRemainingBytes() {
        if (in != null) {
            if (!useLoadBuf) {
                return in.remaining();
            }
            try {
                if (channel.isOpen()) {
                    return channelSize - channel.position() + in.remaining();
                } else {
                    return in.remaining();
                }
            } catch (Exception e) {
                log.error("Error getRemainingBytes", e);
            }
        }
        return 0;
    }

    /**
     * Get the total readable bytes in a file or ByteBuffer.
     *
     * @return Total readable bytes
     */
    public long getTotalBytes() {
        if (!useLoadBuf) {
            return in.capacity();
        }
        try {
            return channelSize;
        } catch (Exception e) {
            log.error("Error getTotalBytes", e);
            return 0;
        }
    }

    /**
     * Get the current position in a file or ByteBuffer.
     *
     * @return Current position in a file
     */
    private long getCurrentPosition() {
        long pos;
        if (!useLoadBuf) {
            return in.position();
        }
        try {
            if (in != null) {
                pos = (channel.position() - in.remaining());
            } else {
                pos = channel.position();
            }
            return pos;
        } catch (Exception e) {
            log.error("Error getCurrentPosition", e);
            return 0;
        }
    }

    /**
     * Modifies current position.
     *
     * @param pos
     *            Current position in file
     */
    private void setCurrentPosition(long pos) {
        if (pos == Long.MAX_VALUE) {
            pos = file.length();
        }
        if (!useLoadBuf) {
            in.position((int) pos);
            return;
        }
        try {
            if (pos >= (channel.position() - in.limit()) && pos < channel.position()) {
                in.position((int) (pos - (channel.position() - in.limit())));
            } else {
                channel.position(pos);
                fillBuffer(bufferSize, true);
            }
        } catch (Exception e) {
            log.error("Error setCurrentPosition", e);
        }

    }

    /**
     * Loads whole buffer from file channel, with no reloading (that is, appending).
     */
    private void fillBuffer() {
        fillBuffer(bufferSize, false);
    }

    /**
     * Loads data from channel to buffer.
     *
     * @param amount
     *            Amount of data to load with no reloading
     */
    private void fillBuffer(long amount) {
        fillBuffer(amount, false);
    }

    /**
     * Load enough bytes from channel to buffer. After the loading process, the caller can make sure the amount in buffer is of size 'amount' if we haven't reached the end of channel.
     *
     * @param amount
     *            The amount of bytes in buffer after returning, no larger than bufferSize
     * @param reload
     *            Whether to reload or append
     */
    private void fillBuffer(long amount, boolean reload) {
        try {
            if (amount > bufferSize) {
                amount = bufferSize;
            }
            log.debug("Buffering amount: {} buffer size: {}", amount, bufferSize);
            // Read all remaining bytes if the requested amount reach the end of channel
            if (channelSize - channel.position() < amount) {
                amount = channelSize - channel.position();
            }
            if (in == null) {
                switch (bufferType) {
                    case HEAP:
                        in = IoBuffer.allocate(bufferSize, false);
                        break;
                    case DIRECT:
                        in = IoBuffer.allocate(bufferSize, true);
                        break;
                    default:
                        in = IoBuffer.allocate(bufferSize);
                }
                channel.read(in.buf());
                in.flip();
                useLoadBuf = true;
            }
            if (!useLoadBuf) {
                return;
            }
            if (reload || in.remaining() < amount) {
                if (!reload) {
                    in.compact();
                } else {
                    in.clear();
                }
                channel.read(in.buf());
                in.flip();
            }
        } catch (Exception e) {
            log.error("Error fillBuffer", e);
        }
    }

    /**
     * Post-initialization hook, reads keyframe metadata and decodes header (if any).
     */
    private void postInitialize() {
        if (log.isDebugEnabled()) {
            log.debug("FLVReader 1 - Buffer size: {} position: {} remaining: {}", new Object[] { getTotalBytes(), getCurrentPosition(), getRemainingBytes() });
        }
        if (getRemainingBytes() >= 9) {
            decodeHeader();
        }
        if (file != null) {
            keyframeMeta = analyzeKeyFrames();
        }
        long old = getCurrentPosition();
        log.debug("Position: {}", old);
    }

    /** {@inheritDoc} */
    public boolean hasVideo() {
        KeyFrameMeta meta = analyzeKeyFrames();
        if (meta == null) {
            return false;
        }
        return (!meta.audioOnly && meta.positions.length > 0);
    }

    /**
     * Getter for buffer type (auto, direct or heap).
     *
     * @return Value for property 'bufferType'
     */
    public static String getBufferType() {
        switch (bufferType) {
            case AUTO:
                return "auto";
            case DIRECT:
                return "direct";
            case HEAP:
                return "heap";
            default:
                return null;
        }
    }

    /**
     * Setter for buffer type.
     *
     * @param bufferType
     *            Value to set for property 'bufferType'
     */
    public static void setBufferType(String bufferType) {
        int bufferTypeHash = bufferType.hashCode();
        switch (bufferTypeHash) {
            case 3198444: //heap
                //Get a heap buffer from buffer pool
                FLVReader.bufferType = BufferType.HEAP;
                break;
            case -1331586071: //direct
                //Get a direct buffer from buffer pool
                FLVReader.bufferType = BufferType.DIRECT;
                break;
            case 3005871: //auto
                //Let MINA choose
            default:
                FLVReader.bufferType = BufferType.AUTO;
        }
    }

    /**
     * Getter for buffer size.
     *
     * @return Value for property 'bufferSize'
     */
    public static int getBufferSize() {
        return bufferSize;
    }

    /**
     * Setter for property 'bufferSize'.
     *
     * @param bufferSize
     *            Value to set for property 'bufferSize'
     */
    public static void setBufferSize(int bufferSize) {
        // make sure buffer size is no less than 1024 bytes.
        if (bufferSize < 1024) {
            bufferSize = 1024;
        }
        FLVReader.bufferSize = bufferSize;
    }

    /**
     * Returns the file buffer.
     * 
     * @return File contents as byte buffer
     */
    public IoBuffer getFileData() {
        // TODO as of now, return null will disable cache
        // we need to redesign the cache architecture so that
        // the cache is layered underneath FLVReader not above it,
        // thus both tag cache and file cache are feasible.
        return null;
    }

    /** {@inheritDoc} */
    public void decodeHeader() {
        // flv header is 9 bytes
        fillBuffer(9);
        header = new FLVHeader();
        // skip signature
        in.skip(4);
        header.setTypeFlags(in.get());
        header.setDataOffset(in.getInt());
        if (log.isDebugEnabled()) {
            log.debug("Header: {}", header.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    public IStreamableFile getFile() {
        // TODO wondering if we need to have a reference
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public int getOffset() {
        // XXX what's the difference from getBytesRead
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public long getBytesRead() {
        // XXX should summarize the total bytes read or
        // just the current position?
        return getCurrentPosition();
    }

    /** {@inheritDoc} */
    public long getDuration() {
        return duration;
    }

    public int getVideoCodecId() {
        if (keyframeMeta != null) {
            return keyframeMeta.videoCodecId;
        }
        return -1;
    }

    public int getAudioCodecId() {
        if (keyframeMeta != null) {
            return keyframeMeta.audioCodecId;
        }
        return -1;
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasMoreTags() {
        try {
            lock.lockInterruptibly();
            return getRemainingBytes() > 4;
        } catch (InterruptedException e) {
            log.warn("Exception acquiring lock", e);
            return false;
        } finally {
            if (lock.isLocked()) {
                lock.unlock();
            }
        }
    }

    /**
     * Create tag for metadata event.
     *
     * @return Metadata event tag
     */
    private ITag createFileMeta() {
        // Create tag for onMetaData event
        IoBuffer buf = IoBuffer.allocate(192);
        buf.setAutoExpand(true);
        Output out = new Output(buf);
        // Duration property
        out.writeString("onMetaData");
        Map<Object, Object> props = new HashMap<Object, Object>();
        props.put("duration", duration / 1000.0);
        if (firstVideoTag != -1) {
            long old = getCurrentPosition();
            setCurrentPosition(firstVideoTag);
            readTagHeader();
            fillBuffer(1);
            byte frametype = in.get();
            // Video codec id
            props.put("videocodecid", frametype & MASK_VIDEO_CODEC);
            setCurrentPosition(old);
        }
        if (firstAudioTag != -1) {
            long old = getCurrentPosition();
            setCurrentPosition(firstAudioTag);
            readTagHeader();
            fillBuffer(1);
            byte frametype = in.get();
            // Audio codec id
            props.put("audiocodecid", (frametype & MASK_SOUND_FORMAT) >> 4);
            setCurrentPosition(old);
        }
        props.put("canSeekToEnd", true);
        out.writeMap(props);
        buf.flip();

        ITag result = new Tag(IoConstants.TYPE_METADATA, 0, buf.limit(), null, 0);
        result.setBody(buf);
        //
        out = null;
        return result;
    }

    /** {@inheritDoc} */
    public ITag readTag() {
        ITag tag = null;
        try {
            lock.lockInterruptibly();
            long oldPos = getCurrentPosition();
            tag = readTagHeader();
            if (tag != null) {
                boolean isMetaData = tag.getDataType() == TYPE_METADATA;
                log.debug("readTag, oldPos: {}, tag header: \n{}", oldPos, tag);
                if (!metadataSent && !isMetaData && generateMetadata) {
                    // Generate initial metadata automatically
                    setCurrentPosition(oldPos);
                    KeyFrameMeta meta = analyzeKeyFrames();
                    if (meta != null) {
                        metadataSent = true;
                        return createFileMeta();
                    }
                }
                int bodySize = tag.getBodySize();
                IoBuffer body = IoBuffer.allocate(bodySize, false);
                // XXX Paul: this assists in 'properly' handling damaged FLV files		
                long newPosition = getCurrentPosition() + bodySize;
                if (newPosition <= getTotalBytes()) {
                    int limit;
                    while (getCurrentPosition() < newPosition) {
                        fillBuffer(newPosition - getCurrentPosition());
                        if (getCurrentPosition() + in.remaining() > newPosition) {
                            limit = in.limit();
                            in.limit((int) (newPosition - getCurrentPosition()) + in.position());
                            body.put(in);
                            in.limit(limit);
                        } else {
                            body.put(in);
                        }
                    }
                    body.flip();
                    tag.setBody(body);
                }
            } else {
                log.debug("Tag was null");
            }
        } catch (InterruptedException e) {
            log.warn("Exception acquiring lock", e);
        } finally {
            if (lock.isLocked()) {
                lock.unlock();
            }
        }
        return tag;
    }

    /**
     * {@inheritDoc}
     */
    public void close() {
        log.debug("Reader close: {}", file.getName());
        try {
            lock.lock();
            if (in != null) {
                in.free();
                in = null;
            }
            if (channel != null) {
                try {
                    channel.close();
                    fis.close();
                } catch (IOException e) {
                    log.error("FLVReader :: close ::>\n", e);
                }
            }
            log.debug("Reader closed: {}", file.getName());
        } finally {
            if (lock.isLocked()) {
                lock.unlock();
            }
        }
    }

    /**
     * Key frames analysis may be used as a utility method so synchronize it.
     *
     * @return Keyframe metadata
     */
    public KeyFrameMeta analyzeKeyFrames() {
        if (keyframeMeta != null) {
            return keyframeMeta;
        }
        try {
            lock.lockInterruptibly();
            // check for cached keyframe informations
            if (keyframeCache != null) {
                keyframeMeta = keyframeCache.loadKeyFrameMeta(file);
                if (keyframeMeta != null) {
                    // Keyframe data loaded, create other mappings
                    duration = keyframeMeta.duration;
                    posTimeMap = new HashMap<Long, Long>();
                    for (int i = 0; i < keyframeMeta.positions.length; i++) {
                        posTimeMap.put(keyframeMeta.positions[i], (long) keyframeMeta.timestamps[i]);
                    }
                    return keyframeMeta;
                }
            }
            // create a holder for the metadata
            keyframeMeta = new KeyFrameMeta();
            // Lists of video positions and timestamps
            List<Long> positionList = new ArrayList<Long>();
            List<Integer> timestampList = new ArrayList<Integer>();
            // Lists of audio positions and timestamps
            List<Long> audioPositionList = new ArrayList<Long>();
            List<Integer> audioTimestampList = new ArrayList<Integer>();
            long origPos = getCurrentPosition();
            // point to the first tag
            setCurrentPosition(9);
            // number of tags read
            int totalValidTags = 0;
            // start off as audio only
            boolean audioOnly = true;
            while (hasMoreTags()) {
                long pos = getCurrentPosition();
                // Read tag header and duration
                ITag tmpTag = this.readTagHeader();
                if (tmpTag != null) {
                    totalValidTags++;
                } else {
                    break;
                }
                duration = tmpTag.getTimestamp();
                if (tmpTag.getDataType() == IoConstants.TYPE_VIDEO) {
                    if (audioOnly) {
                        audioOnly = false;
                        audioPositionList.clear();
                        audioTimestampList.clear();
                    }
                    if (firstVideoTag == -1) {
                        firstVideoTag = pos;
                    }
                    // Grab Frame type
                    fillBuffer(1);
                    int frametype = in.get();
                    if (keyframeMeta.videoCodecId == -1) {
                        keyframeMeta.videoCodecId = frametype & MASK_VIDEO_CODEC;
                    }
                    if (((frametype & MASK_VIDEO_FRAMETYPE) >> 4) == FLAG_FRAMETYPE_KEYFRAME) {
                        positionList.add(pos);
                        timestampList.add(tmpTag.getTimestamp());
                    }
                } else if (tmpTag.getDataType() == IoConstants.TYPE_AUDIO) {
                    if (firstAudioTag == -1) {
                        firstAudioTag = pos;
                    }
                    // Grab Frame type
                    fillBuffer(1);
                    int frametype = in.get() & 0xff;
                    if (keyframeMeta.audioCodecId == -1) {
                        keyframeMeta.audioCodecId = (frametype & MASK_SOUND_FORMAT) >> 4;
                    }
                    if (audioOnly) {
                        audioPositionList.add(pos);
                        audioTimestampList.add(tmpTag.getTimestamp());
                    }
                }
                // XXX Paul: this 'properly' handles damaged FLV files - as far as duration/size is concerned
                long newPosition = pos + tmpTag.getBodySize() + 15;
                // log.debug("---->" + in.remaining() + " limit=" + in.limit() + "
                // new pos=" + newPosition);
                if (newPosition >= getTotalBytes()) {
                    log.error("New position exceeds limit");
                    if (log.isDebugEnabled()) {
                        log.debug("-----");
                        log.debug("Keyframe analysis");
                        log.debug(" data type=" + tmpTag.getDataType() + " bodysize=" + tmpTag.getBodySize());
                        log.debug(" remaining=" + getRemainingBytes() + " limit=" + getTotalBytes() + " new pos=" + newPosition);
                        log.debug(" pos=" + pos);
                        log.debug("-----");
                    }
                    //XXX Paul: A runtime exception is probably not needed here
                    log.info("New position {} exceeds limit {}", newPosition, getTotalBytes());
                    //just break from the loop
                    break;
                } else {
                    setCurrentPosition(newPosition);
                }
            }
            // restore the pos
            setCurrentPosition(origPos);
            log.debug("Total valid tags found: {}", totalValidTags);
            keyframeMeta.duration = duration;
            posTimeMap = new HashMap<Long, Long>();
            if (audioOnly) {
                // The flv only contains audio tags, use their lists
                // to support pause and seeking
                positionList = audioPositionList;
                timestampList = audioTimestampList;
            }
            keyframeMeta.audioOnly = audioOnly;
            keyframeMeta.positions = new long[positionList.size()];
            keyframeMeta.timestamps = new int[timestampList.size()];
            for (int i = 0; i < keyframeMeta.positions.length; i++) {
                keyframeMeta.positions[i] = positionList.get(i);
                keyframeMeta.timestamps[i] = timestampList.get(i);
                posTimeMap.put((long) positionList.get(i), (long) timestampList.get(i));
            }
            if (keyframeCache != null) {
                keyframeCache.saveKeyFrameMeta(file, keyframeMeta);
            }
        } catch (InterruptedException e) {
            log.warn("Exception acquiring lock", e);
        } finally {
            if (lock.isLocked()) {
                lock.unlock();
            }
        }
        return keyframeMeta;
    }

    /**
     * Put the current position to pos. The caller must ensure the pos is a valid one (eg. not sit in the middle of a frame).
     *
     * @param pos
     *            New position in file. Pass <code>Long.MAX_VALUE</code> to seek to end of file.
     */
    public void position(long pos) {
        setCurrentPosition(pos);
    }

    /**
     * Read only header part of a tag.
     *
     * @return Tag header
     */
    private ITag readTagHeader() {
        // previous tag size (4 bytes) + flv tag header size (11 bytes)
        fillBuffer(15);
        //		if (log.isDebugEnabled()) {
        //			in.mark();
        //			StringBuilder sb = new StringBuilder();
        //			HexDump.dumpHex(sb, in.array());
        //			log.debug("\n{}", sb);
        //			in.reset();
        //		}		
        // previous tag's size
        int previousTagSize = in.getInt();
        // start of the flv tag
        byte dataType = in.get();
        if (log.isTraceEnabled()) {
            log.trace("Bits: {}", Integer.toBinaryString(dataType));
        }
        dataType = (byte) (dataType & 31);
        byte filter = (byte) ((dataType & 63) >> 5);
        byte reserved = (byte) ((dataType & 127) >> 6);
        log.debug("Reserved: {}, Filter: {}, Datatype: {}", reserved, filter, dataType);
        switch (dataType) {
            case 8: // audio
                log.debug("Found audio");
                break;
            case 9: // video
                log.debug("Found video");
                break;
            case 15: // special fms undocumented type?
            case 18: // meta / script data
                log.debug("Found meta/script data");
                break;
            default:
                log.debug("Invalid data type detected ({}), reading ahead", dataType);
                log.debug("Current position: {} limit: {}", in.position(), in.limit());
                // loop a few times to see if we find a usable data type
                int i = 0;
                while (dataType != 8 && dataType != 9 && dataType != 18) {
                    // only allow 10 loops
                    if (i++ > 10) {
                        return null;
                    }
                    // move ahead and see if we get a valid datatype		
                    dataType = in.get();
                }
        }
        //		byte aacType = 0;
        //		if (dataType == 8 && keyframeMeta.audioCodecId == AudioCodec.AAC.getId()) {
        //			// flv spec indicates that aac contains an extra byte after the data type
        //			aacType = in.get();
        //		}
        int bodySize = IOUtils.readUnsignedMediumInt(in);
        int timestamp = IOUtils.readExtendedMediumInt(in);
        if (log.isDebugEnabled()) {
            int streamId = IOUtils.readUnsignedMediumInt(in);
            log.debug("Data type: {} timestamp: {} stream id: {} body size: {} previous tag size: {}", new Object[] { dataType, timestamp, streamId, bodySize, previousTagSize });
        } else {
            in.skip(3);
        }
        return new Tag(dataType, timestamp, bodySize, null, previousTagSize);
    }

    /**
     * Returns the last tag's timestamp as the files duration.
     * 
     * @param flvFile
     *            FLV file
     * @return duration
     */
    public static int getDuration(File flvFile) {
        int duration = 0;
        RandomAccessFile flv = null;
        try {
            flv = new RandomAccessFile(flvFile, "r");
            long flvLength = Math.max(flvFile.length(), flv.length());
            log.debug("File length: {}", flvLength);
            if (flvLength > 13) {
                flv.seek(flvLength - 4);
                int lastTagSize = flv.readInt();
                log.debug("Last tag size: {}", lastTagSize);
                if (lastTagSize > 0 && (lastTagSize < flvLength)) {
                    // jump right to where tag timestamp would be
                    flv.seek(flvLength - lastTagSize);
                    // grab timestamp as a regular int
                    duration = flv.readInt();
                    // adjust value to match extended timestamp
                    duration = (duration >>> 8) | ((duration & 0x000000ff) << 24);
                } else {
                    // attempt to read the metadata
                    flv.seek(13);
                    byte tagType = flv.readByte();
                    if (tagType == ITag.TYPE_METADATA) {
                        ByteBuffer buf = ByteBuffer.allocate(3);
                        flv.getChannel().read(buf);
                        int bodySize = IOUtils.readMediumInt(buf);
                        log.debug("Metadata body size: {}", bodySize);
                        flv.skipBytes(4); // timestamp
                        flv.skipBytes(3); // stream id
                        buf.clear();
                        buf = ByteBuffer.allocate(bodySize);
                        flv.getChannel().read(buf);
                        // construct the meta
                        IoBuffer ioBuf = IoBuffer.wrap(buf);
                        Input input = new Input(ioBuf);
                        String metaType = Deserializer.deserialize(input, String.class);
                        log.debug("Metadata type: {}", metaType);
                        Map<String, ?> meta = Deserializer.deserialize(input, Map.class);
                        Object tmp = meta.get("duration");
                        if (tmp != null) {
                            if (tmp instanceof Double) {
                                duration = ((Double) tmp).intValue();
                            } else {
                                duration = Integer.valueOf((String) tmp);
                            }
                        }
                        input = null;
                        meta.clear();
                        meta = null;
                        ioBuf.clear();
                        ioBuf.free();
                        ioBuf = null;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Exception getting file duration", e);
        } finally {
            try {
                if (flv != null) {
                    flv.close();
                }
            } catch (IOException e) {
            }
            flv = null;
        }
        return duration;
    }
}
