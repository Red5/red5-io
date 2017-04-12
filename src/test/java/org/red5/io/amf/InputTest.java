package org.red5.io.amf;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;

/**
 * @author varga.bence@ustream.tv
 */
public class InputTest
{

    @Test
    public void testZeroBasedEcmaArray() {
        // { '0': 'hello', '1': 'world' }
        byte[] stream = new byte[] { 0x00, 0x00, 0x00, 0x02,
                0x00, 0x01, 0x30, 0x02, 0x00, 0x05, 'h', 'e', 'l', 'l', 'o',
                0x00, 0x01, 0x31, 0x02, 0x00, 0x05, 'w', 'o', 'r', 'l', 'd',
                0x00, 0x00, 0x09 };
        Input input = new Input(IoBuffer.wrap(stream));
        Object actual = input.readMap();

        List<String> expected = new ArrayList<>();
        expected.add("hello");
        expected.add("world");

        assertEquals(expected, actual);
    }

    @Test
    public void testNonZeroBasedEcmaArray() {
        // { '1': 'hello' }
        byte[] stream = new byte[] { 0x00, 0x00, 0x00, 0x01,
                0x00, 0x01, 0x31, 0x02, 0x00, 0x05, 'h', 'e', 'l', 'l', 'o',
                0x00, 0x00, 0x09 };
        Input input = new Input(IoBuffer.wrap(stream));
        Object actual = input.readMap();

        Map<Object, Object> expected = new HashMap<>();
        expected.put(1, "hello");

        assertEquals(expected, actual);
    }

}