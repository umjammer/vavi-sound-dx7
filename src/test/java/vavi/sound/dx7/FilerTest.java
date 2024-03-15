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

import java.util.Random;

import org.junit.jupiter.api.Test;

import vavi.util.Debug;


/**
 * Little test app for measuring FIR speed
 */
public class FilerTest {

    static long v = 0;

    void conditionGovernor() {
        // sleep for a bit to avoid thermal throttling
        try {
            Thread.sleep(900);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // consume cpu a bit to try to coax max cpufreq
        long x = v;
        for (int i = 0; i < 10000000; i++) {
            x += 42;
            x += (x << 10);
            x ^= (x >> 6);
        }
        // storing it in a static guarantees not optimizing out
        v = x;
    }

    float[] makeRandom(int size) {
        Random random = new Random(System.currentTimeMillis());
        float[] result = new float[size];
        for (int i = 0; i < size; i++) {
            result[i] = (float) (random.nextGaussian() * ((2.0 / Integer.MAX_VALUE) - 1));
        }
        return result;
    }

//    double test_accuracy(FirFilter<Float, Float> f1, FirFilter<Float, Float> f2, final float[] inp, int nBlock) {
//        float[] out1 = new float[nBlock];
//        float[] out2 = new float[nBlock];
//        f1.process(inp, 1, out1, nBlock);
//        f2.process(inp, 1, out2, nBlock);
//        double err = 0;
//        for (int i = 0; i < nBlock; i++) {
//            Debug.printf("#%d: %f %f\n", i, out1[i], out2[i]);
//            err += Math.abs(out1[i] - out2[i]);
//        }
//        return err;
//    }

//    void benchFir(int size, int experiment) {
//        conditionGovernor();
//
//        final int nBlock = 64;
//        float[] kernel = makeRandom(size);
//        float[] in = makeRandom(size + nBlock);
//        float[] out = new float[nBlock];
//        FirFilter<Float, Float> f;
//
//        switch (experiment) {
//        case 0:
//            f = new SimpleFirFilter(kernel, size);
//            break;
//        case 4:
//            f = new HalfRateFirFilter(kernel, size, nBlock);
//            break;
//        }
//
//        double start = System.currentTimeMillis();
//        for (int j = 0; j < 15625; j++) {
//            f.process(in, 1, out, nBlock);
//        }
//        double elapsed = System.currentTimeMillis() - start;
//        Debug.printf("%i %f\n", size, 1e3 * elapsed);
//
//        FirFilter<Float, Float> fbase = new SimpleFirFilter(kernel, size);
//        double accuracy = test_accuracy(fbase, f, in, nBlock);
//        Debug.printf("#accuracy = %g\n", accuracy);
//    }

//    @Test
//    void runFirBench() {
//        Debug.printf("set style data linespoints\n" + "set xlabel 'FIR kernel size'\n" + "set ylabel 'ns per sample'\n" +
//                     "plot '-' title 'scalar', '-' title '4x4 block', '-' title 'fixed16', '-' title 'fixed16 mirror', '-' title 'half rate'\n");
//        for (int experiment = 0; experiment < 6; experiment++) {
//            for (int i = 16; i <= 256; i += 16) {
//                benchFir(i, experiment);
//            }
//            Debug.printf("e\n");
//        }
//    }

    void scalarBiQuad(float[] inp, float[] out, int n, float b0, float b1, float b2, float a1, float a2) {
        float x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        for (int i = 0; i < n; i++) {
            float x = inp[i];
            float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            out[i] = y;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
        }
    }

    void benchScalarBiQuad() {
        conditionGovernor();
        final int nbuf = 1 << 10;
        float[] inp = makeRandom(nbuf);
        float[] out = new float[nbuf];

        double start = System.currentTimeMillis();
        final int niter = 10000;
        for (int i = 0; i < niter; i++) {
            scalarBiQuad(inp, out, nbuf, 1.0207f, -1.7719f, .9376f, -1.7719f, 0.9583f);
        }
        double elapsed = System.currentTimeMillis() - start;
        double ns_per_iir = 1e9 * elapsed / nbuf / niter;
        Debug.printf("scalar: %f ns/iir\n", ns_per_iir);
    }

    // see "lab/biquadin two.ipynb" for why
    void initBiquadMatrix(float[] matrix, double b0, double b1, double b2, double a1, double a2) {
        double c1 = b1 - a1 * b0;
        double c2 = b2 - a2 * b0;
        matrix[0] = (float) b0;
        matrix[1] = (float) c1;
        matrix[2] = (float) (-a1 * c1 + c2);
        matrix[3] = (float) (-a2 * c1);
        matrix[4] = 0;
        matrix[5] = (float) b0;
        matrix[6] = (float) c1;
        matrix[7] = (float) c2;
        matrix[8] = 1;
        matrix[9] = (float) -a1;
        matrix[10] = (float) (-a2 + a1 * a1);
        matrix[11] = (float) (a1 * a2);
        matrix[12] = 0;
        matrix[13] = 1;
        matrix[14] = (float) -a1;
        matrix[15] = (float) -a2;
    }

    @Test
    void runBiQuad() {
        benchScalarBiQuad();
    }

    @Test
    void runfmbench() {
        conditionGovernor();
        final int nBuf = 64;
        int[] out = new int[nBuf];

        int freq = (int) (440.0 / 44100.0 * (1 << 24));
        double start = System.currentTimeMillis();
        final int niter = 1000000;
        for (int i = 0; i < niter; i++) {
            FmOpKernel.compute(out, out, 0, freq, 1 << 24, 1 << 24, false);
        }

        double elapsed = System.currentTimeMillis() - start;
        double nsPerSample = 1e9 * elapsed / nBuf / niter;
        Debug.printf("fm op kernel: %f ms/sample\n", nsPerSample);
    }

    @Test
    void runSawBench() {
        conditionGovernor();
        double sampleRate = 44100.0;
        final int nBuf = 64;
        int[] out = new int[nBuf];
        Sawtooth s = Sawtooth.getInstance(sampleRate);
        int[] controlLast = new int[1];
        int[] control = new int[1];
        int[][] bufs = new int[1][];
        bufs[0] = out;

        for (int i = 0; i < 1; i++) {
            double f = 440.0 * (i + 1);
            control[0] = (int) ((1 << 24) * Math.log(f) / Math.log(2));
            controlLast[0] = control[0];

            double start = System.currentTimeMillis();
            final int niter = 1000000;
            for (int j = 0; j < niter; j++) {
                s.process(null, control, controlLast, bufs);
            }

            double elapsed = System.currentTimeMillis() - start;
            double nsPerSample = 1e9 * elapsed / nBuf / niter;
            Debug.printf("sawtooth %gHz: %f ms/sample\n", f, nsPerSample);
        }
    }

    @Test
    void runLadderBench() {
        ResoFilter.testMatrix();
        conditionGovernor();
        float sampleRate = 44100.0f;
        final int nBuf = 64;
        int[] in = new int[nBuf];
        int[] out = new int[nBuf];
        Context context = Context.getInstance(sampleRate);
        ResoFilter r = new ResoFilter(context);
        int[] controlLast = new int[3];
        int[] control = new int[3];
        int[][] inBufs = new int[1][];
        int[][] outBufs = new int[1][];
        inBufs[0] = in;
        outBufs[0] = out;

        for (int i = 0; i < nBuf; i++) {
            in[i] = (i - 32) << 18;
        }
        control[0] = 1 << 23;
        control[1] = 1 << 23;
        for (int nl = 0; nl < 2; nl++) {
            control[2] = nl << 20;
            double start = System.currentTimeMillis();
            final int niter = 1000000;
//            for (float f : in) System.err.printf("%f, ",  f); System.err.println();
            for (int i = 0; i < niter; i++) {
                r.process(inBufs, control, controlLast, outBufs);
            }
            for (float f : out) System.err.printf("%f, ",  f); System.err.println();

            double elapsed = System.currentTimeMillis() - start;
            double nsPerSample = 1e9 * elapsed / nBuf / niter;
            Debug.printf("ladder %s: %f ms/sample\n", nl != 0 ? "nonlinear" : "linear", nsPerSample);
        }
    }
}
