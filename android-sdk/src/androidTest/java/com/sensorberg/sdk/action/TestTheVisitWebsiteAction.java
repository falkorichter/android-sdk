package com.sensorberg.sdk.action;

import org.fest.assertions.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.net.Uri;
import android.os.Parcel;
import android.support.test.runner.AndroidJUnit4;

import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class TestTheVisitWebsiteAction {

    private static final String MESSAGE = "message";
    private static final String TITLE = "title";
    private static final String URL = "http://www.sensorberg.com";
    VisitWebsiteAction tested;

    @Before
    public void setUp() throws Exception {
        tested = new VisitWebsiteAction(UUID.randomUUID(), MESSAGE, TITLE, Uri.parse(URL), null, 0);
    }

    @Test
    public void test_parcelable(){
        Parcel output = Parcel.obtain();

        tested.writeToParcel(output, 0);
        output.setDataPosition(0);

        VisitWebsiteAction copy = VisitWebsiteAction.CREATOR.createFromParcel(output);

        Assertions.assertThat(copy.getUri()).isEqualTo(tested.getUri());
        Assertions.assertThat(copy.getSubject()).isEqualTo(tested.getSubject());
        Assertions.assertThat(copy.getBody()).isEqualTo(tested.getBody());

        Assertions.assertThat(copy.getType()).isEqualTo(ActionType.MESSAGE_WEBSITE);
    }
}
