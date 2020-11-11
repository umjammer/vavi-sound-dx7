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

import java.util.logging.Level;

import vavi.util.Debug;


class Sawtooth {

    @SuppressWarnings("unused")
    private static final int R = (1 << 29);
    private static final int LG_N_SAMPLES = 10;
    private static final int N_SAMPLES = (1 << LG_N_SAMPLES);
    private static final int N_PARTIALS_MAX = (N_SAMPLES / 2);
    private static final int LG_SLICES_PER_OCTAVE = 2;
    private static final int SLICES_PER_OCTAVE = (1 << LG_SLICES_PER_OCTAVE);
    private static final int SLICE_SHIFT = (24 - LG_SLICES_PER_OCTAVE);
    private static final int SLICE_EXTRA = 3;
    private static final int N_SLICES = 36;
    // 0.5 * (log(440./44100) / log(2) + log(440./48000) / log(2) + 2./12) + 1./64 - 3 in Q24
    private static final int SLICE_BASE = 161217316;
    private static final int LOW_FREQ_LIMIT = -SLICE_BASE;
    private static final double NEG2OVERPI = -0.63661977236758138;

    private static int[][] sawtooth = new int[N_SLICES][N_SAMPLES];

    private static int sawtooth_freq_off;

    // There's a fair amount of lookup table and so on that needs to be set before
    // generating any signal. In Java, this would be done by a separate factory class.
    // Here, we're just going to do it as globals.
    public static void init(double sample_rate) {
        sawtooth_freq_off = (int) (-(1 << 24) * Math.log(sample_rate) / Math.log(2));
        int[] lut = new int[N_SAMPLES / 2];

        for (int i = 0; i < N_SAMPLES / 2; i++) {
            lut[i] = 0;
        }

        double slice_inc = Math.pow(2.0, 1.0 / SLICES_PER_OCTAVE);
        double f_0 = Math.pow(slice_inc, N_SLICES - 1) * Math.pow(0.5, SLICE_BASE * 1.0 / (1 << 24));
        int n_partials_last = 0;
        for (int j = N_SLICES - 1; j >= 0; j--) {
            int n_partials = (int) Math.floor(0.5 / f_0);
            n_partials = n_partials < N_PARTIALS_MAX ? n_partials : N_PARTIALS_MAX;
            // printf("slice %d: n_partials=%d\n", j, n_partials);
            for (int k = n_partials_last + 1; k <= n_partials; k++) {
                double scale = NEG2OVERPI / k;
                scale = (N_PARTIALS_MAX - k) > (N_PARTIALS_MAX >> 2) ? scale
                                                                     : scale * (N_PARTIALS_MAX - k) / (N_PARTIALS_MAX >> 2);
                double dphase = k * 2 * Math.PI / N_SAMPLES;
                int maxerr = 0;
                double ds_d = (1 << 30) * scale * Math.sin(dphase);
                double cm2_d = (1 << 29) * (2 * (Math.cos(dphase) - 1));
                int dshift = 0;
                for (dshift = 0; dshift < 16; dshift++) {
                    if (ds_d < -(1 << (30 - dshift)))
                        break;
                    if (cm2_d < -(1 << (30 - dshift)))
                        break;
                }
                int ds = (int) Math.floor((1 << dshift) * ds_d + 0.5);
                int cm2 = (int) Math.floor((1 << dshift) * cm2_d + 0.5);
                // cout << cm2_d << " " << cm2 << " " << dphase << " " << ds <<
                // " " << dshift << endl;
                int s = 0;
                int round = (1 << dshift) >> 1;
                for (int i = 0; i < N_SAMPLES / 2; i++) {
                    lut[i] += s;
                    int good = (int) Math.floor(scale * Math.sin(dphase * i) * (1 << 30) + 0.5);
                    int err = s - good;
                    int abs_err = err > 0 ? err : -err;
                    maxerr = abs_err > maxerr ? abs_err : maxerr;
                    ds += (int) (((long) cm2 * (long) s + (1 << 28)) >> 29);
                    s += (ds + round) >> dshift;
                }
                Debug.println(Level.FINE, maxerr);
            }
            sawtooth[j][0] = 0;
            sawtooth[j][N_SAMPLES / 2] = 0;
            for (int i = 1; i < N_SAMPLES / 2; i++) {
                int value = (lut[i] + 32) >> 6;
                sawtooth[j][i] = value;
                sawtooth[j][N_SAMPLES - i] = -value;
            }
            n_partials_last = n_partials;
            f_0 *= 1.0 / slice_inc;
        }
    }

