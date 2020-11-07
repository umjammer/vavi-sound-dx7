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

import vavi.util.Debug;


/**
 * @see "https://github.com/asb2m10/dexed/blob/master/Documentation/sysex-format.txt"
 */
class Patch {

    public static void unpackPatch(final byte[] bulk, byte[] patch) {
        for (int op = 0; op < 6; op++) {
            // eg rate and level, brk pt, depth, scaling
            System.arraycopy(bulk, op * 17, patch, op * 21, 11);
            byte leftrightcurves = bulk[op * 17 + 11];
            patch[op * 21 + 11] = (byte) (leftrightcurves & 3);
            patch[op * 21 + 12] = (byte) ((leftrightcurves >> 2) & 3);
            byte detune_rs = bulk[op * 17 + 12];
            patch[op * 21 + 13] = (byte) (detune_rs & 7);
            patch[op * 21 + 20] = (byte) (detune_rs >> 3);
            byte kvs_ams = bulk[op * 17 + 13];
            patch[op * 21 + 14] = (byte) (kvs_ams & 3);
            patch[op * 21 + 15] = (byte) (kvs_ams >> 2);
            patch[op * 21 + 16] = bulk[op * 17 + 14]; // output level
            byte fcoarse_mode = bulk[op * 17 + 15];
            patch[op * 21 + 17] = (byte) (fcoarse_mode & 1);
            patch[op * 21 + 18] = (byte) (fcoarse_mode >> 1);
            patch[op * 21 + 19] = bulk[op * 17 + 16]; // fine freq
        }
        System.arraycopy(bulk, 102, patch, 126, 9); // pitch env, algo
        byte oks_fb = bulk[111];
        patch[135] = (byte) (oks_fb & 7);
        patch[136] = (byte) (oks_fb >> 3);
        System.arraycopy(bulk, 112, patch, 137, 4); // lfo
        byte lpms_lfw_lks = bulk[116];
        patch[141] = (byte) (lpms_lfw_lks & 1);
        patch[142] = (byte) ((lpms_lfw_lks >> 1) & 7);
        patch[143] = (byte) (lpms_lfw_lks >> 4);
        System.arraycopy(bulk, 117, patch, 144, 11); // transpose, name
        patch[155] = 0x3f; // operator on/off

        // Confirm the parameters are within range
        checkPatch(patch);
    }

    private static int clamped = 0; // not thread safe

    private static int file_clamped = 0; // not thread safe

    private static byte clamp(byte byte_, int pos, byte max) {
        if (byte_ > max || byte_ < 0) {
            clamped++;
            Debug.printf("file %d clamped %d pos %d was %d is %d\n", file_clamped, clamped, pos, byte_, max);
            return max;
        }
        return byte_;
    }

    // Helpful, from
    // http://homepages.abdn.ac.uk/d.j.benson/dx7/sysex-format.txt
    // Note, 1% of my downloaded voices go well outside these ranges.
    // a TODO is check what happens when we slightly go outside
    private static final byte[] max = {
        99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, // osc6
        3, 3, 7, 3, 7, 99, 1, 31, 99, 14, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, // osc5
        3, 3, 7, 3, 7, 99, 1, 31, 99, 14, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, // osc4
        3, 3, 7, 3, 7, 99, 1, 31, 99, 14, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, // osc3
        3, 3, 7, 3, 7, 99, 1, 31, 99, 14, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, // osc2
        3, 3, 7, 3, 7, 99, 1, 31, 99, 14, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, // osc1
        3, 3, 7, 3, 7, 99, 1, 31, 99, 14,

        99, 99, 99, 99, 99, 99, 99, 99, // pitch eg rate & level
        31, 7, 1, 99, 99, 99, 99, 1, 5, 7, 48, // algorithm etc
        126, 126, 126, 126, 126, 126, 126, 126, 126, 126 // name
    };

    private static void checkPatch(byte[] patch) {
        for (int i = 0; i < 155; i++) {
            patch[i] = clamp(patch[i], i, max[i]);
        }
        if (clamped != 0)
            file_clamped++;
        clamped = 0;
    }
}
