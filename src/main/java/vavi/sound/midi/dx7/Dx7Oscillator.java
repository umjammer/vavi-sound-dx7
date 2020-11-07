/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.dx7;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.Patch;
import javax.sound.midi.VoiceStatus;

import com.sun.media.sound.ModelAbstractOscillator;
import com.sun.media.sound.ModelPatch;
import com.sun.media.sound.SimpleInstrument;

import vavi.sound.dx7.Freqlut;
import vavi.sound.dx7.Lfo;
import vavi.sound.dx7.Note;
import vavi.sound.dx7.PitchEnv;
import vavi.sound.dx7.ResoFilter;
import vavi.util.Debug;


/**
 * Dx7Oscillator.
 *
 * TODO add a receiver for control change, sysex 
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 201104 nsano initial version <br>
 */
@SuppressWarnings("restriction")
public class Dx7Oscillator extends ModelAbstractOscillator {

    /** */
    public static class Dx7Instrument extends SimpleInstrument {
        byte[] data;
        protected Dx7Instrument(int bank, int program, boolean isPercussion, byte[] data) {
            setPatch(new ModelPatch(bank, program, isPercussion));
            this.data = data;
        }

        @Override
        public String getName() {
            return new String(data, 145, 10);
        }

        @Override
        public Class<?> getDataClass() {
            return byte[].class;
        }

        @Override
        public Object getData() {
            return data;
        }
    }

    private static byte[][] b;

    /** */
    private static Instrument[] instruments;

    static {
        try {
            DataInputStream dis = new DataInputStream(Dx7Soundbank.class.getResourceAsStream("/unpacked.bin"));
            int n = dis.available() / 156;
Debug.println("patchs: " + n);
            b = new byte[n][156];
            for (int i = 0; i < n; i++) {
                dis.readFully(b[i]);
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        instruments = new Instrument[129];
        for (int i = 0; i < instruments.length; i++) {
            if (i >= 113 && 1 <= 120) {
                // Percussive
                instruments[i] = new Dx7Instrument(0, i, true, b[8]);
            } else {
                instruments[i] = new Dx7Instrument(0, i, false, b[3]);
            }
        }
        instruments[128] = new Dx7Instrument(0, 0, true, b[8]);
    }

    private static class Dx7 {
        // The original DX7 had one single LFO. Later units had an LFO per note.
        Lfo lfo = new Lfo();

        // in MIDI units (0x4000 is neutral)
        Note.Controllers controllers;

        ResoFilter filter = new ResoFilter();

        int[] filterControl = new int[3];

        Dx7() {
            filterControl[0] = 258847126;
            filterControl[1] = 0;
            filterControl[2] = 0;
            controllers = new Note.Controllers(0x2000);
        }
    }

    private Dx7 dx7;

    // Extra buffering for when GetSamples wants a buffer not a multiple of N
    private float[] extraBuf = new float[Note.N];
    private int extraBufSize;

    @Override
    public void init() {
//Debug.println("init");
        dx7 = new Dx7();
        super.init();
    }

    private static float sampleRate = -1;

    @Override
    public void setSampleRate(float sampleRate) {
        if (Dx7Oscillator.sampleRate != sampleRate) {
Debug.println("sampleRate: " + sampleRate);
            Freqlut.init(sampleRate);
            Lfo.init(sampleRate);
            PitchEnv.init(sampleRate);
            Dx7Oscillator.sampleRate = sampleRate;
        }
        super.setSampleRate(sampleRate);
    }

    @Override
    public Instrument[] getInstruments() {
        for (Instrument i : instruments) {
            ((SimpleInstrument) i).add(getPerformer());
        }
        return instruments;
    }

    @Override
    public Instrument getInstrument(Patch patch) {
//Debug.println("patch: " + patch.getBank() + "," + patch.getProgram() + ", " + patch.getClass().getName());
        for (Instrument ins : instruments) {
            Patch p = ins.getPatch();
            if (p.getBank() != patch.getBank())
                continue;
            if (p.getProgram() != patch.getProgram())
                continue;
            if (p instanceof ModelPatch && patch instanceof ModelPatch) {
                if (((ModelPatch)p).isPercussion()
                        != ((ModelPatch)patch).isPercussion()) {
                    continue;
                }
            }
//Debug.println("instrument: " + ins.getPatch().getBank() + ", " + ins.getPatch().getProgram() + ", " + ins.getName());
            return ins;
        }
Debug.println("instrument not found for: " + patch.getBank() + "," + patch.getProgram());
        return null;
    }

    private static class ActiveNote {
        boolean keydown;
        Note note;
    }

    private ActiveNote activeNote;

    @Override
    public void noteOn(MidiChannel channel, VoiceStatus voice, int noteNumber, int velocity) {
        if (velocity > 0) {
//Debug.println("channel: " + voice.channel + ", patch: " + voice.bank + "," + voice.program);
            dx7.lfo.keydown(); // TODO: should only do this if # keys down was 0
            activeNote = new ActiveNote();
            activeNote.keydown = true;
            activeNote.note = new Note((byte[]) getInstrument(new Patch(voice.bank, voice.program)).getData(), noteNumber, velocity);
            super.noteOn(channel, voice, noteNumber, velocity);
        } else {
            if (activeNote.keydown) {
                activeNote.note.keyup();
                activeNote.keydown = false;
            }
            super.noteOff(velocity);
        }
    }

    @Override
    public void noteOff(int velocity) {
        activeNote.note.keyup();
        activeNote.keydown = false;
        super.noteOff(velocity);
    }

    /**
     * Note.N = 64, len = 300
     *
     * @see com.sun.media.sound.SoftVoice#processControlLogic()
     */
    @Override
    public int read(float[][] buffers, int offset, int len) throws IOException {

        // Grab channel 0 buffer from buffers
        float[] buffer = buffers[0];

        int i = 0;
        for (; i < len && i < extraBufSize; i++) {
            buffer[offset + i] = extraBuf[i];
        }
        if (extraBufSize > len) {
            for (int j = 0; j < extraBufSize - len; j++) {
                extraBuf[j] = extraBuf[j + len];
            }
            extraBufSize -= len;
            return len;
        }

        for (; i < len; i += Note.N) {
            int[] audioBuf = new int[Note.N];
            int[] audioBuf2 = new int[Note.N];
            int lfoValue = dx7.lfo.getsample();
            int lfoDelay = dx7.lfo.getdelay();
            activeNote.note.compute(audioBuf, lfoValue, lfoDelay, dx7.controllers);
//            activeNote.dx7_note.compute(audiobuf, 0, 0, controllers_);
            final int[][] bufs = { audioBuf };
            int[][] bufs2 = { audioBuf2 };
            dx7.filter.process(bufs, dx7.filterControl, dx7.filterControl, bufs2);
            int jmax = len - i;
            for (int j = 0; j < Note.N; j++) {
                int val = audioBuf2[j] >> 4;
//                int val = audiobuf[j] >> 4;
                int clip_val = val < -(1 << 24) ? 0x8000 : val >= (1 << 24) ? 0x7fff : val >> 9;
                // TODO: maybe some dithering?
                float f;
                int x = clip_val;
                f = ((float) x) / (float) 32768;
                if (f > 1) f = 1;
                if (f < -1) f = -1;
                if (j < jmax) {
                    buffer[offset + i + j] = f;
                } else {
                    extraBuf[j - jmax] = f;
                }
            }
        }

        extraBufSize = i - len;

        return len;
    }
}
