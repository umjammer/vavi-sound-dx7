/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vavi.sound.dx7;


/**
 * Resolve frequency signal (1.0 in Q24 format = 1 octave) to phase delta.
 * <p>
 * The LUT is just a global, and we'll need the init function to be called before
 * use.
 */
public class Freqlut {
    static final int LG_N_SAMPLES = 10;
    static final int N_SAMPLES = 1 << LG_N_SAMPLES;
    static final int SAMPLE_SHIFT = 24 - LG_N_SAMPLES;
    static final int MAX_LOGFREQ_INT = 20;

    static int[] lut = new int[N_SAMPLES + 1];

    public static void init(double sample_rate) {
        double y = (1L << (24 + MAX_LOGFREQ_INT)) / sample_rate;
        double inc = Math.pow(2, 1.0 / N_SAMPLES);
        for (int i = 0; i < N_SAMPLES + 1; i++) {
            lut[i] = (int) Math.floor(y + 0.5);
            y *= inc;
        }
    }

    // Note: if logfreq is more than 20.0, the results will be inaccurate. However,
    // that will be many times the Nyquist rate.
    static int lookup(int logfreq) {
        int ix = (logfreq & 0xffffff) >> SAMPLE_SHIFT;

        int y0 = lut[ix];
        int y1 = lut[ix + 1];
        int lowbits = logfreq & ((1 << SAMPLE_SHIFT) - 1);
        int y = (int) (y0 + ((((long) (y1 - y0) * (long) lowbits)) >> SAMPLE_SHIFT));
        int hibits = logfreq >> 24;
        return y >> (MAX_LOGFREQ_INT - hibits);
    }
}
