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
     * Create a new {@link NotificationDevice} instance.
     * 
     * @param deviceTokenHex
     *            The device's trust token as a string of hexadecimal characters. This token will identify the
     *            destination device.
     */
    public NotificationDevice(String deviceTokenHex) {

        this( deviceTokenHexToBytes( deviceTokenHex ) );
    }

    /**
     * Convert a string of hexadecimal characters into a binary device token.
     */
    private static byte[] deviceTokenHexToBytes(String deviceTokenHex) {

        byte[] deviceToken = new byte[deviceTokenHex.length() / 2];
        for (int i = 0; i < deviceTokenHex.length(); i += 2)
            deviceToken[i / 2] = Integer.decode( "0x" + deviceTokenHex.subSequence( i, i + 2 ) ).byteValue();
        return deviceToken;
    }

    /**
     * @return The token of this {@link NotificationDevice}.
     */
    public byte[] getToken() {

        return token;
    }

    /**
     * This method generates a hexadecimal representation of the device token.
     * 
     * {@inheritDoc}
     */
    @Override
    public String toString() {

        StringBuffer bytes = new StringBuffer( "[d: " );
        for (byte b : token)
            bytes.append( Integer.toHexString( b ) );

        return bytes.append( ']' ).toString();
    }
}
