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

// Resonant filter implementation. This closely follows "Non-Linear
// Digital Implementation of the Moog Ladder Filter" by Antti
// Huovilainen, 2004.

// The full implementation requires both a tuning table and 2x
// oversampling, neither of which are present yet, but we'll get there. 

package vavi.sound.dx7;

import vavi.util.Debug;


class ResoFilter {
    static double this_sample_rate;

    static void init(double sample_rate) {
        this_sample_rate = sample_rate;
    }

    int[] x = new int[4];
    int[] w = new int[4];
    int yy;

    ResoFilter() {
        for (int i = 0; i < 4; i++) {
            x[i] = 0;
            w[i] = 0;
        }
    }

    int compute_alpha(int logf) {
        return Math.min(1 << 24, Freqlut.lookup(logf));
    }

    // Some really generic 4x4 matrix multiplication operations, suitable
    // for NEON'ing
    private void matmult4(float[] dst, int dP, final float[] a, int aP, final float[] b, int bP) {
        dst[dP + 0] = a[aP + 0] * b[bP + 0] + a[aP + 4] * b[bP + 1] + a[aP + 8] * b[bP + 2] + a[aP + 12] * b[bP + 3];
        dst[dP + 1] = a[aP + 1] * b[bP + 0] + a[aP + 5] * b[bP + 1] + a[aP + 9] * b[bP + 2] + a[aP + 13] * b[bP + 3];
        dst[dP + 2] = a[aP + 2] * b[bP + 0] + a[aP + 6] * b[bP + 1] + a[aP + 10] * b[bP + 2] + a[aP + 14] * b[bP + 3];
        dst[dP + 3] = a[aP + 3] * b[bP + 0] + a[aP + 7] * b[bP + 1] + a[aP + 11] * b[bP + 2] + a[aP + 15] * b[bP + 3];
        dst[dP + 4] = a[aP + 0] * b[bP + 4] + a[aP + 4] * b[bP + 5] + a[aP + 8] * b[bP + 6] + a[aP + 12] * b[bP + 7];
        dst[dP + 5] = a[aP + 1] * b[bP + 4] + a[aP + 5] * b[bP + 5] + a[aP + 9] * b[bP + 6] + a[aP + 13] * b[bP + 7];
        dst[dP + 6] = a[aP + 2] * b[bP + 4] + a[aP + 6] * b[bP + 5] + a[aP + 10] * b[bP + 6] + a[aP + 14] * b[bP + 7];
        dst[dP + 7] = a[aP + 3] * b[bP + 4] + a[aP + 7] * b[bP + 5] + a[aP + 11] * b[bP + 6] + a[aP + 15] * b[bP + 7];
        dst[dP + 8] = a[aP + 0] * b[bP + 8] + a[aP + 4] * b[bP + 9] + a[aP + 8] * b[bP + 10] + a[aP + 12] * b[bP + 11];
        dst[dP + 9] = a[aP + 1] * b[bP + 8] + a[aP + 5] * b[bP + 9] + a[aP + 9] * b[bP + 10] + a[aP + 13] * b[bP + 11];
        dst[dP + 10] = a[aP + 2] * b[bP + 8] + a[aP + 6] * b[bP + 9] + a[aP + 10] * b[bP + 10] + a[aP + 14] * b[bP + 11];
        dst[dP + 11] = a[aP + 3] * b[bP + 8] + a[aP + 7] * b[bP + 9] + a[aP + 11] * b[bP + 10] + a[aP + 15] * b[bP + 11];
        dst[dP + 12] = a[aP + 0] * b[bP + 12] + a[aP + 4] * b[bP + 13] + a[aP + 8] * b[bP + 14] + a[aP + 12] * b[bP + 15];
        dst[dP + 13] = a[aP + 1] * b[bP + 12] + a[aP + 5] * b[bP + 13] + a[aP + 9] * b[bP + 14] + a[aP + 13] * b[bP + 15];
        dst[dP + 14] = a[aP + 2] * b[bP + 12] + a[aP + 6] * b[bP + 13] + a[aP + 10] * b[bP + 14] + a[aP + 14] * b[bP + 15];
        dst[dP + 15] = a[aP + 3] * b[bP + 12] + a[aP + 7] * b[bP + 13] + a[aP + 11] * b[bP + 14] + a[aP + 15] * b[bP + 15];
    }

    private void matvec4(float[] dst, int dP, final float[] a, int aP, final float[] b, int bP) {
        dst[dP + 0] = a[aP + 0] * b[bP + 0] + a[aP + 4] * b[bP + 1] + a[aP + 8] * b[bP + 2] + a[aP + 12] * b[bP + 3];
        dst[dP + 1] = a[aP + 1] * b[bP + 0] + a[aP + 5] * b[bP + 1] + a[aP + 9] * b[bP + 2] + a[aP + 13] * b[bP + 3];
        dst[dP + 2] = a[aP + 2] * b[bP + 0] + a[aP + 6] * b[bP + 1] + a[aP + 10] * b[bP + 2] + a[aP + 14] * b[bP + 3];
        dst[dP + 3] = a[aP + 3] * b[bP + 0] + a[aP + 7] * b[bP + 1] + a[aP + 11] * b[bP + 2] + a[aP + 15] * b[bP + 3];
    }

    private void vecupdate4(float[] dst, float x, final float[] a) {
        for (int i = 0; i < 4; i++) {
            dst[i] += x * a[i];
        }
    }

