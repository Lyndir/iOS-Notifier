package com.lyndir.lhunath.ios.notifier.data;

import com.google.gson.annotations.Expose;
import com.lyndir.lhunath.opal.system.util.ObjectMeta;
import com.lyndir.lhunath.opal.system.util.ObjectUtils;


/**
 * <h2>{@link Payload}<br> <sub>[in short] (TODO).</sub></h2>
 * <p/>
 * <p> <i>02 22, 2011</i> </p>
 *
 * @author lhunath
 */
@ObjectMeta
public class Payload {

    @Expose
    private final APSPayload aps;

    public Payload(final APSPayload aps) {

        this.aps = aps;
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
