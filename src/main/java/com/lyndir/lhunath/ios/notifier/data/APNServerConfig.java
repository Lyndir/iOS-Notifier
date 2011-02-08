/*
 *   Copyright 2009, Maarten Billemont
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.lyndir.lhunath.ios.notifier.data;

import java.net.InetSocketAddress;


/**
 * <h2>{@link APNServerConfig}<br> <sub>[in short] (TODO).</sub></h2>
 *
 * <p> [description / usage]. </p>
 *
 * <p> <i>Jun 30, 2009</i> </p>
 *
 * @author lhunath
 */
public class APNServerConfig {

    /**
     * The default Apple Push Notification Sandbox environment to be used during development and testing.
     */
    public static final APNServerConfig SANDBOX = new APNServerConfig( //
                                                                       "gateway.sandbox.push.apple.com", 2195, //
                                                                       "feedback.sandbox.push.apple.com", 2196, //
                                                                       "TLSv1" );

    /**
     * The default Apple Push Notification Production environment to be used for live applications.
     */
    public static final APNServerConfig PRODUCTION = new APNServerConfig( //
                                                                          "gateway.push.apple.com", 2195, //
                                                                          "feedback.push.apple.com", 2196, //
                                                                          "TLSv1" );

    /**
     * Connect to a dummy environment on the local host for debugging purposes.
     *
     * <p> You'll need a TLSv1 supporting server running on <code>localhost</code> at ports <code>2195</code> (APNs) and <code>2196</code>
     * (Feedback service). You could, for example, use <code>stunnel</code> & <code>netcat</code> for this purpose. </p>
     */
    public static final APNServerConfig LOCAL = new APNServerConfig( //
                                                                     "localhost", 2195, //
                                                                     "localhost", 2196, //
                                                                     "TLSv1" );

    private final InetSocketAddress apnsAddress;
    private final InetSocketAddress feedBackAddress;
    private final String encryptionProtocol;

    /**
     * Create a new {@link APNServerConfig} instance.
     *
     * @param apnsHostname       The hostname of the Apple Push Notification server.
     * @param apnsPort           The port of the Apple Push Notification server socket.
     * @param feedBackHostname   The hostname of the Apple Push Notification Feedback service.
     * @param feedBackPort       The port of the Apple Push Notification Feedback service socket.
     * @param encryptionProtocol The SSL/TLS protocol to use for transport encryption to these servers.
     */
    public APNServerConfig(
            final String apnsHostname, final int apnsPort, final String feedBackHostname, final int feedBackPort, final String encryptionProtocol) {

        this( new InetSocketAddress( apnsHostname, apnsPort ), new InetSocketAddress( feedBackHostname, feedBackPort ),
              encryptionProtocol );
    }

    /**
     * Create a new {@link APNServerConfig} instance.
     *
     * @param apnsAddress        The hostname and port of the Apple Push Notification server socket.
     * @param feedBackAddress    The hostname and port of the Apple Push Notification Feedback service socket.
     * @param encryptionProtocol The SSL/TLS protocol to use for transport encryption to these servers.
     */
    public APNServerConfig(final InetSocketAddress apnsAddress, final InetSocketAddress feedBackAddress, final String encryptionProtocol) {

        this.apnsAddress = apnsAddress;
        this.feedBackAddress = feedBackAddress;
        this.encryptionProtocol = encryptionProtocol;
    }

    /**
     * @return The hostname and port of the Apple Push Notification server socket.
     */
    public InetSocketAddress getApnsAddress() {

        return apnsAddress;
    }

    /**
     * @return The hostname and port of the Apple Push Notification Feedback service socket.
     */
    public InetSocketAddress getFeedBackAddress() {

        return feedBackAddress;
    }

    /**
     * @return The SSL/TLS protocol to use for transport encryption to these servers.
     */
    public String getEncryptionProtocol() {

        return encryptionProtocol;
    }
}
