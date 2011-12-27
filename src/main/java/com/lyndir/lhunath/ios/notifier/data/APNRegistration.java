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

import static com.google.common.base.Preconditions.*;

import com.lyndir.lhunath.opal.system.CodeUtils;
import java.util.Arrays;


/**
 * <h2>{@link APNRegistration}<br> <sub>An iOS device registration with the APS.</sub></h2>
 * <p/>
 * <p>An APN registration identifies both a user's device and the application that registered it.</p>
 * <p> <i>Jun 23, 2009</i> </p>
 *
 * @author lhunath
 */
public class APNRegistration {

    private byte[] token;

    /**
     * Create a new {@link APNRegistration} instance.
     *
     * @param token The APN registration token. This token will identify the destination device and application to the APS.
     */
    public APNRegistration(final byte[] token) {

        checkArgument( token.length == 32, "APN registration token should be 32 bytes long; was %s.", token.length );

        this.token = token;
    }

    /**
     * Create a new {@link APNRegistration} instance.
     *
     * @param hexToken The APN registration token as a string of hexadecimal characters. This token will identify the destination device
     *                 and application to the APS.
     */
    public APNRegistration(final String hexToken) {

        this( CodeUtils.unhex( hexToken ) );
    }

    /**
     * @return The token of this {@link APNRegistration}.
     */
    public byte[] getToken() {

        return token;
    }

    /**
     * @return The token of this {@link APNRegistration} as a string of hexadecimal characters.
     */
    public String getHexToken() {

        return CodeUtils.hex( getToken() );
    }

    /**
     * This method generates a hexadecimal representation of the device token.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public String toString() {

        return String.format( "{APNRegistration: %s}", getHexToken() );
    }

    @Override
    public int hashCode() {

        return Arrays.hashCode( token );
    }

    @Override
    public boolean equals(final Object obj) {

        return obj == this || (obj instanceof APNRegistration) && Arrays.equals( ((APNRegistration) obj).token, token );
    }
}
