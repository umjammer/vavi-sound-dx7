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

import com.sun.media.sound.ModelPatch;
import com.sun.media.sound.SimpleInstrument;

import vavi.util.Debug;


/**
 * Dx7Soundbank.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/31 umjammer initial version <br>
 */
@SuppressWarnings("restriction")
public class Dx7Soundbank implements Soundbank {

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
            if (i >= 113 && i <= 120) {
                // Percussive
                instruments[i] = new Dx7Instrument(0, i, true, b[8]);
            } else {
                instruments[i] = new Dx7Instrument(0, i, false, b[3]);
            }
        }
        instruments[128] = new Dx7Instrument(0, 0, true, b[8]);
    }

    static byte[] getDireectBuffer(int p) {
        return (byte[]) instruments[p].getData();
    }

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
        return "vavi";
    }

    @Override
    public String getDescription() {
        return "Soundbank for DX7";
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
Debug.printf("instrument not found for: %d.%d, %02x", patch.getBank(), patch.getProgram(), (patch.getBank() >> 7));
        if (patch.getBank() >> 7 == 0x7f || patch.getBank() >> 7 == 0x78) { // TODO check spec.
            return instruments[128];
        } else {
            return instruments[0];
        }
    }
}

/* */
