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
 * Resonant filter implementation. This closely follows "Non-Linear
 * Digital Implementation of the Moog Ladder Filter" by Antti
 * Huovilainen, 2004.
 *
 * The full implementation requires both a tuning table and 2x
 * oversampling, neither of which are present yet, but we'll get there.
 */
public class ResoFilter {

    private int[] x = new int[4];
    private int[] w = new int[4];
    private int yy;

    private Context context;

    public ResoFilter(Context context) {
        for (int i = 0; i < 4; i++) {
            x[i] = 0;
            w[i] = 0;
        }
        this.context = context;
    }

    private int computeAlpha(int logf) {
        return Math.min(1 << 24, context.freqLut.lookup(logf));
    }

    // Some really generic 4x4 matrix multiplication operations, suitable
    // for NEON'ing
    private static void matMult4(float[] dst, int dP, float[] a, int aP, float[] b, int bP) {
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

    private static void matVec4(float[] dst, int dP, float[] a, int aP, float[] b, int bP) {
        dst[dP + 0] = a[aP + 0] * b[bP + 0] + a[aP + 4] * b[bP + 1] + a[aP + 8] * b[bP + 2] + a[aP + 12] * b[bP + 3];
        dst[dP + 1] = a[aP + 1] * b[bP + 0] + a[aP + 5] * b[bP + 1] + a[aP + 9] * b[bP + 2] + a[aP + 13] * b[bP + 3];
        dst[dP + 2] = a[aP + 2] * b[bP + 0] + a[aP + 6] * b[bP + 1] + a[aP + 10] * b[bP + 2] + a[aP + 14] * b[bP + 3];
        dst[dP + 3] = a[aP + 3] * b[bP + 0] + a[aP + 7] * b[bP + 1] + a[aP + 11] * b[bP + 2] + a[aP + 15] * b[bP + 3];
    }

    private static void vecUpdate4(float[] dst, float x, float[] a) {
        for (int i = 0; i < 4; i++) {
            dst[i] += x * a[i];
        }
    }

    /* compute dst := dst + x * a */
    private static void matUpdate4(float[] dst, int dP, float x, float[] a, int aP) {
        for (int i = 0; i < 16; i++) {
            dst[i + dP] += x * a[i + aP];
        }
    }

    private static void dumpMatrix(float[] a) {
        for (int row = 0; row < 5; row++) {
            System.err.printf("%s[", row == 0 ? "[" : " ");
            for (int col = 0; col < 5; col++) {
                float x = (float) (row == 0 ? (col == 0 ? 1.0 : 0.0) : a[col * 4 + (row - 1)]);
                System.err.printf("%6f ", x);
            }
            System.err.printf("]%s\n", row == 4 ? "]" : "");
        }
    }

    private static void makeStateTransition(float[] result, int f0, int k) {
        // TODO: these should depend on k, and be just enough to meet error bound
        int n1 = 4;
        int n2 = 4;
        float f = f0 * (1.0f / (1 << (24 + n2)));
        float kF = k * (1.0f / (1 << 24));
        kF = Math.min(kF, 3.98f);

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
        j[16] = -kF * f;
        j[19] = -f;

        // Top row of exponential is [1 0 0 0 0]
        float[] a = new float[20];
        a[0] = 0;
        a[4] = 1.0f;
        a[9] = 1.0f;
        a[14] = 1.0f;
        a[19] = 1.0f;

        float[] c = new float[20];
        System.arraycopy(j, 0, c, 0, 20);

        float[] scales = { 1.0f, 1 / 2.0f, 1 / 6.0f, 1 / 24.0f };
        // taylor's series to n1
        for (int i = 0; i < n1; i++) {
            float scale = scales[i];
            vecUpdate4(a, scale, c);
            matUpdate4(a, 4, scale, c, 4);
            if (i < n1 - 1) {
                float[] tmp = new float[20];
                matVec4(tmp, 0, c, 4, j, 0);
                matMult4(tmp, 4, c, 4, j, 4);
                System.arraycopy(tmp, 0, c, 0, 20);
            }
        }

        // repeated squaring
        for (int i = 0; i < n2; i++) {
            float[] tmp = new float[20];
            matVec4(tmp, 0, a, 4, a, 0);
            matMult4(tmp, 4, a, 4, a, 4);
            for (int l = 0; l < 4; l++) {
                a[l] += tmp[l];
            }
            System.arraycopy(tmp, 4, a, 4, 16);
        }

        System.arraycopy(a, 0, result, 0, 20);
    }

    static void testMatrix() {
        float[] params = { 1.0f, 3.99f };
        float[] a = new float[20];
        makeStateTransition(a, (int) (params[0] * (1 << 24)), (int) (params[1] * (1 << 24)));
        dumpMatrix(a);
    }

    public void process(int[][] inBufs, int[] controlIn, int[] controlLast, int[][] outBufs) {
        int alpha = computeAlpha(controlLast[0]);
        int alphaIn = computeAlpha(controlIn[0]);
        int deltaAlpha = (alphaIn - alpha) >> Note.LG_N;
        int k = controlLast[1];
        int kIn = controlIn[1];
        int deltaK = (kIn - k) >> Note.LG_N;
        if ((((long) alphaIn * (long) kIn) >> 24) > 1 << 24) {
            kIn = ((1 << 30) / alphaIn) << 18;
        }
        if ((((long) alpha * (long) k) >> 24) > 1 << 24) {
            k = ((1 << 30) / alpha) << 18;
        }
        int[] iBuf = inBufs[0];
        int[] obuf = outBufs[0];
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
            alpha += deltaAlpha;
            k += deltaK;
            int signal = iBuf[i];
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
