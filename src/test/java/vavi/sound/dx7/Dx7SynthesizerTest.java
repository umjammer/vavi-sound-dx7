/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.dx7;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import javax.sound.midi.Instrument;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.media.sound.ModelAbstractOscillator;
import com.sun.media.sound.ModelPatch;

import vavi.sound.midi.dx7.Dx7Oscillator;
import vavi.sound.midi.dx7.Dx7Soundbank;
import vavi.sound.midi.dx7.Dx7Synthesizer;
import vavi.util.Debug;
import vavi.util.StringUtil;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import static vavi.sound.SoundUtil.volume;
import static vavi.sound.midi.MidiUtil.volume;


/**
 * Dx7SynthesizerTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/30 umjammer initial version <br>
 */
@SuppressWarnings("restriction")
@PropsEntity(url = "file:local.properties")
public class Dx7SynthesizerTest {

    static {
        System.setProperty("javax.sound.midi.Synthesizer", "#Gervill");
        System.setProperty("javax.sound.midi.Sequencer", "#Real Time Sequencer");
    }

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    static float volume = Float.parseFloat(System.getProperty("vavi.test.volume.midi",  "0.2"));

    @BeforeEach
    void setup() throws IOException {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }
    }

    @Property(name = "test.midi")
    String midi = "src/test/resources/test.mid";

    @Test
    @DisplayName("directly")
    void test() throws Exception {
        Synthesizer synthesizer = new Dx7Synthesizer();
        synthesizer.open();
Debug.println("synthesizer: " + synthesizer.getClass().getName());

        Sequencer sequencer = MidiSystem.getSequencer(false);
        sequencer.open();
Debug.println("sequencer: " + sequencer.getClass().getName());
        Receiver receiver = synthesizer.getReceiver();
        sequencer.getTransmitter().setReceiver(receiver);

        Path file = Paths.get(midi);

        Sequence seq = MidiSystem.getSequence(new BufferedInputStream(Files.newInputStream(file)));

        CountDownLatch countDownLatch = new CountDownLatch(1);
        MetaEventListener mel = meta -> {
//System.err.println("META: " + meta.getType());
            if (meta.getType() == 47) {
                countDownLatch.countDown();
            }
        };
        sequencer.setSequence(seq);
        sequencer.addMetaEventListener(mel);
System.err.println("START");
        sequencer.start();

        volume(receiver, volume); // gervill volume works!

if (!System.getProperty("vavi.test", "").equals("ide")) {
 Thread.sleep(10 * 1000);
 sequencer.stop();
 Debug.println("STOP");
} else {
        countDownLatch.await();
}
System.err.println("END");
        sequencer.removeMetaEventListener(mel);
        sequencer.close();

        synthesizer.close();
    }

    @Test
    void test2() throws Exception {
        Synthesizer synthesizer = new Dx7Synthesizer();
        synthesizer.open();
Debug.println("synthesizer: " + synthesizer);

        MidiChannel channel = synthesizer.getChannels()[0];
        for (int i = 0; i < 32; i++) {
            channel.programChange(1 + i);
            channel.noteOn(63 + i, 127);
            Thread.sleep(100);
            channel.noteOff(63 + i);
        }

        Thread.sleep(3000);

        synthesizer.close();
    }

    @Test
    void test4() throws Exception {
        ModelAbstractOscillator oscs;
        try {
            oscs = Dx7Oscillator.class.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
        oscs.setSampleRate(44100);
        oscs.init();
    }

    @Test
    @DisplayName("spi")
    void test3() throws Exception {
        Synthesizer synthesizer = MidiSystem.getSynthesizer();
Debug.println("synthesizer: " + synthesizer.getClass().getName());
        synthesizer.open();
        synthesizer.unloadAllInstruments(synthesizer.getDefaultSoundbank());
        synthesizer.loadAllInstruments(new Dx7Oscillator());

        Path file = Paths.get(midi);

        Sequence seq = MidiSystem.getSequence(new BufferedInputStream(Files.newInputStream(file)));

        Sequencer sequencer = MidiSystem.getSequencer(false);
Debug.println("sequencer: " + sequencer.getClass().getName());
        sequencer.open();
        Receiver receiver = synthesizer.getReceiver();
        sequencer.getTransmitter().setReceiver(receiver);

        CountDownLatch countDownLatch = new CountDownLatch(1);
        MetaEventListener mel = meta -> {
System.err.println("META: " + meta.getType());
            if (meta.getType() == 47) {
                countDownLatch.countDown();
            }
        };
        sequencer.setSequence(seq);
        sequencer.addMetaEventListener(mel);
System.err.println("START");
        sequencer.start();

        volume(receiver, volume); // gervill volume works!

if (!System.getProperty("vavi.test", "").equals("ide")) {
 Thread.sleep(10 * 1000);
 sequencer.stop();
 Debug.println("STOP");
} else {
        countDownLatch.await();
}
System.err.println("END");
        sequencer.removeMetaEventListener(mel);

        sequencer.stop();
        sequencer.close();
        synthesizer.close();
    }

    /**
     *
     * @param args
     */
    public static void main(String[] args) throws Exception {
        t3(args);
    }

    public static void t3(String[] args) throws Exception {
        final int nSamples = 20 * 1024;
        final float sampleRate = 44100.0f;
        Instrument instrument = new Dx7Soundbank().getInstrument(new ModelPatch(0, 0, true));
Debug.println(instrument.getName());

        AudioFormat audioFormat = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat, AudioSystem.NOT_SPECIFIED);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
        line.addLineListener(event -> Debug.println(event.getType()));
        line.open();
        volume(line, volume);
        line.start();

for (int m = 35; m < 82; m++) {

        byte[] x = ((byte[][]) instrument.getData())[m];
Debug.println("drum: " + m);

        Context context = Context.getInstance(sampleRate);

        ResoFilter filter = new ResoFilter(context);
        int[] filterControl = new int[3];
        filterControl[0] = 258847126;
        filterControl[1] = 0;
        filterControl[2] = 0;

        context.lfo.keyDown();
        Note note = new Note(context, x, 63, 100);
        Note.Controllers controllers = new Note.Controllers(0x2000);
        int[] buf = new int[Note.N];
        int[] buf2 = new int[Note.N];

        for (int i = 0; i < nSamples; i += Note.N) {
            if (i >= nSamples * (7. / 8.)) {
                note.keyUp();
            }
            int lfoValue = context.lfo.getSample();
            int lfoDelay = context.lfo.getDelay();
            note.compute(buf, lfoValue, lfoDelay, controllers);
            int[][] bufs = { buf };
            int[][] bufs2 = { buf2 };
            filter.process(bufs, filterControl, filterControl, bufs2);
            for (int j = 0; j < Note.N; j++) {
                buf2[j] >>= 2;
            }
            writeData(line, buf2, Note.N);
        }
}
        line.drain();
        line.close();
    }

    public static void t2(String[] args) throws Exception {
        final int nSamples = 20 * 1024;
        final float sampleRate = 44100.0f;
        Instrument[] instruments = new Dx7Soundbank().getInstruments();

        AudioFormat audioFormat = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat, AudioSystem.NOT_SPECIFIED);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
        line.addLineListener(event -> Debug.println(event.getType()));
        line.open();
        volume(line, volume);
        line.start();

        Random r = new Random();
