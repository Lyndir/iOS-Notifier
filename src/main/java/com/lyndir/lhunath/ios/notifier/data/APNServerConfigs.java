package com.lyndir.lhunath.ios.notifier.data;

/**
 * <i>04 21, 2011</i>
 *
 * @author lhunath
 */
public enum APNServerConfigs {

    /**
     * The default Apple Push Notification Sandbox environment to be used during development and testing.
     */
    SANDBOX( new APNServerConfig( //
            "gateway.sandbox.push.apple.com", 2195, //
            "feedback.sandbox.push.apple.com", 2196, //
            "TLSv1" ) ),

    /**
     * The default Apple Push Notification Production environment to be used for live applications.
     */
    PRODUCTION( new APNServerConfig( //
            "gateway.push.apple.com", 2195, //
            "feedback.push.apple.com", 2196, //
            "TLSv1" ) ),

    /**
     * Connect to a dummy environment on the local host for debugging purposes.
     * <p/>
     * <p> You'll need a TLSv1 supporting server running on <code>localhost</code> at ports <code>2195</code> (APNs) and <code>2196</code>
     * (Feedback service). You could, for example, use <code>stunnel</code> & <code>netcat</code> for this purpose. </p>
     */
    LOCAL( new APNServerConfig( //
            "localhost", 2195, //
            "localhost", 2196, //
            "TLSv1" ) );

    private final APNServerConfig apnServerConfig;

    APNServerConfigs(APNServerConfig apnServerConfig) {

        this.apnServerConfig = apnServerConfig;
    }

    public APNServerConfig get() {

        return apnServerConfig;
    }
}
