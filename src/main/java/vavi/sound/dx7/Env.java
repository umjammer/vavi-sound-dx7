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


class Env {

    private int[] rates_ = new int[4];
    private int[] levels_ = new int[4];
    private int outlevel_;
    private int rate_scaling_;
    // Level is stored so that 2^24 is one doubling, ie 16 more bits than
    // the DX7 itself (fraction is stored in level rather than separate
    // counter)
    private int level_;
    private int targetlevel_;
    private boolean rising_;
    private int ix_;
    private int inc_;

    private boolean down_;

    Env(final int[] r, final int[] l, int ol, int rate_scaling) {
        for (int i = 0; i < 4; i++) {
            rates_[i] = r[i];
            levels_[i] = l[i];
        }
        outlevel_ = ol;
        rate_scaling_ = rate_scaling;
        level_ = 0;
        down_ = true;
        advance(0);
    }

    int getsample() {
        if (ix_ < 3 || (ix_ < 4) && !down_) {
            if (rising_) {
                final int jumptarget = 1716;
                if (level_ < (jumptarget << 16)) {
                    level_ = jumptarget << 16;
                }
                level_ += (((17 << 24) - level_) >> 24) * inc_;
                // TODO: should probably be more accurate when inc is large
                if (level_ >= targetlevel_) {
                    level_ = targetlevel_;
                    advance(ix_ + 1);
                }
            } else { // !rising
                level_ -= inc_;
                if (level_ <= targetlevel_) {
                    level_ = targetlevel_;
                    advance(ix_ + 1);
                }
            }
        }
        // TODO: this would be a good place to set level to 0 when under
        // threshold
        return level_;
    }

    void keydown(boolean d) {
        if (down_ != d) {
            down_ = d;
            advance(d ? 0 : 3);
        }
    }

    void setparam(int param, int value) {
        if (param < 4) {
            rates_[param] = value;
        } else if (param < 8) {
            levels_[param - 4] = value;
        }
        // Unknown parameter, ignore for now
    }

    private static final int levellut[] = {
        0, 5, 9, 13, 17, 20, 23, 25, 27, 29, 31, 33, 35, 37, 39, 41, 42, 43, 45, 46
    };

    public static int scaleoutlevel(int outlevel) {
        return outlevel >= 20 ? 28 + outlevel : levellut[outlevel];
    }

    void advance(int newix) {
        ix_ = newix;
        if (ix_ < 4) {
            int newlevel = levels_[ix_];
            int actuallevel = scaleoutlevel(newlevel) >> 1;

            actuallevel = (actuallevel << 6) + outlevel_ - 4256;
            actuallevel = actuallevel < 16 ? 16 : actuallevel;
            // level here is same as Java impl
            targetlevel_ = actuallevel << 16;
            rising_ = (targetlevel_ > level_);

            // rate

            int qrate = (rates_[ix_] * 41) >> 6;

            qrate += rate_scaling_;
            qrate = Math.min(qrate, 63);
            inc_ = (4 + (qrate & 3)) << (2 + Note.LG_N + (qrate >> 2));
        }
    }
}
