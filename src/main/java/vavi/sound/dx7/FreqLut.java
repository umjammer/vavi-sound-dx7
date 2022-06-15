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


import java.util.HashMap;
import java.util.Map;


/**
 * Resolve frequency signal (1.0 in Q24 format = 1 octave) to phase delta.
 * <p>
 * The LUT is just a global, and we'll need the init function to be called before
 * use.
 */
public class FreqLut {
    private static final int LG_N_SAMPLES = 10;
    private static final int N_SAMPLES = 1 << LG_N_SAMPLES;
    private static final int SAMPLE_SHIFT = 24 - LG_N_SAMPLES;
    private static final int MAX_LOGFREQ_INT = 20;

    private int[] lut = new int[N_SAMPLES + 1];

    FreqLut(double sampleRate) {
        double y = (1L << (24 + MAX_LOGFREQ_INT)) / sampleRate;
        double inc = Math.pow(2, 1.0 / N_SAMPLES);
        for (int i = 0; i < N_SAMPLES + 1; i++) {
            lut[i] = (int) Math.floor(y + 0.5);
            y *= inc;
        }
    }

    // Note: if logFreq is more than 20.0, the results will be inaccurate. However,
    // that will be many times the Nyquist rate.
    public int lookup(int logFreq) {
        int ix = (logFreq & 0xffffff) >> SAMPLE_SHIFT;

        int y0 = lut[ix];
        int y1 = lut[ix + 1];
        int lowBits = logFreq & ((1 << SAMPLE_SHIFT) - 1);
        int y = (int) (y0 + ((((long) (y1 - y0) * (long) lowBits)) >> SAMPLE_SHIFT));
        int hiBits = logFreq >> 24;
        return y >> (MAX_LOGFREQ_INT - hiBits);
    }
}
