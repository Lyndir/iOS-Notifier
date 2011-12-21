package com.lyndir.lhunath.ios.notifier;

/**
 * <i>12 20, 2011</i>
 *
 * @author lhunath
 */
public class APNException extends Throwable {

    public APNException(final Throwable cause) {

        super( cause );
    }

    public APNException(final String reason) {

        super( reason );
    }
}
