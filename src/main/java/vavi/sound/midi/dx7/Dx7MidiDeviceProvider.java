/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.dx7;

import java.util.logging.Level;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.spi.MidiDeviceProvider;

import vavi.util.Debug;


/**
 * Dx7MidiDeviceProvider.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 201031 nsano initial version <br>
 */
public class Dx7MidiDeviceProvider extends MidiDeviceProvider {

    /**  */
    public final static int MANUFACTURER_ID = 0x43;

    /** */
    private static final MidiDevice.Info[] infos = new MidiDevice.Info[] { Dx7Synthesizer.info };

    /* */
    public MidiDevice.Info[] getDeviceInfo() {
        return infos;
    }

    /** */
    public MidiDevice getDevice(MidiDevice.Info info)
        throws IllegalArgumentException {

        if (info == Dx7Synthesizer.info) {
Debug.println(Level.FINE, "★1 info: " + info);
            Dx7Synthesizer synthesizer = new Dx7Synthesizer();
            return synthesizer;
        } else {
Debug.println(Level.FINE, "★1 here: " + info);
            throw new IllegalArgumentException();
        }
    }
}
