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

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import vavi.util.Debug;


public class SynthUnit {

    private static final byte[] epiano2 = {
        95, 29, 20, 50, 99, 95, 0, 0, 41, 0, 19, 0, 115, 24, 79, 2, 0, 95, 20, 20, 50, 99, 95, 0, 0, 0, 0, 0, 0, 3, 0, 99, 2, 0,
        95, 29, 20, 50, 99, 95, 0, 0, 0, 0, 0, 0, 59, 24, 89, 2, 0, 95, 20, 20, 50, 99, 95, 0, 0, 0, 0, 0, 0, 59, 8, 99, 2, 0,
        95, 50, 35, 78, 99, 75, 0, 0, 0, 0, 0, 0, 59, 28, 58, 28, 0, 96, 25, 25, 67, 99, 75, 0, 0, 0, 0, 0, 0, 83, 8, 99, 2, 0,

        94, 67, 95, 60, 50, 50, 50, 50, 4, 6, 34, 33, 0, 0, 56, 24, 69, 46, 80, 73, 65, 78, 79, 32, 49, 32
    };

    private static class ActiveNote {
        int midiNote;
        boolean keyDown;
        boolean sustained;
        boolean live;
        Note dx7Note;
//        int channel;
    }

    private BlockingDeque<Integer> deque;
    private long timestump;

    private static final int MAX_ACTIVE_NOTES = 16;
    private ActiveNote[] activeNote = new ActiveNote[MAX_ACTIVE_NOTES];
    private int currentNote;

    private byte[] patchData = new byte[156];

    private Context context;

    // in MIDI units (0x4000 is neutral)
    private Note.Controllers controllers;

    private ResoFilter filter;
    private int[] filterControl = new int[3];
    private boolean sustain;

    // Extra buffering for when GetSamples wants a buffer not a multiple of N
    private int[] extraBuf = new int[Note.N];
    private int extraBufSize;

    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public void close() {
        executor.shutdown();
    }

    public SynthUnit(float sampleRate, BlockingDeque<Integer> deque) {
        for (int note = 0; note < MAX_ACTIVE_NOTES; ++note) {
            activeNote[note] = new ActiveNote();
        }
        for (int i = 0; i < patchData.length; i++) {
            byte[] unpacked_patch_ = new byte[156];
            Patch.unpackPatch(epiano2, unpacked_patch_);
            System.arraycopy(unpacked_patch_, 0, patchData, 0, epiano2.length);
        }
        currentNote = 0;
        filterControl[0] = 258847126;
        filterControl[1] = 0;
        filterControl[2] = 0;
        controllers = new Note.Controllers(0x2000);
        sustain = false;
        extraBufSize = 0;

        this.deque = deque;
        timestump = System.currentTimeMillis();

        context = Context.getInstance(sampleRate);
        filter = new ResoFilter(context);

        Debug.println("period: " + (int) (1000.0 * Note.N / 44100.0));
        executor.scheduleAtFixedRate(this::process, 1000, (int) (1000.0 * Note.N / 44100.0), TimeUnit.MILLISECONDS);
    }

    private int allocateNote() {
        int note = currentNote;
        for (int i = 0; i < MAX_ACTIVE_NOTES; i++) {
            if (!activeNote[note].keyDown) {
                currentNote = (note + 1) % MAX_ACTIVE_NOTES;
                return note;
            }
            note = (note + 1) % MAX_ACTIVE_NOTES;
        }
Debug.println("allocateNote: max");
        return -1;
    }

    public void programChange(int p, byte[] patch, int ofs) {
        p = Math.min(p, 31);
        System.arraycopy(patch, ofs, patchData, 0, patchData.length);
        context.lfo.reset(patchData, 137);

        byte[] name = new byte[10];
        System.arraycopy(patchData, 145, name, 0, 10);
        Debug.println("Loaded patch " + p + ": " + new String(name, 0, 10));
    }

    public void noteOff(int noteNumber) {
//Debug.println("note off: " + noteNumber);
        for (int note = 0; note < MAX_ACTIVE_NOTES; ++note) {
            if (activeNote[note].midiNote == noteNumber && activeNote[note].keyDown) {
                if (sustain) {
                    activeNote[note].sustained = true;
                } else {
                    activeNote[note].dx7Note.keyUp();
                }
                activeNote[note].keyDown = false;
            }
        }
    }

    public void noteOn(int noteNumber, int velocity) {
        if (velocity == 0) {
            noteOff(noteNumber);
            return;
        }
//Debug.println("note on: " + noteNumber + ", " + velocity);
        int noteIx = allocateNote();
        if (noteIx >= 0) {
            context.lfo.keyDown(); // TODO: should only do this if # keys down was 0
            activeNote[noteIx].midiNote = noteNumber;
            activeNote[noteIx].keyDown = true;
            activeNote[noteIx].sustained = sustain;
            activeNote[noteIx].live = true;
            activeNote[noteIx].dx7Note = new Note(context, patchData, noteNumber, velocity);
        }
    }