    /* compute dst := dst + x * a */
    private void matupdate4(float[] dst, int dP, float x, final float[] a, int aP) {
        for (int i = 0; i < 16; i++) {
            dst[i + dP] += x * a[i + aP];
        }
    }

    private void matcopy(float[] dst, int dP, final float[] src, int sP, int n) {
        System.arraycopy(src, sP, dst, dP, n);
    }

    void dump_matrix(final float[] a) {
        for (int row = 0; row < 5; row++) {
            Debug.printf("%s[", row == 0 ? "[" : " ");
            for (int col = 0; col < 5; col++) {
                float x = (float) (row == 0 ? (col == 0 ? 1.0 : 0.0) : a[col * 4 + (row - 1)]);
                Debug.printf("%6f ", x);
            }
            Debug.printf("]%s\n", row == 4 ? "]" : "");
        }
    }

    private void make_state_transition(float[] result, int f0, int k) {
        // TODO: these should depend on k, and be just enough to meet error
        // bound
        int n1 = 4;
        int n2 = 4;
        float f = (float) (f0 * (1.0 / (1 << (24 + n2))));
        float k_f = (float) (k * (1.0 / (1 << 24)));
        k_f = Math.min(k_f, 3.98f);

        // these are 5x5 matrices of which we store the bottom 5x4
        // Top row of Jacobian is all zeros
        float[] j = new float[20];

        // set up initial jacobian
        j[0] = f;
        j[4] = -f;
        j[5] = f;
        j[9] = -f;
        j[10] = f;
        j[14] = -f;
        j[15] = f;
        j[16] = -k_f * f;
        j[19] = -f;

        // Top row of exponential is [1 0 0 0 0]
        float[] a = new float[20];
        a[0] = 0;
        a[4] = 1.0f;
        a[9] = 1.0f;
        a[14] = 1.0f;
        a[19] = 1.0f;

        float[] c = new float[20];
        matcopy(c, 0, j, 0, 20);

        final float[] scales = { 1.0f, 1 / 2.0f, 1 / 6.0f, 1 / 24.0f };
        // taylor's series to n1
        for (int i = 0; i < n1; i++) {
            float scale = scales[i];
            vecupdate4(a, scale, c);
            matupdate4(a, 4, scale, c, 4);
            if (i < n1 - 1) {
                float[] tmp = new float[20];
                matvec4(tmp, 0, c, 4, j, 0);
                matmult4(tmp, 4, c, 4, j, 4);
                matcopy(c, 0, tmp, 0, 20);
            }
        }

        // repeated squaring
        for (int i = 0; i < n2; i++) {
            float[] tmp = new float[20];
            matvec4(tmp, 0, a, 4, a, 0);
            matmult4(tmp, 4, a, 4, a, 4);
            for (int l = 0; l < 4; l++) {
                a[l] += tmp[l];
            }
            matcopy(a, 4, tmp, 4, 16);
        }

        matcopy(result, 0, a, 0, 20);
    }

    void test_matrix() {
        float[] params = { 1.0f, 3.99f };
        float[] a = new float[20];
        make_state_transition(a, (int) (params[0] * (1 << 24)), (int) (params[1] * (1 << 24)));
        dump_matrix(a);
    }

    void process(final int[][] inbufs, final int[] control_in, final int[] control_last, int[][] outbufs) {
        int alpha = compute_alpha(control_last[0]);
        int alpha_in = compute_alpha(control_in[0]);
        int delta_alpha = (alpha_in - alpha) >> Note.LG_N;
        int k = control_last[1];
        int k_in = control_in[1];
        int delta_k = (k_in - k) >> Note.LG_N;
        if ((((long) alpha_in * (long) k_in) >> 24) > 1 << 24) {
            k_in = ((1 << 30) / alpha_in) << 18;
        }
        if ((((long) alpha * (long) k) >> 24) > 1 << 24) {
            k = ((1 << 30) / alpha) << 18;
        }
        final int[] ibuf = inbufs[0];
        int[] obuf = outbufs[0];
        int x0 = x[0];
        int x1 = x[1];
        int x2 = x[2];
        int x3 = x[3];
        int w0 = w[0];
        int w1 = w[1];
        int w2 = w[2];
        int w3 = w[3];
        int yy0 = yy;
        for (int i = 0; i < Note.N; i++) {
            alpha += delta_alpha;
            k += delta_k;
            int signal = ibuf[i];
            int fb = (int) (((long) k * (long) (x3 + yy0)) >> 25);
            yy0 = x3;
            int rx = signal - fb;
            int trx = Tanh.lookup(rx);
            x0 = (int) (x0 + ((((long) (trx - w0) * (long) alpha)) >> 24));
            w0 = Tanh.lookup(x0);
            x1 = (int) (x1 + ((((long) (w0 - w1) * (long) alpha)) >> 24));
            w1 = Tanh.lookup(x1);
            x2 = (int) (x2 + ((((long) (w1 - w2) * (long) alpha)) >> 24));
            w2 = Tanh.lookup(x2);
            x3 = (int) (x3 + ((((long) (w2 - w3) * (long) alpha)) >> 24));
            w3 = Tanh.lookup(x3);
            obuf[i] = x3;
        }
        x[0] = x0;
        x[1] = x1;
        x[2] = x2;
        x[3] = x3;
        w[0] = w0;
        w[1] = w1;
        w[2] = w2;
        w[3] = w3;
        yy = yy0;
    }
}
