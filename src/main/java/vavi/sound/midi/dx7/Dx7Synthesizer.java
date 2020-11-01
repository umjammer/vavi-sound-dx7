/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.dx7;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import vavi.sound.dx7.Note;
import vavi.sound.dx7.SynthUnit;
import vavi.sound.midi.dx7.Dx7SoundBank.Dx7Instrument;
import vavi.util.Debug;
import vavi.util.StringUtil;


/**
 * MochaSynthesizer.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/31 umjammer initial version <br>
 */
public class Dx7Synthesizer implements Synthesizer {

    private static Logger logger = Logger.getLogger(Dx7Synthesizer.class.getName());

    private static final String version = "0.0.1.";

    /** the device information */
    protected static final MidiDevice.Info info =
        new MidiDevice.Info("DX7 MIDI Synthesizer",
                            "Vavisoft",
                            "Software synthesizer for DX7",
                            "Version " + version) {};

    // TODO != channel???
    private static final int MAX_CHANNEL = 1;

    private Dx7MidiChannel[] channels = new Dx7MidiChannel[MAX_CHANNEL];

    private VoiceStatus[] voiceStatus = new VoiceStatus[MAX_CHANNEL];

    private long timestump;

    private boolean isOpen;

    private SourceDataLine line;

    // ----

    private Dx7SoundBank soundBank;

    /** */
    private Dx7Instrument[] instruments;

    private static final AudioFormat audioFormat = new AudioFormat(44100, 16, 1, true, false);

    static {
        SynthUnit.init(audioFormat.getSampleRate());
    }

    private SynthUnit synthUnit;

    // ----

    @Override
    public Info getDeviceInfo() {
        return info;
    }

    @Override
    public void open() throws MidiUnavailableException {
        if (isOpen()) {
Debug.println("already open: " + hashCode());
            return;
        }

        for (int i = 0; i < MAX_CHANNEL; i++) {
            channels[i] = new Dx7MidiChannel(i);
            voiceStatus[i] = new VoiceStatus();
            voiceStatus[i].channel = i;
        }

        soundBank = new Dx7SoundBank();
        instruments = new Dx7Instrument[128];
        for (int i = 0; i < 128; ++i) {
            instruments[i] = (Dx7Instrument) soundBank.getInstruments()[i];
        }

        //
        isOpen = true;

        init();
        executor.submit(this::play);
    }