    public void controlChange(int controller, int value) {
        // TODO: move more logic into setController
        if (controller == 1) {
            filterControl[0] = 142365917 + value * 917175;
        } else if (controller == 2) {
            filterControl[1] = value * 528416;
        } else if (controller == 3) {
            filterControl[2] = value * 528416;
        } else if (controller == 64) {
            // damper pedal hold 1
            sustain = value != 0;
            if (!sustain) {
                for (int note = 0; note < MAX_ACTIVE_NOTES; note++) {
                    if (activeNote[note].sustained && !activeNote[note].keyDown) {
                        activeNote[note].dx7Note.keyUp();
                        activeNote[note].sustained = false;
                    }
                }
            }
        }
        controllers.values[controller] = value;
//Debug.println("control change: " + controller + ", " + value);
    }

    public void pitchBend(int data1, int data2) {
        controlChange(Note.kControllerPitch, data1 | (data2 << 7));
//Debug.println("pitch bend: " + data1 + ", " + data2);
    }

    public void sysex(byte[] b) {
    }

    public void getSamples(int nSamples, int[] buffer) {
        int i;
        for (i = 0; i < nSamples && i < extraBufSize; i++) {
            buffer[i] = extraBuf[i];
        }
        if (extraBufSize > nSamples) {
            System.arraycopy(extraBuf, nSamples, extraBuf, 0, extraBufSize - nSamples);
            extraBufSize -= nSamples;
            return;
        }

        for (; i < nSamples; i += Note.N) {
            int[] audioBuf = new int[Note.N];
            int[] audioBuf2 = new int[Note.N];
            int lfoValue = context.lfo.getSample();
            int lfoDelay = context.lfo.getDelay();
            for (int note = 0; note < MAX_ACTIVE_NOTES; ++note) {
                if (activeNote[note].live && activeNote[note].dx7Note != null) {
                    activeNote[note].dx7Note.compute(audioBuf, lfoValue, lfoDelay, controllers);
//                    activeNote[note].dx7Note.compute(audioBuf, 0, 0, controllers);
                }
            }
            final int[][] bufs = { audioBuf };
            int[][] bufs2 = { audioBuf2 };
            filter.process(bufs, filterControl, filterControl, bufs2);
            int jmax = nSamples - i;
            for (int j = 0; j < Note.N; ++j) {
                int val = audioBuf2[j] >> 4;
//                int val = audioBuf[j] >> 4;
                int clip_val = val < -(1 << 24) ? 0x8000 : val >= (1 << 24) ? 0x7fff : val >> 9;
                // TODO: maybe some dithering?
                if (j < jmax) {
                    buffer[i + j] = clip_val;
                } else {
                    extraBuf[j - jmax] = clip_val;
                }
            }
        }
        extraBufSize = i - nSamples;
//Debug.println("extra: " + (i - nSamples));
    }

    public void process() {
        int nsec = (int) (System.currentTimeMillis() - timestump);
        timestump = System.currentTimeMillis();
        int nSamples = (int) (44100 * nsec / 1000.0);
        int i;
        for (i = 0; i < nSamples && i < extraBufSize; i++) {
            deque.add(extraBuf[i]);
        }
        if (extraBufSize > nSamples) {
            System.arraycopy(extraBuf, nSamples, extraBuf, 0, extraBufSize - nSamples);
            extraBufSize -= nSamples;
            return;
        }

        for (; i < nSamples; i += Note.N) {
            int[] audioBuf = new int[Note.N];
//            int[] audioBuf2 = new int[Note.N];
//            int lfoValue = lfo.getSample();
//            int lfoDelay = lfo.getSelay();
            for (int note = 0; note < MAX_ACTIVE_NOTES; ++note) {
                if (activeNote[note].live && activeNote[note].dx7Note != null) {
//                    activeNnote[note].dx7Note.compute(audioBuf, lfoValue, lfoDelay, controllers);
                    activeNote[note].dx7Note.compute(audioBuf, 0, 0, controllers);
                }
            }
//            final int[][] bufs = { audioBuf };
//            int[][] bufs2 = { audiobuf2 };
//            filter.process(bufs, filterControl, filterControl, bufs2);
            int jmax = nSamples - i;
            for (int j = 0; j < Note.N; ++j) {
//                int val = audiobuf2[j] >> 4;
                int val = audioBuf[j] >> 4;
                int clipVal = val < -(1 << 24) ? 0x8000 : val >= (1 << 24) ? 0x7fff : val >> 9;
                // TODO: maybe some dithering?
                if (j < jmax) {
                    deque.add(clipVal);
                } else {
                    extraBuf[j - jmax] = clipVal;
                }
            }
        }
        extraBufSize = i - nSamples;
//Debug.println("deque: " + deque.size());
    }
}
