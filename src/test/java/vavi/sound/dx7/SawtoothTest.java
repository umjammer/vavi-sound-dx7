/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.dx7;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import org.junit.jupiter.api.Test;

import vavi.util.Debug;

import static vavi.sound.SoundUtil.volume;


/**
 * SawtoothTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/31 umjammer initial version <br>
 */
class SawtoothTest {

    @Test
    void test() throws Exception {
        double sample_rate = 44100.0;
        final int n_samples = 400 * 1024;

        Sawtooth.init(sample_rate);

        Sawtooth s = new Sawtooth();
        int[] control_last = new int[1];
        int[] control = new int[1];

        ResoFilter rf = new ResoFilter();
        int[] fc_last = new int[2];
        int[] fc = new int[2];
        fc[0] = 0; // TODO
        fc[1] = (int) (4.2 * (1 << 24));
        fc_last[0] = fc[0];
        fc_last[1] = fc[1];

        double ramp = 1e-7;
        double f0 = ramp * (64 + 1);
        control[0] = (int) ((1 << 24) * Math.log(f0 * sample_rate) / Math.log(2));

        AudioFormat audioFormat = new AudioFormat((float) sample_rate, 16, 1, true, false);
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat, AudioSystem.NOT_SPECIFIED);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
//        CountDownLatch cdl = new CountDownLatch(1);
        line.addLineListener(event -> { Debug.println(event.getType()); });
        line.open();
        volume(line, .2d);
        line.start();

        int[] buf = new int[64];
        int[] buf2 = new int[64];
        int[][] bufs = new int[1][];
        int[][] bufs2 = new int[1][];
        bufs[0] = buf;
        bufs2[0] = buf2;
//        int phase = 0;
        for (int i = 0; i < n_samples; i += 64) {

            double f = ramp * (i + 64 + 1);
            // f = 44.0 / sample_rate;
            control_last[0] = control[0];
            control[0] = (int) ((1 << 24) * Math.log(f * sample_rate) / Math.log(2));
            fc_last[1] = fc[1];
            fc[1] = (int) (4.0 * i * (1 << 24) / n_samples);
            s.process(null, control, control_last, bufs);
            rf.process(bufs, fc, fc_last, bufs2);
            for (int j = 0; j < 64; j++) {
                buf2[j] = buf[j] >> 1;
//                phase += 100000;
//                buf2[j] = (Sin.compute(phase) - (int)((1<< 24) * sin(phase * 2 * M_PI / (1 << 24)))) << 12;
            }
            write_data(line, buf2, 64);
        }
        line.drain();
        line.close();
    }

    static byte[] sample_buf = new byte[128];

    static void write_data(SourceDataLine line, final int[] buf, int n) {
        int delta = 0x100;
        for (int i = 0; i < n; i++) {
            int val = buf[i];
            int clip_val = val < -(1 << 24) ? 0x8000 : (val >= (1 << 24) ? 0x7fff : (val + delta) >> 9);
            delta = (delta + val) & 0x1ff;
            sample_buf[i * 2] = (byte) (clip_val & 0xff);
            sample_buf[i * 2 + 1] = (byte) ((clip_val >> 8) & 0xff);
        }
        line.write(sample_buf, 0, n * 2);
    }
}

/* */
