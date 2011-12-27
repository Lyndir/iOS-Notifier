package com.lyndir.lhunath.ios.notifier;

import com.lyndir.lhunath.opal.system.util.ObjectMeta;
import com.lyndir.lhunath.opal.system.util.ObjectUtils;


/**
 * <h2>{@link APNResponse}<br> <sub>[in short] (TODO).</sub></h2>
 * <p/>
 * <p> <i>02 22, 2011</i> </p>
 *
 * @author lhunath
 */
@ObjectMeta
public class APNResponse {

    public enum Status {
        PROCESSING_ERROR,
        MISSING_DEVICE_TOKEN,
        MISSING_TOPIC,
        MISSING_PAYLOAD,
        INVALID_TOKEN_SIZE,
        INVALID_TOPIC_SIZE,
        INVALID_PAYLOAD_SIZE,
        INVALID_TOKEN,
        UNKNOWN,
        SUCCESS
    }


    private final Status status;
    private final int    identifier;

    public APNResponse(final byte status, final int identifier) {

        this.identifier = identifier;
        switch (status) {
            case (byte) 0:
                this.status = Status.SUCCESS;
                break;
            case (byte) 1:
                this.status = Status.PROCESSING_ERROR;
                break;
            case (byte) 2:
                this.status = Status.MISSING_DEVICE_TOKEN;
                break;
            case (byte) 3:
                this.status = Status.MISSING_TOPIC;
                break;
            case (byte) 4:
                this.status = Status.MISSING_PAYLOAD;
                break;
            case (byte) 5:
                this.status = Status.INVALID_TOKEN_SIZE;
                break;
            case (byte) 6:
                this.status = Status.INVALID_TOPIC_SIZE;
                break;
            case (byte) 7:
                this.status = Status.INVALID_PAYLOAD_SIZE;
                break;
            case (byte) 8:
                this.status = Status.INVALID_TOKEN;
                break;
            case (byte) 255:
                this.status = Status.UNKNOWN;
                break;
            default:
                throw new IllegalArgumentException( "Did not understand status code: " + Byte.valueOf( status ).intValue() );
        }
    }

    public Status getStatus() {

        return status;
    }

    public int getIdentifier() {

        return identifier;
    }

    @Override
    public int hashCode() {

        return ObjectUtils.hashCode( this );
    }

    @Override
    public boolean equals(final Object obj) {

        return ObjectUtils.equals( this, obj );
    }

    @Override
    public String toString() {

        return ObjectUtils.toString( this );
    }
}
