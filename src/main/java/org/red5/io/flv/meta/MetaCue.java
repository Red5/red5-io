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

package org.red5.io.flv.meta;

import java.util.HashMap;
import java.util.Map;

/**
 * Cue point is metadata marker used to control and accompany video playback with client-side application events. Each cue point have at least one attribute, timestamp. Timestamp specifies position of cue point in FLV file. <br>
 * Cue points are usually used as event triggers down video flow or navigation points in a file. Cue points are of two types:
 * <ul>
 * <li>Embedded into FLV or SWF</li>
 * <li>External, or added on fly (e.g. with FLVPlayback component or ActionScript) on both server-side and client-side.</li>
 * </ul>
 * <br>
 * To add cue point trigger event listener at client-side in Flex/Flash application, use NetStream.onCuePoint event handler.
 * 
 * @author The Red5 Project
 * @author Dominick Accattato (daccattato@gmail.com)
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 * @param <K>
 *            key type
 * @param <V>
 *            value type
 */
public class MetaCue<K, V> extends HashMap<String, Object> implements IMetaCue {

    /**
     * SerialVersionUID = -1769771340654996861L;
     */
    private static final long serialVersionUID = -1769771340654996861L;

    /**
     * CuePoint constructor
     */
    public MetaCue() {

    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.put("name", name);
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return (String) this.get("name");
    }

    /**
     * {@inheritDoc}
     */
    public void setType(String type) {
        this.put("type", type);
    }

    /**
     * {@inheritDoc}
     */
    public String getType() {
        return (String) this.get("type");
    }

    /**
     * {@inheritDoc}
     */
    public void setTime(double d) {
        this.put("time", d);
    }

    /**
     * {@inheritDoc}
     */
    public double getTime() {
        return (Double) this.get("time");
    }

    /** {@inheritDoc} */
    public int compareTo(Object arg0) {
        MetaCue<?, ?> cp = (MetaCue<?, ?>) arg0;
        double cpTime = cp.getTime();
        double thisTime = this.getTime();

        if (cpTime > thisTime) {
            return -1;
        } else if (cpTime < thisTime) {
            return 1;
        }

        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MetaCue{");
        for (Map.Entry<String, Object> entry : entrySet()) {
            sb.append(entry.getKey().toLowerCase());
            sb.append('=');
            sb.append(entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }
}
