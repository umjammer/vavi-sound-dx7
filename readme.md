[![Release](https://jitpack.io/v/umjammer/vavi-sound-dx7.svg)](https://jitpack.io/#umjammer/vavi-sound-dx7)
[![Java CI](https://github.com/umjammer/vavi-sound-dx7/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-sound-dx7/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/vavi-sound-dx7/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/umjammer/vavi-sound-dx7/actions/workflows/codeql-analysis.yml)
![Java](https://img.shields.io/badge/Java-17-b07219)

# vavi-sound-dx7

<img alt="logo" src="https://github.com/umjammer/vavi-sound-dx7/assets/493908/ad201f35-b348-4e3c-8382-6570f56bc9cf" width="160" /> <sub><a href="https://www.yamaha.com/ja/tech-design/design/synapses/id_009">© YAMAHA</a></sub>

DX7 emulated synthesizer. `javax.sound.midi.spi` compatible.

### spec.

 * sysex [43, 00, 09, 20, 00] supported
 * control change 1, 2, 3, 64 supported

## install

 * maven [jitpack](https://jitpack.io/#umjammer/vavi-sound-dx7)
 * copy [`unpacked.bin`](https://github.com/bwhitman/learnfm/blob/f5415157c65b0298dad692e5e332c71644718e28/unpacked.bin) into your class path
 * edit `dx7.properties`([sample](src/test/resources/dx7.properties)), create your instruments set

## Usage

### with [Herr Mueller's DX7](http://www.vstforx.de/index.php/disco-news-blog/29-goodies/92-vstforx-presents-herr-mueller-s-dx7)

without a real dx7 machine, you can play dx7 sound!

![SS 2020-11-10 15 42 30](https://user-images.githubusercontent.com/493908/195994898-beb01841-8a6b-4071-91e1-542b36a4ac4c.jpg)

## References

 * https://github.com/google/music-synthesizer-for-android
 * gervill

## TODO

 * better default instruments set
 * syx, vcs loader (use SoundbankReader)
 * automatic instruments recognizer/classifier
 * ~~volume~~ works! thanks gervill
