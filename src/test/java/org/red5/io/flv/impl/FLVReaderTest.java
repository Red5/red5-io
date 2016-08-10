package org.red5.io.flv.impl;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.red5.io.ITag;
import org.red5.io.flv.IKeyFrameDataAnalyzer.KeyFrameMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FLVReaderTest {

    private static Logger log = LoggerFactory.getLogger(FLVReaderTest.class);

    @Test
    public void testFLVReaderFileWithPreProcessInfo() {
        File file = new File("target/test-classes/fixtures/131647.flv");
        //File file = new File("target/test-classes/fixtures/test.flv");
        try {
            FLVReader reader = new FLVReader(file, true);
            KeyFrameMeta meta = reader.analyzeKeyFrames();
            log.debug("Meta: {}", meta);
            ITag tag = null;
            for (int t = 0; t < 6; t++) {
                tag = reader.readTag();
                log.debug("Tag: {}", tag);
            }
            reader.close();
            log.info("----------------------------------------------------------------------------------");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testFLVReaderFile() {
        File[] files = new File[] { new File("target/test-classes/fixtures/h264_aac.flv"), new File("target/test-classes/fixtures/h264_mp3.flv"), new File("target/test-classes/fixtures/h264_speex.flv") };
        try {
            for (File file : files) {
                FLVReader reader = new FLVReader(file, true);

                KeyFrameMeta meta = reader.analyzeKeyFrames();
                log.debug("Meta: {}", meta);

                ITag tag = null;
                for (int t = 0; t < 6; t++) {
                    tag = reader.readTag();
                    log.debug("Tag: {}", tag);
                }
                reader.close();
                log.info("----------------------------------------------------------------------------------");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    @Test
//    public void testFLVReaderFileWithMetaData() {
//        File[] files = new File[] { new File("target/test-classes/fixtures/flashContent.flv") };
//        try {
//            for (File file : files) {
//                log.info("\nFile: {}", file.getName());
//                FLVReader reader = new FLVReader(file, false);
//                ITag tag = null;
//                while (reader.hasMoreTags()) {
//                    tag = reader.readTag();
//                    if (tag.getDataType() > 9) {
//                        log.debug("Tag: {}", tag);
//                    }
//                }
//                reader.close();
//                log.info("----------------------------------------------------------------------------------");
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

}
