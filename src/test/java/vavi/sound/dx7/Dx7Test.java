/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.dx7;

import java.util.Random;

import org.junit.jupiter.api.Test;

import vavi.util.Debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * Dx7Test.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/31 umjammer initial version <br>
 */
class Dx7Test {

    @Test
    void test_sin_accuracy() {
Debug.println("test_sin_accuracy ----");
        Random random = new Random();
        double maxErr = 0;
        for (int i = 0; i < 1000000; i++) {
          int phase = random.nextInt() & ((1 << 24) - 1);
          int y = Sin.compute(phase);
          double yd = (1 << 24) * Math.sin(phase * (Math.PI / (1 << 23)));
          double err = Math.abs(y - yd);
          if (err > maxErr) maxErr = err;
        }
Debug.println("Max error: " + maxErr);
        assertEquals(2.5, maxErr, 0.05);
    }

    @Test
    void test_tanh_accuracy() {
Debug.println("test_tanh_accuracy ----");
        Random random = new Random();
        double maxErr = 0;
        for (int i = 0; i < 1000000; i++) {
            int x = (random.nextInt() & ((1 << 29) - 1)) - (1 << 28);
            int y = Tanh.lookup(x);
            double yd = (1 << 24) * Math.tanh(x * (1.0 / (1 << 24)));
            double err = Math.abs(y - yd);
            if (err > maxErr) {
Debug.println("x = " + x + ", y = " + y + ", yd = " + (int) yd);
              maxErr = err;
            }
        }
Debug.println("Max error: " + maxErr);
        assertEquals(25.8, maxErr, 0.15);
    }

    @Test
    void test_exp2() {
Debug.println("test_exp2 --------");
        for (int i = -16 << 24; i < 6 << 24; i += 123) {
            int result = Exp2.lookup(i);
            int accurate = (int) Math.floor((1 << 24) * Math.pow(2, i * 1.0 / (1 << 24)) + .5);
            int error = accurate - result;
            if (Math.abs(error) > 1 && Math.abs(error) > accurate / 10000000) {
Debug.println(i + ": " + result + " " + accurate);
                fail();
            }
        }
    }

    @Test
    void test_pure_accuracy() {
Debug.println("test_pure_accuracy ----");
        Random random = new Random();
        int worstFreq = 0;
        int worstPhase = 0;
        int worstErr = 0;
        double errsum = 0;
        for (int i = 0; i < 1000000; i++) {
            int freq = random.nextInt() & 0x7fffff;
            int phase = random.nextInt() & 0xffffff;
            int gain = 1 << 24;
            int[] buf = new int[64];
            FmOpKernel.computePure(buf, phase, freq, gain, gain, false);
            int maxerr = 0;
            for (int j = 0; j < 64; j++) {
                double y = gain * Math.sin((phase + j * freq) * (2.0 * Math.PI / (1 << 24)));
                int accurate = (int) Math.floor(y + 0.5);
                int err = Math.abs(buf[j] - accurate);
                if (err > maxerr)
                    maxerr = err;
            }
            errsum += maxerr;
            if (maxerr > worstErr) {
                worstErr = maxerr;
                worstFreq = freq;
                worstPhase = phase;
            }
            if (i < 10) {
Debug.println(phase + " " + freq + " " + maxerr);
            }
        }
Debug.println(worstPhase + " " + worstFreq + " " + worstErr);
Debug.println("Mean: " + (errsum * 1e-6));
        assertEquals(77.08, errsum * 1e-6, 0.015);
    }
}
