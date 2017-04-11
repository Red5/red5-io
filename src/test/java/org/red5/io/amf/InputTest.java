package org.red5.io.amf;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author varga.bence@ustream.tv
 */
public class InputTest
{

    @Test
    public void testNonZeroBasedEcmaArray() {
        // { '1': 'hello' }
        byte[] stream = new byte[] { 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x31, 0x02, 0x00, 0x05, 'h', 'e', 'l', 'l', 'o', 0x00, 0x00, 0x09 };
        Input input = new Input(IoBuffer.wrap(stream));
        Object actual = input.readMap();

        Map expected = new HashMap();
        expected.put(1, "hello");

        assertEquals(expected, actual);
    }

}