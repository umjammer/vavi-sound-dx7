/*
 * Copyright 2013 Google Inc.
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


class Exp2 {

    static final int EXP2_LG_N_SAMPLES = 10;
    static final int EXP2_N_SAMPLES = 1 << EXP2_LG_N_SAMPLES;

    static int[] exp2tab = new int[EXP2_N_SAMPLES << 1];

    static {
        double inc = Math.pow(2, 1.0 / EXP2_N_SAMPLES);
        double y = 1 << 30;
        for (int i = 0; i < EXP2_N_SAMPLES; i++) {
            exp2tab[(i << 1) + 1] = (int) Math.floor(y + 0.5);
            y *= inc;
        }
        for (int i = 0; i < EXP2_N_SAMPLES - 1; i++) {
            exp2tab[i << 1] = exp2tab[(i << 1) + 3] - exp2tab[(i << 1) + 1];
        }
        exp2tab[(EXP2_N_SAMPLES << 1) - 2] = (1 << 31) - exp2tab[(EXP2_N_SAMPLES << 1) - 1];
    }

    static final int lookup(int x) {
        final int SHIFT = 24 - EXP2_LG_N_SAMPLES;
        int lowbits = x & ((1 << SHIFT) - 1);
        int x_int = (x >> (SHIFT - 1)) & ((EXP2_N_SAMPLES - 1) << 1);
        int dy = exp2tab[x_int];
        int y0 = exp2tab[x_int + 1];

        int y = (int) (y0 + (((long)dy * (long)lowbits) >> SHIFT));
        return y >> (6 - (x >> 24));
      }
}

class Tanh {
    static final int TANH_LG_N_SAMPLES = 10;
    static final int TANH_N_SAMPLES = 1 << TANH_LG_N_SAMPLES;

    static int[] tanhtab = new int[TANH_N_SAMPLES << 1];

    static {
        double step = 4.0 / TANH_N_SAMPLES;
        double y = 0;
        for (int i = 0; i < TANH_N_SAMPLES; i++) {
            tanhtab[(i << 1) + 1] = (int) ((1 << 24) * y + 0.5);
            // printf("%d\n", tanhtab[(i << 1) + 1]);
            // Use a basic 4th order Runge-Kutte to compute tanh from its
            // differential equation.
            double k1 = dtanh(y);
            double k2 = dtanh(y + 0.5 * step * k1);
            double k3 = dtanh(y + 0.5 * step * k2);
            double k4 = dtanh(y + step * k3);
            double dy = (step / 6) * (k1 + k4 + 2 * (k2 + k3));
            y += dy;
        }
        for (int i = 0; i < TANH_N_SAMPLES - 1; i++) {
            tanhtab[i << 1] = tanhtab[(i << 1) + 3] - tanhtab[(i << 1) + 1];
        }
        int lasty = (int) ((1 << 24) * y + 0.5);
        tanhtab[(TANH_N_SAMPLES << 1) - 2] = lasty - tanhtab[(TANH_N_SAMPLES << 1) - 1];
    }

    static int lookup(int x) {
        int signum = x >> 31;
        x ^= signum;
        if (x >= (4 << 24)) {
            if (x >= (17 << 23)) {
                return signum ^ (1 << 24);
            }
            int sx = (int) (((long) -48408812 * (long) x) >> 24);
            return signum ^ ((1 << 24) - 2 * Exp2.lookup(sx));
        } else {
            final int SHIFT = 26 - TANH_LG_N_SAMPLES;
            int lowbits = x & ((1 << SHIFT) - 1);
            int x_int = (x >> (SHIFT - 1)) & ((TANH_N_SAMPLES - 1) << 1);
            int dy = tanhtab[x_int];
            int y0 = tanhtab[x_int + 1];
            int y = (int) (y0 + (((long) dy * (long) lowbits) >> SHIFT));
            return y ^ signum;
        }
    }

    private static double dtanh(double y) {
        return 1 - y * y;
    }
}
