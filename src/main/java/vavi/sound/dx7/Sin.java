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


class Sin {
    private static final int SIN_LG_N_SAMPLES = 10;
    private static final int SIN_N_SAMPLES = 1 << SIN_LG_N_SAMPLES;
    private static final int R = 1 << 29;

    private static int[] sinTab = new int[SIN_N_SAMPLES << 1];

    static {
        double dPhase = 2 * Math.PI / SIN_N_SAMPLES;
        int c = (int) Math.floor(Math.cos(dPhase) * (1 << 30) + 0.5);
        int s = (int) Math.floor(Math.sin(dPhase) * (1 << 30) + 0.5);
        int u = 1 << 30;
        int v = 0;
        for (int i = 0; i < SIN_N_SAMPLES / 2; i++) {
            sinTab[(i << 1) + 1] = (v + 32) >> 6;
            sinTab[((i + SIN_N_SAMPLES / 2) << 1) + 1] = -((v + 32) >> 6);
            int t = (int) (((long) u * (long) s + (long) v * (long) c + R) >> 30);
            u = (int) (((long) u * (long) c - (long) v * (long) s + R) >> 30);
            v = t;
        }
        for (int i = 0; i < SIN_N_SAMPLES - 1; i++) {
            sinTab[i << 1] = sinTab[(i << 1) + 3] - sinTab[(i << 1) + 1];
        }
        sinTab[(SIN_N_SAMPLES << 1) - 2] = -sinTab[(SIN_N_SAMPLES << 1) - 1];
    }

    public static int lookup(int phase) {
        final int SHIFT = 24 - SIN_LG_N_SAMPLES;
        int lowBits = phase & ((1 << SHIFT) - 1);
        int phaseInt = (phase >> (SHIFT - 1)) & ((SIN_N_SAMPLES - 1) << 1);
        int dy = sinTab[phaseInt];
        int y0 = sinTab[phaseInt + 1];

        return (int) (y0 + (((long) dy * (long) lowBits) >> SHIFT));
    }

    // coefficients are Chebyshev polynomial, computed by compute_cos_poly.py
    private static final int C8_0 = 16777216;
    private static final int C8_2 = -331168742;
    private static final int C8_4 = 1089453524;
    private static final int C8_6 = -1430910663;
    private static final int C8_8 = 950108533;

    public static int compute(int phase) {
        int x = (phase & ((1 << 23) - 1)) - (1 << 22);
        int x2 = (int) (((long) x * (long) x) >> 16);
        int y = (int) (((((((((((((long) C8_8
                * (long) x2) >> 32) + C8_6)
                * x2) >> 32) + C8_4)
                * x2) >> 32) + C8_2)
                * x2) >> 32) + C8_0);
        y ^= -((phase >> 23) & 1);
        return y;
    }

    private static final int C10_0 = 1 << 30;
    private static final int C10_2 = -1324675874; // scaled * 4
    private static final int C10_4 = 1089501821;
    private static final int C10_6 = -1433689867;
    private static final int C10_8 = 1009356886;
    private static final int C10_10 = -421101352;

    public static int compute10(int phase) {
        int x = (phase & ((1 << 29) - 1)) - (1 << 28);
        int x2 = (int) (((long) x * (long) x) >> 26);
        int y = (int) ((((((((((((((((long) C10_10
                * (long) x2) >> 34) + C10_8)
                * x2) >> 34) + C10_6)
                * x2) >> 34) + C10_4)
                * x2) >> 32) + C10_2)
                * x2) >> 30) + C10_0);
        y ^= -((phase >> 29) & 1);
        return y;
    }
}
