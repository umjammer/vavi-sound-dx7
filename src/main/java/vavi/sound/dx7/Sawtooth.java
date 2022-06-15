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
import java.util.logging.Level;

import vavi.util.Debug;


class Sawtooth {

    @SuppressWarnings("unused")
    private static final int R = 1 << 29;
    private static final int LG_N_SAMPLES = 10;
    private static final int N_SAMPLES = 1 << LG_N_SAMPLES;
    private static final int N_PARTIALS_MAX = N_SAMPLES / 2;
    private static final int LG_SLICES_PER_OCTAVE = 2;
    private static final int SLICES_PER_OCTAVE = 1 << LG_SLICES_PER_OCTAVE;
    private static final int SLICE_SHIFT = 24 - LG_SLICES_PER_OCTAVE;
    private static final int SLICE_EXTRA = 3;
    private static final int N_SLICES = 36;
    // 0.5 * (log(440./44100) / log(2) + log(440./48000) / log(2) + 2./12) + 1./64 - 3 in Q24
    private static final int SLICE_BASE = 161217316;
    private static final int LOW_FREQ_LIMIT = -SLICE_BASE;
    private static final double NEG2OVER_PI = -0.63661977236758138;

    private int[][] sawTooth = new int[N_SLICES][N_SAMPLES];

    private static int sawToothFreqOff;

    private static Map<Double, Sawtooth> instances = new HashMap<>();

    public static Sawtooth getInstance(double sampleRate) {
        if (instances.containsKey(sampleRate)) {
            return instances.get(sampleRate);
        } else {
            Sawtooth sawtooth = new Sawtooth(sampleRate);
            instances.put(sampleRate, sawtooth);
            return sawtooth;
        }
    }

    // There's a fair amount of lookup table and so on that needs to be set before
    // generating any signal. In Java, this would be done by a separate factory class.
    // Here, we're just going to do it as globals.
    private void init(double sampleRate) {
        sawToothFreqOff = (int) (-(1 << 24) * Math.log(sampleRate) / Math.log(2));
        int[] lut = new int[N_SAMPLES / 2];

        for (int i = 0; i < N_SAMPLES / 2; i++) {
            lut[i] = 0;
        }

        double sliceInc = Math.pow(2.0, 1.0 / SLICES_PER_OCTAVE);
        double f0 = Math.pow(sliceInc, N_SLICES - 1) * Math.pow(0.5, SLICE_BASE * 1.0 / (1 << 24));
        int nPartialsLast = 0;
        for (int j = N_SLICES - 1; j >= 0; j--) {
            int nPartials = (int) Math.floor(0.5 / f0);
            nPartials = Math.min(nPartials, N_PARTIALS_MAX);
            // System.err.printf("slice %d: nPartials=%d\n", j, nPartials);
            for (int k = nPartialsLast + 1; k <= nPartials; k++) {
                double scale = NEG2OVER_PI / k;
                scale = (N_PARTIALS_MAX - k) > (N_PARTIALS_MAX >> 2) ? scale
                                                                     : scale * (N_PARTIALS_MAX - k) / (N_PARTIALS_MAX >> 2);
                double dPhase = k * 2 * Math.PI / N_SAMPLES;
                int maxErr = 0;
                double dsD = (1 << 30) * scale * Math.sin(dPhase);
                double cm2D = (1 << 29) * (2 * (Math.cos(dPhase) - 1));
                int dShift = 0;
                for (dShift = 0; dShift < 16; dShift++) {
                    if (dsD < -(1 << (30 - dShift)))
                        break;
                    if (cm2D < -(1 << (30 - dShift)))
                        break;
                }
                int ds = (int) Math.floor((1 << dShift) * dsD + 0.5);
                int cm2 = (int) Math.floor((1 << dShift) * cm2D + 0.5);
                // Debug.println(cm2D + " " + cm2 + " " + dPhase + " " + ds + " " + dShift);
                int s = 0;
                int round = (1 << dShift) >> 1;
                for (int i = 0; i < N_SAMPLES / 2; i++) {
                    lut[i] += s;
                    int good = (int) Math.floor(scale * Math.sin(dPhase * i) * (1 << 30) + 0.5);
                    int err = s - good;
                    int absErr = err > 0 ? err : -err;
                    maxErr = Math.max(absErr, maxErr);
                    ds += (int) (((long) cm2 * (long) s + (1 << 28)) >> 29);
                    s += (ds + round) >> dShift;
                }
                Debug.println(Level.FINE, maxErr);
            }
            sawTooth[j][0] = 0;
            sawTooth[j][N_SAMPLES / 2] = 0;
            for (int i = 1; i < N_SAMPLES / 2; i++) {
                int value = (lut[i] + 32) >> 6;
                sawTooth[j][i] = value;
                sawTooth[j][N_SAMPLES - i] = -value;
            }
            nPartialsLast = nPartials;
            f0 *= 1.0 / sliceInc;
        }
    }