    private ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setPriority(Thread.MAX_PRIORITY);
        return thread;
    });

    /** */
    private void init() throws MidiUnavailableException {
        try {
            synthUnit = new SynthUnit();

            //
            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat, AudioSystem.NOT_SPECIFIED);
            line = (SourceDataLine) AudioSystem.getLine(lineInfo);
Debug.println(line.getClass().getName());
            line.addLineListener(event -> { Debug.println(event.getType()); });

            line.open();
            line.start();
        } catch (LineUnavailableException e) {
            throw (MidiUnavailableException) new MidiUnavailableException().initCause(e);
        }

        timestump = 0;
    }

    /** */
    private void play() {
        byte[] buf = new byte[4 * (int) audioFormat.getSampleRate() / 10];
//Debug.printf("buf: %d", buf.length);

        while (isOpen) {
            try {
                long msec = System.currentTimeMillis() - timestump;
                timestump = System.currentTimeMillis();
                msec = msec > 100 ? 100 : msec;
                int len = (4 * (int) (audioFormat.getSampleRate() * msec / 1000.0)) / Note.N * Note.N;
                read(buf, 0, len);
                line.write(buf, 0, buf.length);
                Thread.sleep(33); // TODO how to define?
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /** */
    private void read(byte[] buf, int ofs, int len) {
        int[] b = new int[len / 2];
        synthUnit.getSamples(b.length, b);
        for (int i = 0; i < b.length; i++) {
            buf[i * 2] = (byte) (b[i] & 0xff);
            buf[i * 2 + 1] = (byte) ((b[i] >> 8) & 0xff);
        }
    }

    @Override
    public void close() {
        isOpen = false;
        line.drain();
        line.close();
        executor.shutdown();
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public long getMicrosecondPosition() {
        return timestump;
    }

    @Override
    public int getMaxReceivers() {
        return 1;
    }

    @Override
    public int getMaxTransmitters() {
        return 0;
    }

    @Override
    public Receiver getReceiver() throws MidiUnavailableException {
        return new Dx7Receiver();
    }

    @Override
    public List<Receiver> getReceivers() {
        return receivers;
    }

    @Override
    public Transmitter getTransmitter() throws MidiUnavailableException {
        return null;
    }

    @Override
    public List<Transmitter> getTransmitters() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public int getMaxPolyphony() {
        return MAX_CHANNEL * 6; // TODO
    }

    @Override
    public long getLatency() {
        return 0;
    }

    @Override
    public MidiChannel[] getChannels() {
        return channels;
    }

    @Override
    public VoiceStatus[] getVoiceStatus() {
        return voiceStatus;
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Instrument[] getAvailableInstruments() {
        // TODO Auto-generated method stub
        return new Instrument[0];
    }

    @Override
    public Instrument[] getLoadedInstruments() {
        return instruments;
    }

    @Override
    public boolean loadAllInstruments(Soundbank soundbank) {
        // TODO Auto-generated method stub
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

    public class Dx7MidiChannel implements MidiChannel {

        private int channel;

        private int[] polyPressure = new int[128];
        private int pressure;
        private int pitchBend;
        private int[] control = new int[128];

        /** */
        public Dx7MidiChannel(int channel) {
            this.channel = channel;
        }

        @Override
        public void noteOn(int noteNumber, int velocity) {
            synthUnit.noteOn(noteNumber, velocity);
            //
            voiceStatus[channel].note = noteNumber;
            this.pressure = velocity;
        }

        @Override
        public void noteOff(int noteNumber, int velocity) {
            synthUnit.noteOff(noteNumber);
            //
            voiceStatus[channel].note = 0;
            this.pressure = velocity;
        }

        @Override
        public void noteOff(int noteNumber) {
            noteOff(noteNumber, 0);
        }

        @Override
        public void setPolyPressure(int noteNumber, int pressure) {
            polyPressure[noteNumber] = pressure;
        }

        @Override
        public int getPolyPressure(int noteNumber) {
            return polyPressure[noteNumber];
        }

        @Override
        public void setChannelPressure(int pressure) {
            this.pressure = pressure;
        }

        @Override
        public int getChannelPressure() {
            return pressure;
        }

        @Override
        public void controlChange(int controller, int value) {
            synthUnit.controlChange(controller, value);
            //
            control[controller] = value;
        }

        @Override
        public int getController(int controller) {
            return control[controller];
        }

        @Override
        public void programChange(int program) {
            synthUnit.programChange(program, (byte[]) instruments[program].getData(), 0);
            //
            voiceStatus[channel].program = program;
        }

        @Override
        public void programChange(int bank, int program) {
            int bankMSB = bank >> 7;
            int bankLSB = bank & 0x7F;
            controlChange(0, bankMSB);
            controlChange(32, bankLSB);
            programChange(program);
        }

        @Override
        public int getProgram() {
            return voiceStatus[channel].program;
        }

        @Override
        public void setPitchBend(int bend) {
            pitchBend = bend;
        }

        @Override
        public int getPitchBend() {
            return pitchBend;
        }

        @Override
        public void resetAllControllers() {
            controlChange(121, 0); // 0x79
        }

        @Override
        public void allNotesOff() {
            controlChange(123, 0); // 0x7b
        }

        @Override
        public void allSoundOff() {
            controlChange(120, 0); // 0x78
        }

        @Override
        public boolean localControl(boolean on) {
            controlChange(122, on ? 127 : 0); // 0x7a
            return getController(122) >= 64;
        }

        @Override
        public void setMono(boolean on) {
            controlChange(on ? 126 : 127, 0); // 0x7e, 0x7d
        }

        @Override
        public boolean getMono() {
            return getController(126) == 0;
        }

        @Override
        public void setOmni(boolean on) {
            controlChange(on ? 125 : 124, 0); // 0x7d, 0x7c
        }

        @Override
        public boolean getOmni() {
            return getController(125) == 0;
        }

        @Override
        public void setMute(boolean mute) {
            voiceStatus[channel].active = !mute;
        }

        @Override
        public boolean getMute() {
            return voiceStatus[channel].active;
        }

        @Override
        public void setSolo(boolean soloState) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public boolean getSolo() {
            // TODO Auto-generated method stub
            return false;
        }
    }

    private List<Receiver> receivers = new ArrayList<>();

    private class Dx7Receiver implements Receiver {
        private boolean isOpen;

        public Dx7Receiver() {
            receivers.add(this);
            isOpen = true;
        }

        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (!isOpen) {
                throw new IllegalStateException("receiver is not open");
            }

            if (message instanceof ShortMessage) {
                ShortMessage shortMessage = (ShortMessage) message;
                int channel = shortMessage.getChannel();
                int command = shortMessage.getCommand();
                int data1 = shortMessage.getData1();
                int data2 = shortMessage.getData2();
                switch (command) {
                case ShortMessage.NOTE_OFF:
                    channels[channel].noteOff(data1, data2);
                    break;
                case ShortMessage.NOTE_ON:
                    channels[channel].noteOn(data1, data2);
                    break;
                case ShortMessage.POLY_PRESSURE:
                    channels[channel].setPolyPressure(data1, data2);
                    break;
                case ShortMessage.CONTROL_CHANGE:
                    channels[channel].controlChange(data1, data2);
                    break;
                case ShortMessage.PROGRAM_CHANGE:
                    channels[channel].programChange(data1);
                    break;
                case ShortMessage.CHANNEL_PRESSURE:
                    channels[channel].setChannelPressure(data1);
                    break;
                case ShortMessage.PITCH_BEND:
                    channels[channel].setPitchBend(data1 | (data2 << 7));
                    break;
                default:
Debug.printf("unhandled short: %02X\n", command);
                }
            } else if (message instanceof SysexMessage) {
                SysexMessage sysexMessage = (SysexMessage) message;
                byte[] data = sysexMessage.getData();
//Debug.print("sysex:\n" + StringUtil.getDump(data));
//Debug.printf(Level.FINE, "sysex: %02x %02x %02x", data[1], data[2], data[3]);
                if (data[0] == 0x43) {
                    if (data[1] == 0x00 && data[2] == 0x09 && data[3] == 0x20 && data[4] == 0x00) {
                        if (data.length > 4096) {
Debug.println("sysex: bank change?");
                        }
                    } else if (data[1] == 0x00 && data[2] == 0x00 && data[3] == 0x01 && data[4] == 0x1b) {
                        synthUnit.programChange(0, data, 5);
                    } else {
                        synthUnit.sysex(data);
                    }
                } else {
Debug.println("sysex\n" + StringUtil.getDump(data));
                }
            } else if (message instanceof SysexMessage) {
                MetaMessage metaMessage = (MetaMessage) message;
Debug.printf("meta: %02x", metaMessage.getType());
                switch (metaMessage.getType()) {
                case 0x2f:
                    break;
                }
            } else {
                assert false;
            }
        }

        @Override
        public void close() {
            receivers.remove(this);
            isOpen = false;
        }
    }
}

/* */
