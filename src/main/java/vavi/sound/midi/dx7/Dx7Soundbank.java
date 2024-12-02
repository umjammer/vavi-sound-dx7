/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.dx7;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.sound.midi.Instrument;
import javax.sound.midi.Patch;
import javax.sound.midi.Soundbank;
import javax.sound.midi.SoundbankResource;

import com.sun.media.sound.ModelPatch;
import com.sun.media.sound.SimpleInstrument;

import static java.lang.System.getLogger;


/**
 * Dx7Soundbank.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/31 umjammer initial version <br>
 */
@SuppressWarnings("restriction")
public class Dx7Soundbank implements Soundbank {

    private static final Logger logger = getLogger(Dx7Soundbank.class.getName());

    /** */
    public static class Dx7Instrument extends SimpleInstrument {
        Object data;
        protected Dx7Instrument(int bank, int program, boolean isPercussion, Object data) {
            setPatch(new ModelPatch(bank, program, isPercussion));
            if (isPercussion && !(data instanceof byte[][])) {
                throw new IllegalArgumentException("percussuon data must be byte[][]");
            }
            if (!isPercussion && !(data instanceof byte[])) {
                throw new IllegalArgumentException("melodic data must be byte[]");
            }
            this.data = data;
        }

        @Override
        public String getName() {
            return getPatch().isPercussion() ? "Percussion" : new String((byte[]) data, 145, 10);
        }

        @Override
        public Class<?> getDataClass() {
            return getPatch().isPercussion() ? byte[][].class : byte[].class;
        }

        @Override
        public Object getData() {
            return data;
        }
    }

    private static final byte[][] b;

    /** */
    private static final Map<String, Instrument> instruments = new HashMap<>();

    static {
        try {
            DataInputStream dis = new DataInputStream(Dx7Soundbank.class.getResourceAsStream("/unpacked.bin"));
            int n = dis.available() / 156;
logger.log(Level.DEBUG, "patchs: " + n);
            b = new byte[n][156];
            for (int i = 0; i < n; i++) {
                dis.readFully(b[i]);
            }

            Properties props = new Properties();
            props.load(Dx7Soundbank.class.getResourceAsStream("/dx7.properties"));

            for (int i = 0; i < 128; i++) {
                int id = Integer.parseInt(props.getProperty("inst." + i));
                instruments.put(0 + "." + i, new Dx7Instrument(0, i, false, b[id]));
            }

            byte[][] drums = new byte[128][];
            for (int i = 0; i < 128; i++) {
                int id = Integer.parseInt(props.getProperty("drum." + i));
                drums[i] = b[id];
            }

            instruments.put("p." + 0 + "." + 0, new Dx7Instrument(0, 0, true, drums));
        } catch (NullPointerException e) {
            throw new IllegalStateException("unpacked.bin might not be found in classpath", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] getDirectBuffer(int p) {
        return (byte[]) instruments.get("0." + p).getData();
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
        return instruments.values().toArray(new Instrument[0]);
    }

    private final Map<String, Instrument> emergencies = new HashMap<>();

    @Override
    public Instrument getInstrument(Patch patch) {
//logger.log(Level.DEBUG, "patch: " + patch.getBank() + "," + patch.getProgram() + ", " + patch.getClass().getName());
        Instrument ins;
        boolean isPercussion = patch instanceof ModelPatch && ((ModelPatch) patch).isPercussion();
        String k = (isPercussion ? "p." : "") + patch.getBank() + "." + patch.getProgram();
        if (instruments.containsKey(k)) {
            ins = instruments.get(k);
        } else if (emergencies.containsKey(k)) {
            ins = emergencies.get(k);
        } else {
logger.log(Level.DEBUG, "instrument not found for: %d.%d, %02x, %s", patch.getBank(), patch.getProgram(), (patch.getBank() >> 7), isPercussion);
            Instrument emergency;
            if (patch.getBank() >> 7 == 0x7f || patch.getBank() >> 7 == 0x78 || isPercussion) { // TODO check spec.
                emergency = instruments.get("p.0.0");
            } else {
                emergency = instruments.get("0.0");
            }
            emergencies.put(k, emergency);
            ins = emergency;
        }
//logger.log(Level.DEBUG, "instrument: " + ins.getPatch().getBank() + ", " + ins.getPatch().getProgram() + ", " + ins.getName());
        return ins;
    }
}
