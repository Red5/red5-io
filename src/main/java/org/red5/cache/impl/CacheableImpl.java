/*
 * RED5 Open Source Media Server - https://github.com/Red5/
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

package org.red5.cache.impl;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.cache.ICacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * Provides an implementation of a cacheable object.
 * 
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class CacheableImpl implements ICacheable {

    protected static Logger log = LoggerFactory.getLogger(CacheableImpl.class);

    protected ApplicationContext applicationContext;

    private byte[] bytes;

    private String name;

    private boolean cached;

    public CacheableImpl(Object obj) {
        IoBuffer tmp = IoBuffer.allocate(1024, true);
        tmp.setAutoExpand(true);
        tmp.putObject(obj);
        bytes = new byte[tmp.capacity()];
        tmp.get(bytes);
        cached = true;
        tmp.free();
        tmp = null;
    }

    public CacheableImpl(IoBuffer buffer) {
        if (log.isDebugEnabled()) {
            log.debug("Buffer is direct: {} capacity: {}", buffer.isDirect(), buffer.capacity());
            log.debug("Buffer limit: {} remaining: {} position: {}", new Object[] { buffer.limit(), buffer.remaining(), buffer.position() });
        }
        bytes = new byte[buffer.capacity()];
        buffer.rewind();
        int i = 0;
        while (i < buffer.limit()) {
            buffer.position(i);
            while (buffer.remaining() > 0) {
                bytes[i++] = buffer.get();
            }
        }
        cached = true;
        if (log.isDebugEnabled()) {
            log.debug("Buffer size: " + buffer.capacity());
        }
        buffer = null;
    }

    public void addRequest() {
        log.info("Adding request for: " + name);
    }

    /** {@inheritDoc} */
    @Override
    public byte[] getBytes() {
        return bytes;
    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer getByteBuffer() {
        return IoBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCached() {
        return cached;
    }

    /** {@inheritDoc} */
    @Override
    public void setCached(boolean cached) {
        this.cached = cached;
    }

    /** {@inheritDoc} */
    @Override
    public void setName(String name) {
        this.name = name;
    }

}
