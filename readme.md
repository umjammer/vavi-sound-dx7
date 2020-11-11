[![Release](https://jitpack.io/v/umjammer/vavi-sound-dx7.svg)](https://jitpack.io/#umjammer/vavi-sound-dx7) [![Java CI with Maven](https://github.com/umjammer/vavi-sound-dx7/workflows/Java%20CI%20with%20Maven/badge.svg)](https://github.com/umjammer/vavi-sound-dx7/actions)

# vavi-sound-dx7

DX7 emulated synthesizer. `javax.sound.midi.spi` compatible.

## spec.

 * sysex [43, 00, 09, 20, 00] supported
 * control change 1, 2, 3, 64 supported

## install

 * maven repo: [jitpack](https://jitpack.io/#umjammer/vavi-sound-dx7)
 * copy [`unpacked.bin`](https://github.com/bwhitman/learnfm/blob/f5415157c65b0298dad692e5e332c71644718e28/unpacked.bin) into your class path
 * edit `dx7.properties`, create your instruments set

## with [Herr Mueller's DX7](http://www.vstforx.de/index.php/disco-news-blog/29-goodies/92-vstforx-presents-herr-mueller-s-dx7)

without the real dx7 machine, you can play dx7 sound!

![herrMueller](https://lh3.googleusercontent.com/pw/ACtC-3erXg2jLuvfN_0EvFXnGhCRSRaf5D75KJZfOtmtUk8NuZGNkLOm87vipTViapFHoixBgOuMFQ4WTKMZAmfaMeU-wLlZol_udw5XMDLNDj_O9i-5Vl7U4mG-O8r0hJijXE7liyY2RjSXLVLAir0dyg2P=w640-h225-no?authuser=0)

## thanks

 * https://github.com/google/music-synthesizer-for-android
 * gervill

## TODO

 * better default instruments set
 * syx, vcs loader (use SoundbankReader)
 * automatic instruments recognizer/classifier