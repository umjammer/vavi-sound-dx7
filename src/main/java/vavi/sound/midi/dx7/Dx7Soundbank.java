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
import javax.sound.midi.Patch;
import javax.sound.midi.Soundbank;
import javax.sound.midi.SoundbankResource;

import vavi.util.Debug;


/**
 * Dx7Soundbank.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/31 umjammer initial version <br>
 */
public class Dx7Soundbank implements Soundbank {

    private static byte[][] b;

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
    }

    /** */
    public Dx7Soundbank() {
        instruments = new Instrument[b.length];
        for (int i = 0; i < instruments.length; i++) {
            instruments[i] = new Dx7Instrument(this, i / 128, i % 128, "instrument." + i / 128 + "." + i % 128, b[i]);
        }
    }

    /** */
    private Instrument[] instruments;

    @Override
    public String getName() {
        return "Dx7Soundbank";
    }

    @Override
    public String getVersion() {
        return "0.0.1";
    }

    @Override
    public String getVendor() {
        return "vavisoft";
    }

    @Override
    public String getDescription() {
        return "soundbank for DX7";
    }

    @Override
    public SoundbankResource[] getResources() {
        return new SoundbankResource[0];
    }

    @Override
    public Instrument[] getInstruments() {
        return instruments;
    }

    @Override
    public Instrument getInstrument(Patch patch) {
        for (int i = 0; i < instruments.length; i++) {
            if (instruments[i].getPatch().getProgram() == patch.getProgram() &&
                instruments[i].getPatch().getBank() == patch.getBank()) {
                return instruments[i];
            }
        }
        return null;
    }

    /** */
    public static class Dx7Instrument extends Instrument {
        byte[] data;
        protected Dx7Instrument(Dx7Soundbank sounBbank, int bank, int program, String name, byte[] data) {
            super(sounBbank, new Patch(bank, program), name, byte[].class);
            this.data = data;
        }

        @Override
        public Object getData() {
            return data;
        }
    }
}

/* */
