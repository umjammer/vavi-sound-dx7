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
 * SinTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/31 umjammer initial version <br>
 */
class SinTest {

    @Test
    void test() {
        Random random = new Random();
        double maxerr = 0;
        for (int i = 0; i < 1000000; i++) {
          int phase = random.nextInt() & ((1 << 24) - 1);
          int y = Sin.compute(phase);
          double yd = (1 << 24) * Math.sin(phase * (Math.PI / (1 << 23)));
          double err = Math.abs(y - yd);
          if (err > maxerr) maxerr = err;
        }
        Debug.println("Max error: " + maxerr);
    }
}

/* */
