/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.dx7;

import java.util.Random;

import org.junit.jupiter.api.Test;

import vavi.util.Debug;


/**
 * FmOpKernelTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/30 umjammer initial version <br>
 */
class FmOpKernelTest {

    @Test
    void test() {
        Random random = new Random();
        int worstfreq = 0;
        int worstphase = 0;
        int worsterr = 0;
        double errsum = 0;
        for (int i = 0; i < 1000000; i++) {
            int freq = random.nextInt() & 0x7fffff;
            int phase = random.nextInt() & 0xffffff;
            int gain = 1 << 24;
            int[] buf = new int[64];
            FmOpKernel.compute_pure(buf, phase, freq, gain, gain, false);
            int maxerr = 0;
            for (int j = 0; j < 64; j++) {
                double y = gain * Math.sin((phase + j * freq) * (2.0 * Math.PI / (1 << 24)));
                int accurate = (int) Math.floor(y + 0.5);
                int err = Math.abs(buf[j] - accurate);
                if (err > maxerr)
                    maxerr = err;
            }
            errsum += maxerr;
            if (maxerr > worsterr) {
                worsterr = maxerr;
                worstfreq = freq;
                worstphase = phase;
            }
            if (i < 10)
                Debug.println(phase + " " + freq + " " + maxerr);
        }
        Debug.println(worstphase + " " + worstfreq + " " + worsterr);
        Debug.println("Mean: " + (errsum * 1e-6));
    }
}

/* */
