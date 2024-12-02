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


public class PitchEnv {

    private final int[] rates = new int[4];
    private final int[] levels = new int[4];
    private int level;
    private int targetLevel;
    private boolean rising;
    private int ix;
    private int inc;

    private boolean down;

    private final int unit;

    PitchEnv(double sampleRate) {
        unit = (int) (Note.N * (1 << 24) / (21.3 * sampleRate) + 0.5);
    }

    private static final int[] rateTab = {
        1, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 16, 16, 17, 18, 18, 19, 20,
        21, 22, 23, 24, 25, 26, 27, 28, 30, 31, 33, 34, 36, 37, 38, 39, 41, 42, 44, 46, 47, 49, 51, 53, 54, 56, 58, 60, 62, 64,
        66, 68, 70, 72, 74, 76, 79, 82, 85, 88, 91, 94, 98, 102, 106, 110, 115, 120, 125, 130, 135, 141, 147, 153, 159, 165,
        171, 178, 185, 193, 202, 211, 232, 243, 254, 255
    };

    private static final int[] pitchTab = {
        -128, -116, -104, -95, -85, -76, -68, -61, -56, -52, -49, -46, -43, -41, -39, -37, -35, -33, -32, -31, -30, -29, -28,
        -27, -26, -25, -24, -23, -22, -21, -20, -19, -18, -17, -16, -15, -14, -13, -12, -11, -10, -9, -8, -7, -6, -5, -4, -3,
        -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
        30, 31, 32, 33, 34, 35, 38, 40, 43, 46, 49, 53, 58, 65, 73, 82, 92, 103, 115, 127
    };

    public void set(int[] r, int[] l) {
        for (int i = 0; i < 4; i++) {
            rates[i] = r[i];
            levels[i] = l[i];
        }
        level = pitchTab[l[3]] << 19;
        down = true;
        advance(0);
    }

    public int getSample() {
        if (ix < 3 || (ix < 4) && !down) {
            if (rising) {
                level += inc;
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
        return level;
    }

    public void keyDown(boolean d) {
        if (down != d) {
            down = d;
            advance(d ? 0 : 3);
        }
    }

    public void advance(int newIx) {
        ix = newIx;
        if (ix < 4) {
            int newLevel = levels[ix];
            targetLevel = pitchTab[newLevel] << 19;
            rising = (targetLevel > level);

            inc = rateTab[rates[ix]] * unit;
        }
    }
}
