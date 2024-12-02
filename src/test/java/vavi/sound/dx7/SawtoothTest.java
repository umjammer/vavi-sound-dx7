/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.dx7;

import java.nio.file.Files;
import java.nio.file.Paths;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import static vavi.sound.SoundUtil.volume;


/**
 * SawtoothTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/31 umjammer initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class SawtoothTest {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "vavi.test.volume")
    final
    double volume = 0.2;

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
    }

    @Test
    void test() throws Exception {
        float sampleRate = 44100.0f;
        final int nSamples = 400 * 1024;

        Sawtooth s = Sawtooth.getInstance(sampleRate);
        int[] controlLast = new int[1];
        int[] control = new int[1];

        Context context = Context.getInstance(sampleRate);
        ResoFilter rf = new ResoFilter(context);
        int[] fcLast = new int[2];
        int[] fc = new int[2];
        fc[0] = 0; // TODO
        fc[1] = (int) (4.2 * (1 << 24));
        fcLast[0] = fc[0];
        fcLast[1] = fc[1];

        double ramp = 1e-7;
        double f0 = ramp * (64 + 1);
        control[0] = (int) ((1 << 24) * Math.log(f0 * sampleRate) / Math.log(2));

        AudioFormat audioFormat = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat, AudioSystem.NOT_SPECIFIED);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
//        CountDownLatch cdl = new CountDownLatch(1);
        line.addLineListener(event -> Debug.println(event.getType()));
        line.open();
        volume(line, volume);
        line.start();

        int[] buf = new int[64];
        int[] buf2 = new int[64];
        int[][] bufs = new int[1][];
        int[][] bufs2 = new int[1][];
        bufs[0] = buf;
        bufs2[0] = buf2;
//        int phase = 0;
        for (int i = 0; i < nSamples; i += 64) {

            double f = ramp * (i + 64 + 1);
            // f = 44.0 / sampleRate;
            controlLast[0] = control[0];
            control[0] = (int) ((1 << 24) * Math.log(f * sampleRate) / Math.log(2));
            fcLast[1] = fc[1];
            fc[1] = (int) (4.0 * i * (1 << 24) / nSamples);
            s.process(null, control, controlLast, bufs);
            rf.process(bufs, fc, fcLast, bufs2);
            for (int j = 0; j < 64; j++) {
                buf2[j] = buf[j] >> 1;
//                phase += 100000;
//                buf2[j] = (Sin.compute(phase) - (int)((1<< 24) * sin(phase * 2 * M_PI / (1 << 24)))) << 12;
            }
            writeData(line, buf2, 64);
        }
        line.drain();
        line.close();
    }

    static final byte[] sampleBuf = new byte[128];

    static void writeData(SourceDataLine line, int[] buf, int n) {
        int delta = 0x100;
        for (int i = 0; i < n; i++) {
            int val = buf[i];
            int clipVal = val < -(1 << 24) ? 0x8000 : (val >= (1 << 24) ? 0x7fff : (val + delta) >> 9);
            delta = (delta + val) & 0x1ff;
            sampleBuf[i * 2] = (byte) (clipVal & 0xff);
            sampleBuf[i * 2 + 1] = (byte) ((clipVal >> 8) & 0xff);
        }
        line.write(sampleBuf, 0, n * 2);
    }
}
