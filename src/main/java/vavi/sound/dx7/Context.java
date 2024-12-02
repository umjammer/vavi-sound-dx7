package vavi.sound.dx7;

import java.util.HashMap;
import java.util.Map;


/**
 * Context.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2022-06-15 nsano initial version <br>
 */
public class Context {
    public float sampleRate;

    // The original DX7 had one single LFO. Later units had an LFO per note.
    public Lfo lfo;
    public FreqLut freqLut;
    public PitchEnv pitchEnv;

    private static final Map<Float, Context> instances = new HashMap<>();

    public static Context getInstance(float sampleRate) {
        if (instances.containsKey(sampleRate)) {
            return instances.get(sampleRate);
        } else {
            Context context = new Context(sampleRate);
            instances.put(sampleRate, context);
            return context;
        }
    }

    private Context(float sampleRate) {
        this.sampleRate = sampleRate;
        freqLut = new FreqLut(sampleRate);
        lfo = new Lfo(sampleRate);
        pitchEnv = new PitchEnv(sampleRate);
    }

    public void setSampleRate(float sampleRate) {
        this.sampleRate = sampleRate;
        Context context = getInstance(sampleRate);
        this.freqLut = context.freqLut;
        this.lfo = context.lfo;
        this.pitchEnv = context.pitchEnv;
    }
}
