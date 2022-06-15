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


import java.util.HashMap;
import java.util.Map;


/**
 * Low frequency oscillator, compatible with DX7
 */
public class Lfo {

    private long phase; // Q32
    private long delta;
    private byte waveform;
    private byte randState;
    private boolean sync;

    private long delayState;
    private long delayInc;
    private long delayInc2;

    private static long unit;

    Lfo(double sampleRate) {
        // constant is 1 << 32 / 15.5s / 11
        unit = (int) (Note.N * 25190424 / sampleRate + 0.5);
    }

    public void reset(final byte[] params, int ofs) {
        int rate = params[ofs + 0]; // 0..99
        int sr = rate == 0 ? 1 : (165 * rate) >> 6;
        sr *= sr < 160 ? 11 : (11 + ((sr - 160) >> 4));
        delta = unit * sr;
        int a = 99 - params[ofs + 1]; // LFO delay
        if (a == 99) {
            delayInc = ~0;
            delayInc2 = ~0;
        } else {
            a = (16 + (a & 15)) << (1 + (a >> 4));
            delayInc = unit * a;
            a &= 0xff80;
            a = Math.max(0x80, a);
            delayInc2 = unit * a;
        }
        waveform = params[ofs + 5];
        sync = params[ofs + 4] != 0;
    }

    // result is 0..1 in Q24
    public int getSample() {
        phase += delta;
        int x;
        switch (waveform) {
        case 0: // triangle
            x = (int) (phase >> 7);
            x ^= -(phase >> 31);
            x &= (1 << 24) - 1;
            return x;
        case 1: // sawtooth down
            return (int) ((~phase ^ (1 << 31)) >> 8);
        case 2: // sawtooth up
            return (int) ((phase ^ (1 << 31)) >> 8);
        case 3: // square
            return (int) (((~phase) >> 7) & (1 << 24));
        case 4: // sine
            return (1 << 23) + (Sin.lookup((int) (phase >> 8)) >> 1);
        case 5: // s&h
            if (phase < delta) {
                randState = (byte) ((randState * 179 + 17) & 0xff);
            }
            x = randState ^ 0x80;
            return (x + 1) << 16;
        }
        return 1 << 23;
    }

    // result is 0..1 in Q24
    public int getDelay() {
        long delta = (delayState < (1L << 31) ? delayInc : delayInc2);
        long d = delayState + delta;
        if (d < delayInc) {
            return 1 << 24;
        }
        delayState = d;
        if (d < (1L << 31)) {
            return 0;
        } else {
            return (int) ((d >> 7) & ((1 << 24) - 1));
        }
    }

    public void keyDown() {
        if (sync) {
            phase = (1L << 31) - 1;
        }
        delayState = 0;
    }
}
