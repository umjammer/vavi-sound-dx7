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


class FmOpKernel {

    static void compute(int[] output, final int[] input, int phase0, int freq, int gain1, int gain2, boolean add) {
        int dgain = (gain2 - gain1 + (Note.N >> 1)) >> Note.LG_N;
        int gain = gain1;
        int phase = phase0;
        if (add) {
            for (int i = 0; i < Note.N; i++) {
                gain += dgain;
                int y = Sin.lookup(phase + input[i]);
                output[i] += ((long) y * (long) gain) >> 24;
                phase += freq;
            }
        } else {
            for (int i = 0; i < Note.N; i++) {
                gain += dgain;
                int y = Sin.lookup(phase + input[i]);
                output[i] = (int) (((long) y * (long) gain) >> 24);
                phase += freq;
            }
        }
    }

    static void compute_pure(int[] output, int phase0, int freq, int gain1, int gain2, boolean add) {
        int dgain = (gain2 - gain1 + (Note.N >> 1)) >> Note.LG_N;
        int gain = gain1;
        int phase = phase0;
        if (add) {
            for (int i = 0; i < Note.N; i++) {
                gain += dgain;
                int y = Sin.lookup(phase);
                output[i] += ((long) y * (long) gain) >> 24;
                phase += freq;
            }
        } else {
            for (int i = 0; i < Note.N; i++) {
                gain += dgain;
                int y = Sin.lookup(phase);
                output[i] = (int) (((long) y * (long) gain) >> 24);
                phase += freq;
            }
        }
    }

    static void compute_fb(int[] output, int phase0, int freq, int gain1, int gain2, int[] fb_buf, int fb_shift, boolean add) {
        int dgain = (gain2 - gain1 + (Note.N >> 1)) >> Note.LG_N;
        int gain = gain1;
        int phase = phase0;
        int y0 = fb_buf[0];
        int y = fb_buf[1];
        if (add) {
            for (int i = 0; i < Note.N; i++) {
                gain += dgain;
                int scaled_fb = (y0 + y) >> (fb_shift + 1);
                y0 = y;
                y = Sin.lookup(phase + scaled_fb);
                y = (int) (((long) y * (long) gain) >> 24);
                output[i] += y;
                phase += freq;
            }
        } else {
            for (int i = 0; i < Note.N; i++) {
                gain += dgain;
                int scaled_fb = (y0 + y) >> (fb_shift + 1);
                y0 = y;
                y = Sin.lookup(phase + scaled_fb);
                y = (int) (((long) y * (long) gain) >> 24);
                output[i] = y;
                phase += freq;
            }
        }
        fb_buf[0] = y0;
        fb_buf[1] = y;
    }
}
