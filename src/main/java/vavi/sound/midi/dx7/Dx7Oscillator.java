/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.dx7;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.Patch;
import javax.sound.midi.VoiceStatus;

import com.sun.media.sound.ModelAbstractOscillator;
import com.sun.media.sound.SimpleInstrument;

import com.sun.media.sound.ModelPatch;

import static java.lang.System.getLogger;


/**
 * Dx7Oscillator.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 201104 nsano initial version <br>
 */
@SuppressWarnings("restriction")
public class Dx7Oscillator extends ModelAbstractOscillator {

    private static final Logger logger = getLogger(Dx7Oscillator.class.getName());

    private static final Dx7Soundbank soundbank;

    static {
        soundbank = new Dx7Soundbank();
    }

    /** */
    private final Dx7 dx7 = new Dx7();

    public Dx7 getDx7() {
        return dx7;
    }

    // Extra buffering for when GetSamples wants a buffer not a multiple of N
    private final float[] extraBuf = new float[Dx7.BUFFER_SIZE];
    private int extraBufSize;

    @Override
    public void init() {
//logger.log(Level.DEBUG, "init");
        super.init();
    }

    @Override
    public void setSampleRate(float sampleRate) {
        dx7.setSampleRate(sampleRate);
        super.setSampleRate(sampleRate);
    }

    @Override
    public Instrument[] getInstruments() {
        Instrument[] instruments = soundbank.getInstruments();
        for (Instrument i : instruments) {
            ((SimpleInstrument) i).add(getPerformer());
        }
        return instruments;
    }

    @Override
    public Instrument getInstrument(Patch patch) {
        return soundbank.getInstrument(patch);
    }

    @Override
    public void noteOn(MidiChannel channel, VoiceStatus voice, int noteNumber, int velocity) {
        if (velocity > 0) {
//logger.log(Level.DEBUG, "channel: " + voice.channel + ", patch: " + voice.bank + "," + voice.program);
try {
            // TODO how to define drums
            if (voice.channel != 9) {
                dx7.noteOn((byte[]) getInstrument(new Patch(voice.bank, voice.program)).getData(), noteNumber, velocity);
            } else {
                byte[][] drums = (byte[][]) getInstrument(new ModelPatch(voice.bank, voice.program, true)).getData();
                dx7.noteOn(drums[noteNumber], noteNumber, velocity);
            }
} catch (Throwable t) {
 logger.log(Level.ERROR, "ch: " + voice.channel + ", note: " + noteNumber);
}
            super.noteOn(channel, voice, noteNumber, velocity);
        } else {
            dx7.noteOff();
            super.noteOff(velocity);
        }
    }

    @Override
    public void noteOff(int velocity) {
        dx7.noteOff();
        super.noteOff(velocity);
    }

    /**
     * Dx7.BUFFER_SIZE = 64, len = 300
     *
     * @see "com.sun.media.sound.SoftVoice#processControlLogic()"
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
            System.arraycopy(extraBuf, len, extraBuf, 0, extraBufSize - len);
            extraBufSize -= len;
            return len;
        }

        for (; i < len; i += Dx7.BUFFER_SIZE) {
            dx7.write(offset, len, i, buffer, extraBuf);
        }

        extraBufSize = i - len;

        return len;
    }
}
