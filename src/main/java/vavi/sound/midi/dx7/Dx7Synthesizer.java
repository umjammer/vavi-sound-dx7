/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.dx7;

import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.sound.midi.Instrument;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Patch;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Transmitter;
import javax.sound.midi.VoiceStatus;

import vavi.sound.midi.MidiUtil;
import vavi.util.StringUtil;

import static java.lang.System.getLogger;


/**
 * Dx7Synthesizer.
 *
 * creating a synthesizer is too much for us. we need to reproduce
 * timing management, multiple voice management, mix down polyphony etc.
 * Gervill provides well considered sound system. what we need to do
 * is just to create an oscillator. see {@link Dx7Oscillator}.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/31 umjammer initial version <br>
 */
public class Dx7Synthesizer implements Synthesizer {

    private static final Logger logger = getLogger(Dx7Synthesizer.class.getName());

    static {
        try {
            try (InputStream is = Dx7Synthesizer.class.getResourceAsStream("/META-INF/maven/vavi/vavi-sound-dx7/pom.properties")) {
                if (is != null) {
                    Properties props = new Properties();
                    props.load(is);
                    version = props.getProperty("version", "undefined in pom.properties");
                } else {
                    version = System.getProperty("vavi.test.version", "undefined");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String version;

    /** the device information */
    protected static final MidiDevice.Info info =
        new MidiDevice.Info("DX7 MIDI Synthesizer",
                            "vavi",
                            "Software synthesizer for DX7",
                            "Version " + version) {};

    /** required gervill */
    private Synthesizer synthesizer;

    private Dx7Oscillator dx7Oscillator;

    @Override
    public Info getDeviceInfo() {
        return info;
    }

    @Override
    public void open() throws MidiUnavailableException {
        synthesizer = MidiUtil.getDefaultSynthesizer(Dx7MidiDeviceProvider.class);
logger.log(Level.DEBUG, "wrapped synthesizer: " + synthesizer.getClass().getName());
        synthesizer.open();
        synthesizer.unloadAllInstruments(synthesizer.getDefaultSoundbank());
        synthesizer.loadAllInstruments(dx7Oscillator = new Dx7Oscillator());
    }

    @Override
    public void close() {
        synthesizer.close();
    }

    @Override
    public boolean isOpen() {
        if (synthesizer != null) {
            return synthesizer.isOpen();
        } else {
            return false;
        }
    }

    @Override
    public long getMicrosecondPosition() {
        return synthesizer.getMicrosecondPosition();
    }

    @Override
    public int getMaxReceivers() {
        return -1;
    }

    @Override
    public int getMaxTransmitters() {
        return 0;
    }

    @Override
    public Receiver getReceiver() throws MidiUnavailableException {
        return new Dx7Receiver(synthesizer.getReceiver()); // TODO not works, infinite loop?
//        return synthesizer.getReceiver();
    }

    @Override
    public List<Receiver> getReceivers() {
        return receivers; // TODO ditto
//        return synthesizer.getReceivers();
    }

    @Override
    public Transmitter getTransmitter() throws MidiUnavailableException {
        return synthesizer.getTransmitter();
    }

    @Override
    public List<Transmitter> getTransmitters() {
        return synthesizer.getTransmitters();
    }

    @Override
    public int getMaxPolyphony() {
        return synthesizer.getMaxPolyphony();
    }

    @Override
    public long getLatency() {
        return synthesizer.getLatency();
    }

    @Override
    public MidiChannel[] getChannels() {
        return synthesizer.getChannels();
    }

    @Override
    public VoiceStatus[] getVoiceStatus() {
        return synthesizer.getVoiceStatus();
    }

    @Override
    public boolean isSoundbankSupported(Soundbank soundbank) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean loadInstrument(Instrument instrument) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void unloadInstrument(Instrument instrument) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean remapInstrument(Instrument from, Instrument to) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Soundbank getDefaultSoundbank() {
        return dx7Oscillator;
    }

    @Override
    public Instrument[] getAvailableInstruments() {
        return dx7Oscillator.getInstruments();
    }

    @Override
    public Instrument[] getLoadedInstruments() {
        return dx7Oscillator.getInstruments();
    }

    @Override
    public boolean loadAllInstruments(Soundbank soundbank) {
        return false;
    }

    @Override
    public void unloadAllInstruments(Soundbank soundbank) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean loadInstruments(Soundbank soundbank, Patch[] patchList) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void unloadInstruments(Soundbank soundbank, Patch[] patchList) {
        // TODO Auto-generated method stub

    }

    private final List<Receiver> receivers = new ArrayList<>();

    // TODO move into DX7 class?
    private class Dx7Receiver implements Receiver {
        final Receiver receiver;

        public Dx7Receiver(Receiver receiver) {
            receivers.add(this);
            this.receiver = receiver;
logger.log(Level.DEBUG, "receiver: " + this.receiver);
        }

        @Override
        public void send(MidiMessage message, long timeStamp) {
try {
            if (message instanceof ShortMessage shortMessage) {
                int command = shortMessage.getCommand();
                int data1 = shortMessage.getData1();
                int data2 = shortMessage.getData2();
                switch (command) {
                    case ShortMessage.CONTROL_CHANGE -> dx7Oscillator.getDx7().controlChange(data1, data2);
                }
            } else if (message instanceof SysexMessage sysexMessage) {
                byte[] data = sysexMessage.getData();
//logger.log(Level.DEBUG, "sysex: %02x %02x %02x", data[1], data[2], data[3]);

logger.log(Level.DEBUG, "sysex: %02X\n%s", sysexMessage.getStatus(), StringUtil.getDump(data));
                switch (data[0]) {
                    case 0x43 -> {
                        switch (data[1]) {
                            case 0x00 -> {
                                switch (data[2]) {
                                case 0x09: //
                                    if (data[3] == 0x20 && data[4] == 0x00) { //
logger.log(Level.DEBUG, "sysex: bank change?");
                                    }
                                    break;
                                case 0x00: //
                                    if (data[3] == 0x01 && data[4] == 0x1b) {
                                        dx7Oscillator.getDx7().programChange(0, data, 5);
                                    }
                                    break;
                                } //
                            }
                        } // Yamaha
                    }
                    default -> {}
                }

                receiver.send(sysexMessage, timeStamp);
            } else if (message instanceof MetaMessage metaMessage) {
logger.log(Level.DEBUG, "meta: %02x", metaMessage.getType());
                switch (metaMessage.getType()) {
                    case 0x2f -> {}
                }
                receiver.send(message, timeStamp);
            } else {
                assert false;
            }
} catch (Throwable t) {
 logger.log(Level.DEBUG, t);
}
            this.receiver.send(message, timeStamp);
        }

        @Override
        public void close() {
            receivers.remove(this);
        }
    }
}
