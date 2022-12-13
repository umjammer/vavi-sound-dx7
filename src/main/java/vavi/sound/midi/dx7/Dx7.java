/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.dx7;

import java.util.ArrayList;
import java.util.List;

import vavi.sound.dx7.Context;
import vavi.sound.dx7.Note;
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
        boolean keyDown;
        // TODO should be use the value in SoftVoice?
        boolean sustained;
        Note note;
    }

    // in MIDI units (0x4000 is neutral)
    private Note.Controllers controllers;

    private ResoFilter filter;

    private int[] filterControl = new int[3];

    // TODO should be use the value in SoftVoice?
    private boolean sustain;

    Dx7() {
        filterControl[0] = 258847126;
        filterControl[1] = 0;
        filterControl[2] = 0;
        sustain = false;
        controllers = new Note.Controllers(0x2000);
    }

    private Context context = null;

    void setSampleRate(float sampleRate) {
        if (this.context == null) {
            this.context = Context.getInstance(sampleRate);
            filter = new ResoFilter(context);
        }
        if (this.context.sampleRate != sampleRate) {
Debug.println("sampleRate: " + sampleRate);
            this.context.setSampleRate(sampleRate);
        }
    }

    private static List<Dx7.ActiveNote> activeNotes = new ArrayList<>();

    private Dx7.ActiveNote activeNote;

    private int[] audioBuf = new int[BUFFER_SIZE];
    private int[] audioBuf2 = new int[BUFFER_SIZE];

    void write(int offset, int len, int i, float[] buffer, float[] extraBuf) {
        int lfoValue = context.lfo.getSample();
        int lfoDelay = context.lfo.getDelay();
        activeNote.note.compute(audioBuf, lfoValue, lfoDelay, controllers);
//        activeNote.note.compute(audioBuf, 0, 0, controllers);
        int[][] bufs = { audioBuf };
        int[][] bufs2 = { audioBuf2 };
        filter.process(bufs, filterControl, filterControl, bufs2);
        int jmax = len - i;
        for (int j = 0; j < Note.N; j++) {
            int val = audioBuf2[j] >> 4;
//            int val = audioBuf[j] >> 4;
            int clipVal = val < -(1 << 24) ? 0x8000 : val >= (1 << 24) ? 0x7fff : val >> 9;
            // TODO: maybe some dithering?
            float f;
            int x = clipVal;
            f = ((float) x) / (float) 0x8000;
            if (f > 1) {
                f = 1;
            }
            if (f < -1) {
                f = -1;
            }
            if (j < jmax) {
                buffer[offset + i + j] = f;
            } else {
                extraBuf[j - jmax] = f;
            }
        }
    }

    void noteOn(byte[] patch, int noteNumber, int velocity) {
        context.lfo.keyDown();
        activeNote = new Dx7.ActiveNote();
        activeNote.keyDown = true;
        activeNote.sustained = sustain;
        activeNote.note = new Note(context, patch, noteNumber, velocity);
        activeNotes.add(activeNote);
    }

    void noteOff() {
        if (activeNote.keyDown) {
            if (sustain) {
                activeNote.sustained = true;
            } else {
                activeNote.note.keyUp();
                activeNotes.remove(activeNote);
            }
            activeNote.keyDown = false;
        }
    }

    public void programChange(int p, byte[] patch, int ofs) {
        // TODO location
        byte[] b = Dx7Soundbank.getDirectBuffer(p);
        System.arraycopy(patch, ofs, b, 0, b.length);
        context.lfo.reset(b, 137);

Debug.println("Loaded patch " + p + ": " + new String(b, 145, 10));
    }

    public void sysex(byte[] b) {
        // TODO impl
    }

    public void controlChange(int controller, int value) {
        switch (controller) {
        case 1:
            filterControl[0] = 142365917 + value * 917175;
Debug.println("control change: " + controller + ", " + value);
            break;
        case 2:
            filterControl[1] = value * 528416;
Debug.println("control change: " + controller + ", " + value);
            break;
        case 3:
            filterControl[2] = value * 528416;
Debug.println("control change: " + controller + ", " + value);
            break;
        case 64:
            sustain = value != 0;
            if (!sustain) {
                for (ActiveNote activeNote : activeNotes) {
                    if (activeNote.sustained && !activeNote.keyDown) {
                        activeNote.note.keyUp();
                        activeNote.sustained = false;
                        activeNotes.remove(activeNote);
                    }
                }
            }
Debug.println("control change: " + controller + ", " + value);
            break;
        }
        controllers.values[controller] = value;
    }
}

/* */
