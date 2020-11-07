/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.dx7;

import java.util.ArrayList;
import java.util.List;

import vavi.sound.dx7.Freqlut;
import vavi.sound.dx7.Lfo;
import vavi.sound.dx7.Note;
import vavi.sound.dx7.PitchEnv;
import vavi.sound.dx7.ResoFilter;
import vavi.util.Debug;


/**
 * Dx7.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/11/07 umjammer initial version <br>
 */
class Dx7 {
    static final int BUFFER_SIZE = Note.N;

    private static class ActiveNote {
        boolean keydown;

        boolean sustained;

        Note note;
    }

    // The original DX7 had one single LFO. Later units had an LFO per note.
    private Lfo lfo = new Lfo();

    // in MIDI units (0x4000 is neutral)
    private Note.Controllers controllers;

    private ResoFilter filter = new ResoFilter();

    private int[] filterControl = new int[3];

    private boolean sustain;

    Dx7() {
        filterControl[0] = 258847126;
        filterControl[1] = 0;
        filterControl[2] = 0;
        sustain = false;
        controllers = new Note.Controllers(0x2000);
    }

    private static float sampleRate = -1;

    static void setSampleRate(float sampleRate) {
        if (Dx7.sampleRate != sampleRate) {
            Debug.println("sampleRate: " + sampleRate);
            Freqlut.init(sampleRate);
            Lfo.init(sampleRate);
            PitchEnv.init(sampleRate);
            Dx7.sampleRate = sampleRate;
        }
    }

    private static List<Dx7.ActiveNote> activeNotes = new ArrayList<>();

    private Dx7.ActiveNote activeNote;

    private int[] audioBuf = new int[BUFFER_SIZE];

    private int[] audioBuf2 = new int[BUFFER_SIZE];

    void write(int offset, int len, int i, float[] buffer, float[] extraBuf) {
        int lfoValue = lfo.getsample();
        int lfoDelay = lfo.getdelay();
        activeNote.note.compute(audioBuf, lfoValue, lfoDelay, controllers);
//        activeNote.dx7_note.compute(audiobuf, 0, 0, controllers_);
        final int[][] bufs = { audioBuf };
        int[][] bufs2 = { audioBuf2 };
        filter.process(bufs, filterControl, filterControl, bufs2);
        int jmax = len - i;
        for (int j = 0; j < Note.N; j++) {
            int val = audioBuf2[j] >> 4;
//            int val = audiobuf[j] >> 4;
            int clip_val = val < -(1 << 24) ? 0x8000 : val >= (1 << 24) ? 0x7fff : val >> 9;
            // TODO: maybe some dithering?
            float f;
            int x = clip_val;
            f = ((float) x) / (float) 32768;
            if (f > 1)
                f = 1;
            if (f < -1)
                f = -1;
            if (j < jmax) {
                buffer[offset + i + j] = f;
            } else {
                extraBuf[j - jmax] = f;
            }
        }
    }

    void noteOn(byte[] patch, int noteNumber, int velocity) {
        lfo.keydown();
        activeNote = new Dx7.ActiveNote();
        activeNote.keydown = true;
        activeNote.sustained = sustain;
        activeNote.note = new Note(patch, noteNumber, velocity);
        activeNotes.add(activeNote);
    }

    void noteOff() {
        if (activeNote.keydown) {
            if (sustain) {
                activeNote.sustained = true;
            } else {
                activeNote.note.keyup();
                activeNotes.remove(activeNote);
            }
            activeNote.keydown = false;
        }
    }

    public void programChange(int p, byte[] patch, int ofs) {
        // TODO location
        byte[] b = Dx7Soundbank.getDireectBuffer(p);
        System.arraycopy(patch, ofs, b, 0, b.length);
        lfo.reset(b, 137);

Debug.println("Loaded patch " + p + ": " + new String(b, 145, 10));
    }

    public void sysex(byte[] b) {
        // TODO impl
    }

    public void controlChange(int controller, int value) {
        if (controller == 1) {
            filterControl[0] = 142365917 + value * 917175;
        } else if (controller == 2) {
            filterControl[1] = value * 528416;
        } else if (controller == 3) {
            filterControl[2] = value * 528416;
        } else if (controller == 64) {
            sustain = value != 0;
            if (!sustain) {
                for (ActiveNote activeNote : activeNotes) {
                    if (activeNote.sustained && !activeNote.keydown) {
                        activeNote.note.keyup();
                        activeNote.sustained = false;
                        activeNotes.remove(activeNote);
                    }
                }
            }
        }
        controllers.values_[controller] = value;
Debug.println("control change: " + controller + ", " + value);
    }
}
/* */