for (int m = 0; m < 128; m++) {

        byte[] x = (byte[]) instruments[m].getData();
Debug.println(m + ": " + instruments[m].getName());

        Context context = Context.getInstance(audioFormat.getSampleRate());

        ResoFilter filter = new ResoFilter(context);
        int[] filterControl = new int[3];
        filterControl[0] = 258847126;
        filterControl[1] = 0;
        filterControl[2] = 0;

        context.lfo.keyDown();
        Note note = new Note(context, x, 50 + (r.nextInt(12)), 100);
        Note.Controllers controllers = new Note.Controllers(0x2000);
        int[] buf = new int[Note.N];
        int[] buf2 = new int[Note.N];

        for (int i = 0; i < nSamples; i += Note.N) {
            if (i >= nSamples * (7. / 8.)) {
                note.keyUp();
            }
            int lfoValue = context.lfo.getSample();
            int lfoDelay = context.lfo.getDelay();
            note.compute(buf, lfoValue, lfoDelay, controllers);
            int[][] bufs = { buf };
            int[][] bufs2 = { buf2 };
            filter.process(bufs, filterControl, filterControl, bufs2);
            for (int j = 0; j < Note.N; j++) {
                buf2[j] >>= 2;
            }
            writeData(line, buf2, Note.N);
        }
}
        line.drain();
        line.close();
    }

    public static void t1(String[] args) throws Exception {
        final int nSamples = 10 * 1024;
        final float sampleRate = 44100.0f;

        int k = 1004;

        DataInputStream dis = new DataInputStream(Dx7SynthesizerTest.class.getResourceAsStream("/unpacked.bin"));
        int n = dis.available() / 156;
Debug.println("patchs: " + n);
        byte[][] b = new byte[n][156];
        for (int i = 0; i < n; i++) {
            dis.readFully(b[i]);
        }

        AudioFormat audioFormat = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat, AudioSystem.NOT_SPECIFIED);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
        line.addLineListener(event -> Debug.println(event.getType()));
        line.open();
        volume(line, volume);
        line.start();

        Random r = new Random();
