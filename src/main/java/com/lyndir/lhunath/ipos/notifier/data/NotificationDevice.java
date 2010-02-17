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
package com.lyndir.lhunath.ipos.notifier.data;

import java.util.Formatter;

import com.lyndir.lhunath.lib.system.logging.Logger;


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

    private static final Logger logger = Logger.get( NotificationDevice.class );

    private byte[]              token;


    /**
     * Create a new {@link NotificationDevice} instance.
     * 
     * @param token
     *            The device's trust token. This token will identify the destination device.
     */
    public NotificationDevice(byte[] token) {

        if (token.length != 32)
            throw logger.err( "Device token should be 32 bytes long; was %d.", //
                    token.length ).toError( IllegalArgumentException.class );

        this.token = token;
    }

    /**
     * Create a new {@link NotificationDevice} instance.
     * 
     * @param hexDeviceToken
     *            The device's trust token as a string of hexadecimal characters. This token will identify the
     *            destination device.
     */
    public NotificationDevice(String hexDeviceToken) {

        this( deviceTokenHexToBytes( hexDeviceToken ) );
    }

    /**
     * Convert a string of hexadecimal characters into a binary device token.
     */
    private static byte[] deviceTokenHexToBytes(String hexDeviceToken) {

        if (hexDeviceToken.length() != 64)
            throw logger.err( "Device token (%s) should be 64 hexadecimal characters long; was %d.", //
                    hexDeviceToken, hexDeviceToken.length() ).toError( IllegalArgumentException.class );

        byte[] deviceToken = new byte[hexDeviceToken.length() / 2];
        for (int i = 0; i < hexDeviceToken.length(); i += 2)
            deviceToken[i / 2] = Integer.valueOf( hexDeviceToken.substring( i, i + 2 ), 16 ).byteValue();

        return deviceToken;
    }

    /**
     * @return The token of this {@link NotificationDevice}.
     */
    public byte[] getToken() {

        return token;
    }

    /**
     * @return The token of this {@link NotificationDevice} as a string of hexadecimal characters.
     */
    public String getTokenHexString() {

        StringBuffer bytes = new StringBuffer();
        Formatter formatter = new Formatter( bytes );

        for (byte b : token)
            formatter.format( "%02X", b );

        return bytes.toString();
    }

    /**
     * This method generates a hexadecimal representation of the device token.
     * 
     * {@inheritDoc}
     */
    @Override
    public String toString() {

        return String.format( "[d: %s]", getTokenHexString() );
    }
}
