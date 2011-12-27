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

import com.lyndir.lhunath.opal.system.util.ObjectMeta;
import com.lyndir.lhunath.opal.system.util.ObjectUtils;
import java.io.Serializable;
import java.net.InetSocketAddress;


/**
 * <h2>{@link APSConfig}<br> <sub>[in short] (TODO).</sub></h2>
 * <p/>
 * <p> [description / usage]. </p>
 * <p/>
 * <p> <i>Jun 30, 2009</i> </p>
 *
 * @author lhunath
 */
@ObjectMeta
public class APSConfig implements Serializable {

    private final InetSocketAddress apsAddress;
    private final InetSocketAddress feedBackAddress;
    private final String            encryptionProtocol;

    /**
     * Create a new {@link APSConfig} instance.
     *
     * @param apsHostname        The hostname of the Apple Push Notification server.
     * @param apsPort            The port of the Apple Push Notification server socket.
     * @param feedBackHostname   The hostname of the Apple Push Notification Feedback service.
     * @param feedBackPort       The port of the Apple Push Notification Feedback service socket.
     * @param encryptionProtocol The SSL/TLS protocol to use for transport encryption to these servers.
     */
    public APSConfig(final String apsHostname, final int apsPort, final String feedBackHostname, final int feedBackPort,
                     final String encryptionProtocol) {

        this( new InetSocketAddress( apsHostname, apsPort ), new InetSocketAddress( feedBackHostname, feedBackPort ), encryptionProtocol );
    }

    /**
     * Create a new {@link APSConfig} instance.
     *
     * @param apsAddress         The hostname and port of the Apple Push Notification server socket.
     * @param feedBackAddress    The hostname and port of the Apple Push Notification Feedback service socket.
     * @param encryptionProtocol The SSL/TLS protocol to use for transport encryption to these servers.
     */
    public APSConfig(final InetSocketAddress apsAddress, final InetSocketAddress feedBackAddress, final String encryptionProtocol) {

        this.apsAddress = apsAddress;
        this.feedBackAddress = feedBackAddress;
        this.encryptionProtocol = encryptionProtocol;
    }

    /**
     * @return The hostname and port of the Apple Push Notification server socket.
     */
    public InetSocketAddress getAPSAddress() {

        return apsAddress;
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

    @Override
    public String toString() {

        return ObjectUtils.toString( this );
    }

    @Override
    public int hashCode() {

        return ObjectUtils.hashCode( this );
    }

    @Override
    public boolean equals(final Object obj) {

        return ObjectUtils.equals( this, obj );
    }
}
