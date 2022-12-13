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


class FmCore {

    public static class FmOpParams {
        int[] gain = new int[2];
        int freq;
        int phase;
    }

    public static class FmOperatorInfo {
        int in;
        int out;
    }

    private static final int OUT_BUS_ONE = 1 << 0;
    private static final int OUT_BUS_TWO = 1 << 1;
    private static final int OUT_BUS_ADD = 1 << 2;
    private static final int IN_BUS_ONE = 1 << 4;
    private static final int IN_BUS_TWO = 1 << 5;
    private static final int FB_IN = 1 << 6;
    private static final int FB_OUT = 1 << 7;

    private static class FmAlgorithm {
        int[] ops = new int[6];
        public FmAlgorithm(Integer... args) {
            int c = 0;
            for (int i : args) {
                ops[c++] = i;
            }
        }
    }

    private static final FmAlgorithm[] algorithms = {
        new FmAlgorithm(0xc1, 0x11, 0x11, 0x14, 0x01, 0x14), // 1
        new FmAlgorithm(0x01, 0x11, 0x11, 0x14, 0xc1, 0x14), // 2
        new FmAlgorithm(0xc1, 0x11, 0x14, 0x01, 0x11, 0x14), // 3
        new FmAlgorithm(0x41, 0x11, 0x94, 0x01, 0x11, 0x14), // 4
        new FmAlgorithm(0xc1, 0x14, 0x01, 0x14, 0x01, 0x14), // 5
        new FmAlgorithm(0x41, 0x94, 0x01, 0x14, 0x01, 0x14), // 6
        new FmAlgorithm(0xc1, 0x11, 0x05, 0x14, 0x01, 0x14), // 7
        new FmAlgorithm(0x01, 0x11, 0xc5, 0x14, 0x01, 0x14), // 8
        new FmAlgorithm(0x01, 0x11, 0x05, 0x14, 0xc1, 0x14), // 9
        new FmAlgorithm(0x01, 0x05, 0x14, 0xc1, 0x11, 0x14), // 10
        new FmAlgorithm(0xc1, 0x05, 0x14, 0x01, 0x11, 0x14), // 11
        new FmAlgorithm(0x01, 0x05, 0x05, 0x14, 0xc1, 0x14), // 12
        new FmAlgorithm(0xc1, 0x05, 0x05, 0x14, 0x01, 0x14), // 13
        new FmAlgorithm(0xc1, 0x05, 0x11, 0x14, 0x01, 0x14), // 14
        new FmAlgorithm(0x01, 0x05, 0x11, 0x14, 0xc1, 0x14), // 15
        new FmAlgorithm(0xc1, 0x11, 0x02, 0x25, 0x05, 0x14), // 16
        new FmAlgorithm(0x01, 0x11, 0x02, 0x25, 0xc5, 0x14), // 17
        new FmAlgorithm(0x01, 0x11, 0x11, 0xc5, 0x05, 0x14), // 18
        new FmAlgorithm(0xc1, 0x14, 0x14, 0x01, 0x11, 0x14), // 19
        new FmAlgorithm(0x01, 0x05, 0x14, 0xc1, 0x14, 0x14), // 20
        new FmAlgorithm(0x01, 0x14, 0x14, 0xc1, 0x14, 0x14), // 21
        new FmAlgorithm(0xc1, 0x14, 0x14, 0x14, 0x01, 0x14), // 22
        new FmAlgorithm(0xc1, 0x14, 0x14, 0x01, 0x14, 0x04), // 23
        new FmAlgorithm(0xc1, 0x14, 0x14, 0x14, 0x04, 0x04), // 24
        new FmAlgorithm(0xc1, 0x14, 0x14, 0x04, 0x04, 0x04), // 25
        new FmAlgorithm(0xc1, 0x05, 0x14, 0x01, 0x14, 0x04), // 26
        new FmAlgorithm(0x01, 0x05, 0x14, 0xc1, 0x14, 0x04), // 27
        new FmAlgorithm(0x04, 0xc1, 0x11, 0x14, 0x01, 0x14), // 28
        new FmAlgorithm(0xc1, 0x14, 0x01, 0x14, 0x04, 0x04), // 29
        new FmAlgorithm(0x04, 0xc1, 0x11, 0x14, 0x04, 0x04), // 30
        new FmAlgorithm(0xc1, 0x14, 0x04, 0x04, 0x04, 0x04), // 31
        new FmAlgorithm(0xc4, 0x04, 0x04, 0x04, 0x04, 0x04), // 32
    };

    private int n_out(FmAlgorithm alg) {
        int count = 0;
        for (int i = 0; i < 6; i++) {
            if ((alg.ops[i] & 7) == OUT_BUS_ADD)
                count++;
        }
        return count;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            sb.append(i + 1).append(":");
            FmAlgorithm alg = algorithms[i];
            for (int j = 0; j < 6; j++) {
                int flags = alg.ops[j];
                sb.append(" ");
                if ((flags & FB_IN) != 0)
                    sb.append("[");
                sb.append((flags & IN_BUS_ONE) != 0 ? "1" : (flags & IN_BUS_TWO) != 0 ? "2" : "0").append("->");
                sb.append((flags & OUT_BUS_ONE) != 0 ? "1" : (flags & OUT_BUS_TWO) != 0 ? "2" : "0");
                if ((flags & OUT_BUS_ADD) != 0)
                    sb.append("+");
                // sb.append(alg.ops[j].in + "->" + alg.ops[j].out);
                if ((flags & FB_OUT) != 0)
                    sb.append("]");
            }
            sb.append(" ").append(n_out(alg));
        }
        return sb.toString();
    }

    public void compute(int[] output, FmOpParams[] params, int algorithm, int[] fbBuf, int feedbackShift) {
        final int kLevelThresh = 1120;
        FmAlgorithm alg = algorithms[algorithm];
        boolean[] hasContents = {
            true, false, false
        };
        for (int op = 0; op < 6; op++) {
            int flags = alg.ops[op];
            boolean add = (flags & OUT_BUS_ADD) != 0;
            FmOpParams param = params[op];
            int inBus = (flags >> 4) & 3;
            int outBus = flags & 3;
            int[] outPtr = (outBus == 0) ? output : output; // maybe align or not
            int gain1 = param.gain[0];
            int gain2 = param.gain[1];
            if (gain1 >= kLevelThresh || gain2 >= kLevelThresh) {
                if (!hasContents[outBus]) {
                    add = false;
                }
                if (inBus == 0 || !hasContents[inBus]) {
                    // TODO more than one op in a feedback loop
                    if ((flags & 0xc0) == 0xc0 && feedbackShift < 16) {
                        // Debug.println(op + " fb " + inBus + outBus + add);
                        FmOpKernel.computeFb(outPtr, param.phase, param.freq, gain1, gain2, fbBuf, feedbackShift, add);
                    } else {
                        // Debug.println(op + " pure " + inBus + outBus + add);
                        FmOpKernel.computePure(outPtr, param.phase, param.freq, gain1, gain2, add);
                    }
                } else {
                    // Debug.println(op + " normal " + inBus + outBus + " " + param.freq + add);
                    FmOpKernel.compute(outPtr, outPtr, param.phase, param.freq, gain1, gain2, add); // TODO 2nd outPtr
                }
                hasContents[outBus] = true;
            } else if (!add) {
                hasContents[outBus] = false;
            }
            param.phase += param.freq << Note.LG_N;
        }
    }
}