    private int phase;

    public Sawtooth(double sampleRate) {
        phase = 0;
        init(sampleRate);
    }

    private int compute(int phase) {
        return phase * 2 - (1 << 24);
    }

    private int lookup1(int phase, int slice) {
        int phaseInt = (phase >> (24 - LG_N_SAMPLES)) & (N_SAMPLES - 1);
        int lowBits = phase & ((1 << (24 - LG_N_SAMPLES)) - 1);
        int y0 = sawTooth[slice][phaseInt];
        int y1 = sawTooth[slice][(phaseInt + 1) & (N_SAMPLES - 1)];

        return (int) (y0 + ((((long) (y1 - y0) * (long) lowBits)) >> (24 - LG_N_SAMPLES)));
    }

    private int lookup2(int phase, int slice, int sliceLowBits) {
        int phaseInt = (phase >> (24 - LG_N_SAMPLES)) & (N_SAMPLES - 1);
        int lowBits = phase & ((1 << (24 - LG_N_SAMPLES)) - 1);
        int y0 = sawTooth[slice][phaseInt];
        int y1 = sawTooth[slice][(phaseInt + 1) & (N_SAMPLES - 1)];
        int y4 = (int) (y0 + ((((long) (y1 - y0) * (long) lowBits)) >> (24 - LG_N_SAMPLES)));

        int y2 = sawTooth[slice + 1][phaseInt];
        int y3 = sawTooth[slice + 1][(phaseInt + 1) & (N_SAMPLES - 1)];
        int y5 = (int) (y2 + ((((long) (y3 - y2) * (long) lowBits)) >> (24 - LG_N_SAMPLES)));

        return (int) (y4 + ((((long) (y5 - y4) * (long) sliceLowBits)) >> (SLICE_SHIFT - SLICE_EXTRA)));
    }

    public void process(final int[][] inBufs, final int[] controlIn, final int[] controlLast, int[][] outBufs) {
        int logf = controlLast[0];
        int[] oBuf = outBufs[0];
        int actualLogF = logf + sawToothFreqOff;
        int f = Exp2.lookup(actualLogF);
        int p = phase;
        // choose a strategy based on the frequency
        if (actualLogF < LOW_FREQ_LIMIT - (1 << (SLICE_SHIFT - SLICE_EXTRA))) {
            for (int i = 0; i < Note.N; i++) {
                oBuf[i] = compute(p);
                p += f;
                p &= (1 << 24) - 1;
            }
        } else if (actualLogF < LOW_FREQ_LIMIT) {
            // interpolate between computed and lookup
            int slice = (LOW_FREQ_LIMIT + SLICE_BASE + (1 << SLICE_SHIFT) - 1) >> SLICE_SHIFT;
            int sliceLowBits = actualLogF - LOW_FREQ_LIMIT + (1 << (SLICE_SHIFT - SLICE_EXTRA));
            for (int i = 0; i < Note.N; i++) {
                int yc = compute(p);
                int yl = lookup1(p, slice + 1);
                oBuf[i] = (int) (yc + ((((long) (yl - yc) * (long) sliceLowBits)) >> (SLICE_SHIFT - SLICE_EXTRA)));
                p += f;
                p &= (1 << 24) - 1;
            }
        } else {
            int slice = (actualLogF + SLICE_BASE + (1 << SLICE_SHIFT) - 1) >> SLICE_SHIFT;
            final int sliceStart = (1 << SLICE_SHIFT) - (1 << (SLICE_SHIFT - SLICE_EXTRA));
            int sliceLowBits = ((actualLogF + SLICE_BASE) & ((1 << SLICE_SHIFT) - 1)) - sliceStart;
            // slice < 0 can't happen because LOW_FREQ_LIMIT kicks in first
            if (slice > N_SLICES - 2) {
                if (slice > N_SLICES - 1 || sliceLowBits > 0) {
                    slice = N_SLICES - 1;
                    sliceLowBits = 0;
                }
            }
            if (sliceLowBits <= 0) {
                for (int i = 0; i < Note.N; i++) {
                    oBuf[i] = lookup1(p, slice);
                    p += f;
                    p &= (1 << 24) - 1;
                }
            } else {
                for (int i = 0; i < Note.N; i++) {
                    oBuf[i] = lookup2(p, slice, sliceLowBits);
                    p += f;
                    p &= (1 << 24) - 1;
                }
            }
        }
        phase = p;
    }
}
