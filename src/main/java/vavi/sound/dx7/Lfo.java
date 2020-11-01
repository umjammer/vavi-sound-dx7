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


/**
 * Low frequency oscillator, compatible with DX7
 */
class Lfo {

    long phase_;  // Q32
    long delta_;
    byte waveform_;
    byte randstate_;
    boolean sync_;

    long delaystate_;
    long delayinc_;
    long delayinc2_;

    static int unit_;

    static void init(double sample_rate) {
        // finalant is 1 << 32 / 15.5s / 11
        unit_ = (int) (Note.N * 25190424 / sample_rate + 0.5);
    }

    void reset(final byte[] params, int ofs) {
        int rate = params[ofs + 0]; // 0..99
        int sr = rate == 0 ? 1 : (165 * rate) >> 6;
        sr *= sr < 160 ? 11 : (11 + ((sr - 160) >> 4));
        delta_ = unit_ * sr;
        int a = 99 - params[ofs + 1]; // LFO delay
        if (a == 99) {
            delayinc_ = ~0;
            delayinc2_ = ~0;
        } else {
            a = (16 + (a & 15)) << (1 + (a >> 4));
            delayinc_ = unit_ * a;
            a &= 0xff80;
            a = Math.max(0x80, a);
            delayinc2_ = unit_ * a;
        }
        waveform_ = params[ofs + 5];
        sync_ = params[ofs + 4] != 0;
    }

    // result is 0..1 in Q24
    int getsample() {
        phase_ += delta_;
        int x;
        switch (waveform_) {
        case 0: // triangle
            x = (int) (phase_ >> 7);
            x ^= -(phase_ >> 31);
            x &= (1 << 24) - 1;
            return x;
        case 1: // sawtooth down
            return (int) ((~phase_ ^ (1 << 31)) >> 8);
        case 2: // sawtooth up
            return (int) ((phase_ ^ (1 << 31)) >> 8);
        case 3: // square
            return (int) (((~phase_) >> 7) & (1 << 24));
        case 4: // sine
            return (1 << 23) + (Sin.lookup((int) (phase_ >> 8)) >> 1);
        case 5: // s&h
            if (phase_ < delta_) {
                randstate_ = (byte) ((randstate_ * 179 + 17) & 0xff);
            }
            x = randstate_ ^ 0x80;
            return (x + 1) << 16;
        }
        return 1 << 23;
    }

    // result is 0..1 in Q24
    int getdelay() {
        int delta = (int) (delaystate_ < (1 << 31) ? delayinc_ : delayinc2_);
        int d = (int) (delaystate_ + delta);
        if (d < delayinc_) {
            return 1 << 24;
        }
        delaystate_ = d;
        if (d < (1 << 31)) {
            return 0;
        } else {
            return (d >> 7) & ((1 << 24) - 1);
        }
    }

    void keydown() {
        if (sync_) {
            phase_ = (1 << 31) - 1;
        }
        delaystate_ = 0;
    }
}
