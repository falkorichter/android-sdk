package com.sensorberg.sdk.action;

import android.net.Uri;
import android.os.Parcel;

import java.util.UUID;

import lombok.Getter;
import lombok.ToString;

@ToString
public class InAppAction extends Action implements android.os.Parcelable {

    /**
     * -- GETTER --
     * the subject of this action as entered on the web interface. This field is optional!
     *
     * @return the subject or null
     */
    @Getter private final String subject;

    /**
     * -- GETTER --
     * the body of the action as entered in the web interface. This field is optional!
     *
     * @return the body as a string or null
     */
    @Getter private final String body;

    /**
     * -- GETTER --
     * the URL of the website as entered in the web interface. This field is mandatory.
     *
     * @return the url
     */
    @Getter private final Uri uri;

    public InAppAction(UUID uuid, String subject, String body, String payload, Uri uri, long delayTime, String instanceUuid) {
        super(ActionType.MESSAGE_IN_APP, delayTime, uuid, payload, instanceUuid);
        this.subject = subject;
        this.body = body;
        this.uri = uri;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(this.subject);
        dest.writeString(this.body);
        dest.writeParcelable(this.uri, 0);
    }

    private InAppAction(Parcel in) {
        super(in);
        this.subject = in.readString();
        this.body = in.readString();
        this.uri = in.readParcelable(Uri.class.getClassLoader());
    }

    public static final Creator<InAppAction> CREATOR = new Creator<InAppAction>() {
        public InAppAction createFromParcel(Parcel source) {
            return new InAppAction(source);
        }

        public InAppAction[] newArray(int size) {
            return new InAppAction[size];
        }
    };
}