for (int m = 0; m < 20; m++) {
//        byte[] x = b1;
//        k = r.nextInt(n);
        k = 3;

        byte[] x = b[k];
Debug.println("patch: " + k / 32 + ", " + k % 32);
Debug.println(x.length + "\n" + StringUtil.getDump(x));

        Context context = Context.getInstance(audioFormat.getSampleRate());

        ResoFilter filter = new ResoFilter(context);
        int[] filterControl = new int[3];
        filterControl[0] = 258847126;
        filterControl[1] = 0;
        filterControl[2] = 0;

        context.lfo.keyDown();
        Note note = new Note(context, x, 50 + (r.nextInt(12)), 100);
        Note.Controllers controllers = new Note.Controllers(0x2000);
        int[] buf = new int[Note.N];
        int[] buf2 = new int[Note.N];

        for (int i = 0; i < nSamples; i += Note.N) {
            if (i >= nSamples * (7. / 8.)) {
                note.keyUp();
            }
            int lfoValue = context.lfo.getSample();
            int lfoDelay = context.lfo.getDelay();
            note.compute(buf, lfoValue, lfoDelay, controllers);
//            note.compute(buf, 0, 0, controllers);
            int[][] bufs = { buf };
            int[][] bufs2 = { buf2 };
            filter.process(bufs, filterControl, filterControl, bufs2);
            for (int j = 0; j < Note.N; j++) {
                buf2[j] >>= 2;
//                buf[j] >>= 2;
            }
            writeData(line, buf2, Note.N);
        }
}
        line.drain();
        line.close();
    }

    static byte[] sample_buf = new byte[128];

    static void writeData(SourceDataLine line, int[] buf, int n) {
        int delta = 0x100;
        for (int i = 0; i < n; i++) {
            int val = buf[i];
            int clip_val = val < -(1 << 24) ? 0x8000 : (val >= (1 << 24) ? 0x7fff : (val + delta) >> 9);
            delta = (delta + val) & 0x1ff;
            sample_buf[i * 2] = (byte) (clip_val & 0xff);
            sample_buf[i * 2 + 1] = (byte) ((clip_val >> 8) & 0xff);
        }
        line.write(sample_buf, 0, n * 2);
    }
}
