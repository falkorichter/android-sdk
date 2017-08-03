package com.sensorberg.sdk.scanner;

import android.support.test.runner.AndroidJUnit4;

import com.sensorberg.sdk.SensorbergTestApplication;
import com.sensorberg.sdk.di.TestComponent;
import com.sensorberg.sdk.model.BeaconId;
import com.sensorberg.sdk.testUtils.NoClock;
import com.sensorberg.sdk.testUtils.TestFileManager;

import org.fest.assertions.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import javax.inject.Inject;

@RunWith(AndroidJUnit4.class)
public class TheBeaconMapShould {

    @Inject
    TestFileManager testFileManager;

    @Inject
    NoClock noClock;

    BeaconMap tested;

    private String pairingId;

    @Before
    public void setUp() throws Exception {
        ((TestComponent) SensorbergTestApplication.getComponent()).inject(this);
        pairingId = UUID.randomUUID().toString();
    }

    @Test
    public void be_able_written_to_a_file() throws IOException {
        File file = getTempFile();
        tested = new BeaconMap(testFileManager, file);

        long firstSize = file.length();

        tested.put(getNewBeaconId(), new EventEntry(System.currentTimeMillis(), 0, ScanEventType.ENTRY.getMask(), pairingId));


        Assertions.assertThat(firstSize).isNotEqualTo(file.length());
    }

    private File getTempFile() throws IOException {
        return File.createTempFile("test" + System.currentTimeMillis(), null);
    }

    @Test
    public void not_fail_if_file_is_corrupted() throws IOException {
        File tempFile = getTempFile();
        testFileManager.write(10000L, tempFile);

        tested = new BeaconMap(testFileManager, tempFile);

        Assertions.assertThat(tested.size()).isEqualTo(0);
    }

    @Test
    public void be_restored_from_a_prviously_serialized_beacon_map() throws IOException {
        File file = getTempFile();
        BeaconMap first = new BeaconMap(testFileManager, file);

        first.put(getNewBeaconId(), new EventEntry(System.currentTimeMillis(), 0, ScanEventType.ENTRY.getMask(), pairingId));

        tested = new BeaconMap(testFileManager, file);

        Assertions.assertThat(tested.size()).isEqualTo(1);
        Assertions.assertThat(tested.get(getNewBeaconId())).isNotNull();
    }

    private BeaconId getNewBeaconId() {
        return new BeaconId(UUID.fromString("D57092AC-DFAA-446C-8EF3-C81AA22815B5"), 1, 1);
    }

    @Test
    public void persist_file_after_filter() throws IOException {
        File file = getTempFile();
        BeaconMap first = new BeaconMap(testFileManager, file);

        first.put(getNewBeaconId(), new EventEntry(noClock.now(), 0, ScanEventType.ENTRY.getMask(), pairingId));

        long originalSize = file.length();

        tested = new BeaconMap(testFileManager, file);
        Assertions.assertThat(tested.size()).isEqualTo(1);

        tested.filter(new BeaconMap.Filter() {
            @Override
            public boolean filter(EventEntry beaconEntry, BeaconId beaconId) {
                return true;
            }
        });

        Assertions.assertThat(file.length()).isLessThan(originalSize);
    }

    @Test
    public void remove_entries_that_match_the_filter() throws IOException {
        tested = new BeaconMap(testFileManager, getTempFile());
        tested.put(getNewBeaconId(), new EventEntry(System.currentTimeMillis(), 0, ScanEventType.ENTRY.getMask(), pairingId));
        Assertions.assertThat(tested.size()).isEqualTo(1);

        tested.filter(new BeaconMap.Filter() {
            @Override
            public boolean filter(EventEntry beaconEntry, BeaconId beaconId) {
                return true;
            }
        });
        Assertions.assertThat(tested.size()).isEqualTo(0);
    }

    @Test
    public void not_fail_if_file_does_not_exist() throws IOException {
        File tempFile = getTempFile();
        tempFile.delete();
        try {
            tested = new BeaconMap(testFileManager, tempFile);
        } catch (Exception e) {
            Assertions.fail(e.getMessage());
        }
    }

    @Test
    public void should_be_readable_right_after_writing() throws Exception {
        File tempFile = getTempFile();
        tested = new BeaconMap(testFileManager, tempFile);
        tested.put(getNewBeaconId(), new EventEntry(noClock.now(), 0, ScanEventType.ENTRY.getMask(), pairingId));

        BeaconMap otherFile = new BeaconMap(testFileManager, tempFile);
        Assertions.assertThat(otherFile).isNotNull();
        Assertions.assertThat(otherFile.size()).isEqualTo(1);
    }
}
