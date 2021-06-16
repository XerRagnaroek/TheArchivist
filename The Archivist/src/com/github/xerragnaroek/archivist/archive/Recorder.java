package com.github.xerragnaroek.archivist.archive;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.xerragnaroek.archivist.Core;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;

/**
 * 
 */
public class Recorder implements AudioReceiveHandler {

	private static DateTimeFormatter timeStampFormat = DateTimeFormatter.ofPattern("HH-mm-ss-SSS");
	private AudioManager am;
	private VoiceChannel vc;
	private Archive archive;
	private ZonedDateTime start;
	private Path file;
	private final Logger log;
	private Saver saver;

	public Recorder(Archive arch, AudioManager am, VoiceChannel vc) {
		archive = arch;
		this.am = am;
		this.vc = vc;
		log = LoggerFactory.getLogger(Recorder.class + "#" + vc.getIdLong());
		init();
	}

	private void init() {
		start = ZonedDateTime.now(Core.GB);
		file = Path.of(archive.voiceChannelPath.get(vc.getIdLong()).toString(), "/" + timeStampFormat.format(start) + "/");
	}

	public void startRecording() {
		log.debug("Starting recording");
		try {
			if (!Files.exists(file)) {
				Files.createDirectory(file);
			}
			saver = new Saver(file);
		} catch (IOException e) {
			log.error("", e);
		}
		am.setReceivingHandler(this);
		am.openAudioConnection(vc);
		saver.start();
	}

	public void stopRecording() {
		log.debug("Stopping recording");
		am.closeAudioConnection();
		am.setReceivingHandler(null);
		Scribes.freeScribe(am.getJDA());
		saver.stop();
		// while (!saver.isDone()) {}
		// log.debug("Saver is done!");
		// Path renamed = Path.of(file.toString().replace(".pcm", " - " +
		// timeStampFormat.format(ZonedDateTime.now(Core.GB)) + ".pcm"));
		// Files.move(file, renamed);
		// log.debug("{} has been renamed to {}", file.getFileName(), renamed.getFileName());
	}

	@Override
	public boolean canReceiveCombined() {
		return true;
	}

	@Override
	public void handleCombinedAudio(CombinedAudio combinedAudio) {
		/*
		 * try {
		 * setFile();
		 * Files.write(file, combinedAudio.getAudioData(1), StandardOpenOption.CREATE_NEW);
		 * log.debug("Wrote 20ms of audio to {}", file.getFileName());
		 * } catch (IOException e) {
		 * log.error("", e);
		 * }
		 */
		saver.addData(combinedAudio.getAudioData(1));
	}
}

class Saver {
	private List<byte[]> data = new LinkedList<>();
	private boolean run = true;
	private final Logger log = LoggerFactory.getLogger(Saver.class);
	private Future<?> f;
	private int runningFileNum = 1;
	private Path base;

	Saver(Path f) throws IOException {
		base = f;
	}

	void addData(byte[] d) {
		if (run) {
			synchronized (data) {
				data.add(d);
			}
		}
	}

	void start() {
		f = Core.EXEC.submit(() -> run());
	}

	private void run() {
		while (run) {
			saveQueue();
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				log.error("", e);
			}
		}
		saveQueue();
	}

	private void saveQueue() {
		if (data.size() > 0) {
			log.debug("Saving {} parts", data.size());
			List<byte[]> tmp;
			synchronized (data) {
				tmp = data;
				data = new LinkedList<>();
			}
			int size = tmp.stream().collect(Collectors.summingInt(b -> b.length));
			byte[] data = new byte[size];
			int i = 0;
			for (byte[] b : tmp) {
				for (int n = 0; n < b.length; n++) {
					data[i++] = b[n];
				}
			}
			try {
				saveToWavFile(data);
				log.debug("Saved {} kilobytes of audio", size / 1000);
			} catch (IOException e) {
				log.error("", e);
			}
		}
	}

	private void saveToWavFile(byte[] data) throws IOException {
		File out = Path.of(base.toString(), (runningFileNum++) + ".wav").toFile();
		if (!out.exists()) {
			out.createNewFile();
		}
		AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(data), AudioReceiveHandler.OUTPUT_FORMAT, data.length), AudioFileFormat.Type.WAVE, out);
	}

	void stop() {
		run = false;
	}

	boolean isDone() {
		return f.isDone();
	}

}