    private int phase;

    public Sawtooth() {
        phase = 0;
    }

    private int compute(int phase) {
        return phase * 2 - (1 << 24);
    }

    private int lookup_1(int phase, int slice) {
        int phase_int = (phase >> (24 - LG_N_SAMPLES)) & (N_SAMPLES - 1);
        int lowbits = phase & ((1 << (24 - LG_N_SAMPLES)) - 1);
        int y0 = sawtooth[slice][phase_int];
        int y1 = sawtooth[slice][(phase_int + 1) & (N_SAMPLES - 1)];

        return (int) (y0 + ((((long) (y1 - y0) * (long) lowbits)) >> (24 - LG_N_SAMPLES)));
    }

    private int lookup_2(int phase, int slice, int slice_lowbits) {
        int phase_int = (phase >> (24 - LG_N_SAMPLES)) & (N_SAMPLES - 1);
        int lowbits = phase & ((1 << (24 - LG_N_SAMPLES)) - 1);
        int y0 = sawtooth[slice][phase_int];
        int y1 = sawtooth[slice][(phase_int + 1) & (N_SAMPLES - 1)];
        int y4 = (int) (y0 + ((((long) (y1 - y0) * (long) lowbits)) >> (24 - LG_N_SAMPLES)));

        int y2 = sawtooth[slice + 1][phase_int];
        int y3 = sawtooth[slice + 1][(phase_int + 1) & (N_SAMPLES - 1)];
        int y5 = (int) (y2 + ((((long) (y3 - y2) * (long) lowbits)) >> (24 - LG_N_SAMPLES)));

        return (int) (y4 + ((((long) (y5 - y4) * (long) slice_lowbits)) >> (SLICE_SHIFT - SLICE_EXTRA)));
    }

    public void process(final int[][] inbufs, final int[] control_in, final int[] control_last, int[][] outbufs) {
        int logf = control_last[0];
        int[] obuf = outbufs[0];
        int actual_logf = logf + sawtooth_freq_off;
        int f = Exp2.lookup(actual_logf);
        int p = phase;
        // choose a strategy based on the frequency
        if (actual_logf < LOW_FREQ_LIMIT - (1 << (SLICE_SHIFT - SLICE_EXTRA))) {
            for (int i = 0; i < Note.N; i++) {
                obuf[i] = compute(p);
                p += f;
                p &= (1 << 24) - 1;
            }
        } else if (actual_logf < LOW_FREQ_LIMIT) {
            // interpolate between computed and lookup
            int slice = (LOW_FREQ_LIMIT + SLICE_BASE + (1 << SLICE_SHIFT) - 1) >> SLICE_SHIFT;
            int slice_lowbits = actual_logf - LOW_FREQ_LIMIT + (1 << (SLICE_SHIFT - SLICE_EXTRA));
            for (int i = 0; i < Note.N; i++) {
                int yc = compute(p);
                int yl = lookup_1(p, slice + 1);
                obuf[i] = (int) (yc + ((((long) (yl - yc) * (long) slice_lowbits)) >> (SLICE_SHIFT - SLICE_EXTRA)));
                p += f;
                p &= (1 << 24) - 1;
            }
        } else {
            int slice = (actual_logf + SLICE_BASE + (1 << SLICE_SHIFT) - 1) >> SLICE_SHIFT;
            final int slice_start = (1 << SLICE_SHIFT) - (1 << (SLICE_SHIFT - SLICE_EXTRA));
            int slice_lowbits = ((actual_logf + SLICE_BASE) & ((1 << SLICE_SHIFT) - 1)) - slice_start;
            // slice < 0 can't happen because LOW_FREQ_LIMIT kicks in first
            if (slice > N_SLICES - 2) {
                if (slice > N_SLICES - 1 || slice_lowbits > 0) {
                    slice = N_SLICES - 1;
                    slice_lowbits = 0;
                }
            }
            if (slice_lowbits <= 0) {
                for (int i = 0; i < Note.N; i++) {
                    obuf[i] = lookup_1(p, slice);
                    p += f;
                    p &= (1 << 24) - 1;
                }
            } else {
                for (int i = 0; i < Note.N; i++) {
                    obuf[i] = lookup_2(p, slice, slice_lowbits);
                    p += f;
                    p &= (1 << 24) - 1;
                }
            }
        }
        phase = p;
    }
}
