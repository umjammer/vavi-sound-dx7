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

    private final int[] rates = new int[4];
    private final int[] levels = new int[4];
    private final int outLevel;
    private final int rateScaling;
    // Level is stored so that 2^24 is one doubling, ie 16 more bits than
    // the DX7 itself (fraction is stored in level rather than separate
    // counter)
    private int level;
    private int targetLevel;
    private boolean rising;
    private int ix;
    private int inc;

    private boolean down;

    Env(int[] r, int[] l, int ol, int rateScaling) {
        for (int i = 0; i < 4; i++) {
            rates[i] = r[i];
            levels[i] = l[i];
        }
        outLevel = ol;
        this.rateScaling = rateScaling;
        level = 0;
        down = true;
        advance(0);
    }

    int getSample() {
        if (ix < 3 || (ix < 4) && !down) {
            if (rising) {
                final int jumpTarget = 1716;
                if (level < (jumpTarget << 16)) {
                    level = jumpTarget << 16;
                }
                level += (((17 << 24) - level) >> 24) * inc;
                // TODO: should probably be more accurate when inc is large
                if (level >= targetLevel) {
                    level = targetLevel;
                    advance(ix + 1);
                }
            } else { // !rising
                level -= inc;
                if (level <= targetLevel) {
                    level = targetLevel;
                    advance(ix + 1);
                }
            }
        }
        // TODO: this would be a good place to set level to 0 when under
        // threshold
        return level;
    }

    void keyDown(boolean d) {
        if (down != d) {
            down = d;
            advance(d ? 0 : 3);
        }
    }

    void setParam(int param, int value) {
        if (param < 4) {
            rates[param] = value;
        } else if (param < 8) {
            levels[param - 4] = value;
        }
        // Unknown parameter, ignore for now
    }

    private static final int[] levelLut = {
        0, 5, 9, 13, 17, 20, 23, 25, 27, 29, 31, 33, 35, 37, 39, 41, 42, 43, 45, 46
    };

    public static int scaleOutLevel(int outLevel) {
        return outLevel >= 20 ? 28 + outLevel : levelLut[outLevel];
    }

    void advance(int newIx) {
        ix = newIx;
        if (ix < 4) {
            int newLevel = levels[ix];
            int actualLevel = scaleOutLevel(newLevel) >> 1;

            actualLevel = (actualLevel << 6) + outLevel - 4256;
            actualLevel = Math.max(actualLevel, 16);
            // level here is same as Java impl
            targetLevel = actualLevel << 16;
            rising = (targetLevel > level);

            // rate

            int qRate = (rates[ix] * 41) >> 6;

            qRate += rateScaling;
            qRate = Math.min(qRate, 63);
            inc = (4 + (qRate & 3)) << (2 + Note.LG_N + (qRate >> 2));
        }
    }
}
