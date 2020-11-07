/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.dx7;

import javax.sound.midi.Soundbank;

import com.sun.media.sound.ModelInstrument;
import com.sun.media.sound.ModelPerformer;


/**
 * TestInstrument.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/11/04 umjammer initial version <br>
 */
@SuppressWarnings("restriction")
public class TestInstrument extends ModelInstrument {

    /** */
    protected TestInstrument(Soundbank soundbank) {
        super(soundbank, null, null, null);
    }

    @Override
    public Object getData() {
        return null;
    }

    @Override
    public ModelPerformer[] getPerformers() {
        return null;
    }
}

/* */
