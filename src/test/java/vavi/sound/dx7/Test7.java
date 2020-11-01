/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.dx7;

import java.io.DataInputStream;
import java.io.File;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

import org.junit.jupiter.api.Test;

import vavi.sound.midi.dx7.Dx7Synthesizer;
import vavi.util.Debug;
import vavi.util.StringUtil;


/**
 * Test7.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/30 umjammer initial version <br>
 */
public class Test7 {

    @Test
    void test() throws Exception {
        Synthesizer synthesizer = new Dx7Synthesizer();
        synthesizer.open();
Debug.println("synthesizer: " + synthesizer);

        Sequencer sequencer = MidiSystem.getSequencer(false);
        sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
        sequencer.open();
Debug.println("sequencer: " + sequencer);

//        String filename = "1/title-screen.mid";
        String filename = "1/overworld.mid";
//        String filename = "1/m0057_01.mid";
//        String filename = "1/ac4br_gm.MID";
        File file = new File(System.getProperty("user.home"), "/Music/midi/" + filename);
        Sequence seq = MidiSystem.getSequence(file);

        CountDownLatch countDownLatch = new CountDownLatch(1);
        MetaEventListener mel = new MetaEventListener() {
            public void meta(MetaMessage meta) {
System.err.println("META: " + meta.getType());
                if (meta.getType() == 47) {
                    countDownLatch.countDown();
                }
            }
        };
        sequencer.setSequence(seq);
        sequencer.addMetaEventListener(mel);
System.err.println("START");
        sequencer.start();
        countDownLatch.await();
System.err.println("END");
        sequencer.removeMetaEventListener(mel);
        sequencer.close();

        synthesizer.close();
    }

    /**
     *
     * @param args
     */
    public static void main(String[] args) throws Exception {
        final int n_samples = 20 * 1024;
        double sample_rate = 44100.0;
        Freqlut.init(sample_rate);
        PitchEnv.init(sample_rate);

        int k = 1004;

//        Voice[] voices = SavenLoad.loadSetOfVoices("tmp/Original Yamaha/DX7 ROM1/ROM1A.syx").voice;
//Debug.println("patchs: " + voices.length);
//        byte[] b1 = voices[k].allToByte();

        DataInputStream dis = new DataInputStream(Test7.class.getResourceAsStream("/unpacked.bin"));
        int n = dis.available() / 156;
Debug.println("patchs: " + n);
        byte[][] b = new byte[n][156];
        for (int i = 0; i < n; i++) {
            dis.readFully(b[i]);
        }

        AudioFormat audioFormat = new AudioFormat((float) sample_rate, 16, 1, true, false);
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat, AudioSystem.NOT_SPECIFIED);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
//        CountDownLatch cdl = new CountDownLatch(1);
        line.addLineListener(event -> { Debug.println(event.getType()); });
        line.open();
FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
double gain = .2d; // number between 0 and 1 (loudest)
float dB = (float) (Math.log(gain) / Math.log(10.0) * 20.0);
gainControl.setValue(dB);
        line.start();

        Random r = new Random();
for (int m = 0; m < 100; m++) {
//        byte[] x = b1;
        k = r.nextInt(n);

        byte[] x = b[k];
Debug.println("patch: " + k);
Debug.println(x.length + "\n" + StringUtil.getDump(x));

        Note note = new Note(x, 50 + (k % 12), 100);
        Note.Controllers controllers = new Note.Controllers(0x2000);
        int[] buf = new int[Note.N];

        for (int i = 0; i < n_samples; i += Note.N) {
            if (i >= n_samples * (7. / 8.)) {
                note.keyup();
            }
            note.compute(buf, 0, 0, controllers);
            for (int j = 0; j < Note.N; j++) {
                buf[j] >>= 2;
            }
            write_data(line, buf, Note.N);
        }
}
        line.drain();
        line.close();
    }

    static byte[] sample_buf = new byte[128];

    static void write_data(SourceDataLine line, final int[] buf, int n) {
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

/* */
