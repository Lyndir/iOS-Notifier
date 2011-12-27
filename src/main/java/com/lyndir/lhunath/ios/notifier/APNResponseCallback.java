package com.lyndir.lhunath.ios.notifier;

/**
 * <h2>{@link APNResponseCallback}<br> <sub>[in short] (TODO).</sub></h2>
 * <p/>
 * <p> <i>02 22, 2011</i> </p>
 *
 * @author lhunath
 */
public interface APNResponseCallback {

    /**
     * A response was received to an APN message that was recently queued.
     *
     * @param apnResponse The response that was received from the APS.
     */
    void responseReceived(APNResponse apnResponse);
}
