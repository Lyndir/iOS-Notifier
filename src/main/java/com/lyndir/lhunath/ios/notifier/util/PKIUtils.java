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
package com.lyndir.lhunath.ios.notifier.util;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;


/**
 * <h2>{@link PKIUtils}<br> <sub>Public Key Infrastructure Utilities.</sub></h2>
 *
 * <p> <i>Nov 20, 2009</i> </p>
 *
 * @author lhunath
 */
public abstract class PKIUtils {

    /**
     * Creates the factory for {@link KeyManager}s which provide the client identity.
     *
     * <p> Uses private key entries in the given <code>keyStore</code> and unlocks them with the given {@code privateKeyPassword}.
     * </p>
     *
     * @param keyStore           The {@link KeyStore} that provides the private key(s).
     * @param privateKeyPassword The password that protects the private key data.
     *
     * @return A {@link KeyManagerFactory}.
     *
     * @throws NoSuchAlgorithmException  The key's algorithm is not supported by the default key manager's provider.
     * @throws UnrecoverableKeyException The private key could not be accessed from the {@code keyStore}. Perhaps the provided
     *                                   {@code privateKeyPassword} is incorrect.
     * @throws KeyStoreException         The {@code keyStore} has not been properly loaded/initialized or is corrupt.
     */
    public static KeyManagerFactory createKeyManagerFactory(final KeyStore keyStore, final String privateKeyPassword)
            throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance( KeyManagerFactory.getDefaultAlgorithm() );
        keyManagerFactory.init( keyStore, privateKeyPassword.toCharArray() );

        return keyManagerFactory;
    }

    /**
     * Creates the factory for {@link TrustManager}s.
     *
     * <p> The factory will provide simple trust for each trusted certificate in the given {@code keyStore}.<br> No additional optional
     * PKIX validation is performed on the trust path. </p>
     *
     * @param keyStore The {@link KeyStore} that provides the certificates of the trusted Certificate Authorities.
     *
     * @return A {@link TrustManagerFactory}.
     *
     * @throws NoSuchAlgorithmException The default trust algorithm is unavailable (see {@link TrustManagerFactory#getDefaultAlgorithm()})
     * @throws KeyStoreException        The {@code keyStore} has not been properly loaded/initialized or is corrupt.
     */
    public static TrustManagerFactory createTrustManagerFactory(final KeyStore keyStore)
            throws NoSuchAlgorithmException, KeyStoreException {

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() );
        trustManagerFactory.init( keyStore );

        return trustManagerFactory;
    }
}
