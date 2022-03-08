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
        int midi_note;
        boolean keydown;
        boolean sustained;
        boolean live;
        Note dx7_note;
//        int channel;
    }

    private BlockingDeque<Integer> deque;
    private long timestump;

    private static final int max_active_notes = 16;
    private ActiveNote[] active_note_ = new ActiveNote[max_active_notes];
    private int current_note_;

    private byte[] patch_data_ = new byte[156];

    // The original DX7 had one single LFO. Later units had an LFO per note.
    private Lfo lfo_ = new Lfo();

    // in MIDI units (0x4000 is neutral)
    private Note.Controllers controllers_;

    private ResoFilter filter_ = new ResoFilter();
    private int[] filter_control_ = new int[3];
    private boolean sustain_;

    // Extra buffering for when GetSamples wants a buffer not a multiple of N
    private int[] extra_buf_ = new int[Note.N];
    private int extra_buf_size_;

    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public static void init(double sample_rate) {
        Freqlut.init(sample_rate);
        Lfo.init(sample_rate);
        PitchEnv.init(sample_rate);
    }

    public void close() {
        executor.shutdown();
    }

    public SynthUnit(BlockingDeque<Integer> deque) {
        for (int note = 0; note < max_active_notes; ++note) {
            active_note_[note] = new ActiveNote();
        }
        for (int i = 0; i < patch_data_.length; i++) {
            byte[] unpacked_patch_ = new byte[156];
            Patch.unpackPatch(epiano2, unpacked_patch_);
            System.arraycopy(unpacked_patch_, 0, patch_data_, 0, epiano2.length);
        }
        current_note_ = 0;
        filter_control_[0] = 258847126;
        filter_control_[1] = 0;
        filter_control_[2] = 0;
        controllers_ = new Note.Controllers(0x2000);
        sustain_ = false;
        extra_buf_size_ = 0;

        this.deque = deque;
        timestump = System.currentTimeMillis();

Debug.println("period: " + (int) (1000.0 * Note.N / 44100.0));
        executor.scheduleAtFixedRate(this::process, 1000, (int) (1000.0 * Note.N / 44100.0), TimeUnit.MILLISECONDS);
    }

    private int allocateNote() {
        int note = current_note_;
        for (int i = 0; i < max_active_notes; i++) {
            if (!active_note_[note].keydown) {
                current_note_ = (note + 1) % max_active_notes;
                return note;
            }
            note = (note + 1) % max_active_notes;
        }
Debug.println("allocateNote: max");
        return -1;
    }

    public void programChange(int p, byte[] patch, int ofs) {
        p = Math.min(p, 31);
        System.arraycopy(patch, ofs, patch_data_, 0, patch_data_.length);
        lfo_.reset(patch_data_, 137);

        byte[] name = new byte[10];
        System.arraycopy(patch_data_, 145, name, 0, 10);
        Debug.println("Loaded patch " + p + ": " + new String(name, 0, 10));
    }

    public void noteOff(int noteNumber) {
//Debug.println("note off: " + noteNumber);
        for (int note = 0; note < max_active_notes; ++note) {
            if (active_note_[note].midi_note == noteNumber && active_note_[note].keydown) {
                if (sustain_) {
                    active_note_[note].sustained = true;
                } else {
                    active_note_[note].dx7_note.keyup();
                }
                active_note_[note].keydown = false;
            }
        }
    }

    public void noteOn(int noteNumber, int velocity) {
        if (velocity == 0) {
            noteOff(noteNumber);
            return;
        }
//Debug.println("note on: " + noteNumber + ", " + velocity);
        int note_ix = allocateNote();
        if (note_ix >= 0) {
            lfo_.keydown(); // TODO: should only do this if # keys down was 0
            active_note_[note_ix].midi_note = noteNumber;
            active_note_[note_ix].keydown = true;
            active_note_[note_ix].sustained = sustain_;
            active_note_[note_ix].live = true;
            active_note_[note_ix].dx7_note = new Note(patch_data_, noteNumber, velocity);
        }
    }

    public void controlChange(int controller, int value) {
        // TODO: move more logic into setController
        if (controller == 1) {
            filter_control_[0] = 142365917 + value * 917175;
        } else if (controller == 2) {
            filter_control_[1] = value * 528416;
        } else if (controller == 3) {
            filter_control_[2] = value * 528416;
        } else if (controller == 64) {
            // damper pedal hold 1
            sustain_ = value != 0;
            if (!sustain_) {
                for (int note = 0; note < max_active_notes; note++) {
                    if (active_note_[note].sustained && !active_note_[note].keydown) {
                        active_note_[note].dx7_note.keyup();
                        active_note_[note].sustained = false;
                    }
                }
            }
        }
        controllers_.values_[controller] = value;
//Debug.println("control change: " + controller + ", " + value);
    }

    public void pitchBend(int data1, int data2) {
        controlChange(Note.kControllerPitch, data1 | (data2 << 7));
//Debug.println("pitch bend: " + data1 + ", " + data2);
    }

    public void sysex(byte[] b) {
    }

    public void getSamples(int n_samples, int[] buffer) {
        int i;
        for (i = 0; i < n_samples && i < extra_buf_size_; i++) {
            buffer[i] = extra_buf_[i];
        }
        if (extra_buf_size_ > n_samples) {
            for (int j = 0; j < extra_buf_size_ - n_samples; j++) {
                extra_buf_[j] = extra_buf_[j + n_samples];
            }
            extra_buf_size_ -= n_samples;
            return;
        }

        for (; i < n_samples; i += Note.N) {
            int[] audiobuf = new int[Note.N];
            int[] audiobuf2 = new int[Note.N];
            int lfovalue = lfo_.getsample();
            int lfodelay = lfo_.getdelay();
            for (int note = 0; note < max_active_notes; ++note) {
                if (active_note_[note].live && active_note_[note].dx7_note != null) {
                    active_note_[note].dx7_note.compute(audiobuf, lfovalue, lfodelay, controllers_);
//                    active_note_[note].dx7_note.compute(audiobuf, 0, 0, controllers_);
                }
            }
            final int[][] bufs = { audiobuf };
            int[][] bufs2 = { audiobuf2 };
            filter_.process(bufs, filter_control_, filter_control_, bufs2);
            int jmax = n_samples - i;
            for (int j = 0; j < Note.N; ++j) {
                int val = audiobuf2[j] >> 4;
//                int val = audiobuf[j] >> 4;
                int clip_val = val < -(1 << 24) ? 0x8000 : val >= (1 << 24) ? 0x7fff : val >> 9;
                // TODO: maybe some dithering?
                if (j < jmax) {
                    buffer[i + j] = clip_val;
                } else {
                    extra_buf_[j - jmax] = clip_val;
                }
            }
        }
        extra_buf_size_ = i - n_samples;
//Debug.println("extra: " + (i - n_samples));
    }

    public void process() {
        int nsec = (int) (System.currentTimeMillis() - timestump);
        timestump = System.currentTimeMillis();
        int n_samples = (int) (44100 * nsec / 1000.0);
        int i;
        for (i = 0; i < n_samples && i < extra_buf_size_; i++) {
            deque.add(extra_buf_[i]);
        }
        if (extra_buf_size_ > n_samples) {
            for (int j = 0; j < extra_buf_size_ - n_samples; j++) {
                extra_buf_[j] = extra_buf_[j + n_samples];
            }
            extra_buf_size_ -= n_samples;
            return;
        }

        for (; i < n_samples; i += Note.N) {
            int[] audiobuf = new int[Note.N];
//            int[] audiobuf2 = new int[Note.N];
//            int lfovalue = lfo_.getsample();
//            int lfodelay = lfo_.getdelay();
            for (int note = 0; note < max_active_notes; ++note) {
                if (active_note_[note].live && active_note_[note].dx7_note != null) {
//                    active_note_[note].dx7_note.compute(audiobuf, lfovalue, lfodelay, controllers_);
                    active_note_[note].dx7_note.compute(audiobuf, 0, 0, controllers_);
                }
            }
//            final int[][] bufs = { audiobuf };
//            int[][] bufs2 = { audiobuf2 };
//            filter_.process(bufs, filter_control_, filter_control_, bufs2);
            int jmax = n_samples - i;
            for (int j = 0; j < Note.N; ++j) {
//                int val = audiobuf2[j] >> 4;
                int val = audiobuf[j] >> 4;
                int clip_val = val < -(1 << 24) ? 0x8000 : val >= (1 << 24) ? 0x7fff : val >> 9;
                // TODO: maybe some dithering?
                if (j < jmax) {
                    deque.add(clip_val);
                } else {
                    extra_buf_[j - jmax] = clip_val;
                }
            }
        }
        extra_buf_size_ = i - n_samples;
//Debug.println("deque: " + deque.size());
    }
}
