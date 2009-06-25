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
package com.lyndir.lhunath.ipos.notifier;

/**
 * <h2>{@link NotificationDevice}<br>
 * <sub>An iPhone OS device that can receive APNs notifications.</sub></h2>
 * 
 * <p>
 * <i>Jun 23, 2009</i>
 * </p>
 * 
 * @author lhunath
 */
public class NotificationDevice {

    private byte[] token;


    /**
     * Create a new {@link NotificationDevice} instance.
     * 
     * @param token
     *            The device's trust token. This token will identify the destination device.
     */
    public NotificationDevice(byte[] token) {

        this.token = token;
    }

    /**
     * @return The token of this {@link NotificationDevice}.
     */
    public byte[] getToken() {

        return token;
    }
}
