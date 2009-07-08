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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.UnrecoverableKeyException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import net.sf.json.JSONObject;
import net.sf.json.util.JSONBuilder;
import net.sf.json.util.JSONStringer;

import com.lyndir.lhunath.lib.network.Network;
import com.lyndir.lhunath.lib.network.NetworkConnectionStateListener;
import com.lyndir.lhunath.lib.network.NetworkDataListener;
import com.lyndir.lhunath.lib.system.logging.Logger;


/**
 * <h2>{@link APNClient}<br>
 * <sub>An APNs client for queueing and dispatching notifications to the APNs.</sub></h2>
 * 
 * <p>
 * TODO
 * </p>
 * 
 * <p>
 * <i>Jun 18, 2009</i>
 * </p>
 * 
 * @author lhunath
 */
public class APNClient implements NetworkConnectionStateListener, NetworkDataListener {

    private static final Logger                 logger          = Logger.get( APNClient.class );

    private Set<NetworkConnectionStateListener> stateListeners  = new HashSet<NetworkConnectionStateListener>();
    private APNQueue                            apnQueue        = new APNQueue( this );

    private Charset                             payloadEncoding = Charset.forName( "UTF-8" );
    private ByteOrder                           byteOrder       = ByteOrder.BIG_ENDIAN;
    private short                               maxPayloadSize  = 256;

    private KeyManagerFactory                   keyManagerFactory;
    private TrustManagerFactory                 trustManagerFactory;
    private APNServerConfig                     serverConfig;

    private Network                             network;
    private SocketChannel                       apnsChannel;
    private SocketChannel                       feedbackChannel;
    private ByteBuffer                          feedbackBuffer;

    Map<NotificationDevice, Date>               feedbackDevices;
    UninstalledDevicesCallback                  feedbackCallback;


    /**
     * Create a new {@link APNClient} instance by setting up the PKIX identity and trust to reasonable defaults from the
     * given parameters.
     * 
     * <p>
     * This constructor uses the default APNs at
     * <code>{@value #DEFAULT_SBOX_APNS_HOSTNAME}:{@value #DEFAULT_SBOX_APNS_TCP_PORT}</code> and SSL/TLS protocol
     * <code>{@value #DEFAULT_SBOX_APNS_PROTOCOL}</code>.
     * </p>
     * 
     * @param keyStore
     *            The keystore which provides all required SSL keys and certificates.
     * @param privateKeyPassword
     *            The password which protects the required <code>keyStore</code>'s private key.
     * @param serverConfig
     *            The {@link APNServerConfig} that determines the host configuration of the Apple Push Notification
     *            server and Feedback service.
     * 
     * @throws UnrecoverableKeyException
     *             The private key could not be accessed from the <code>keyStore</code>. Perhaps the provided
     *             <code>privateKeyPassword</code> is incorrect.
     * @throws NoSuchAlgorithmException
     *             The <code>keyStore</code> provider does not support the necessary algorithms.
     * @throws KeyStoreException
     *             The <code>keyStore</code> has not been properly loaded/initialized or is corrupt.
     */
    public APNClient(KeyStore keyStore, String privateKeyPassword, APNServerConfig serverConfig)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {

        this( createKeyManagerFactory( keyStore, privateKeyPassword ), createTrustManagerFactory( keyStore ),
              serverConfig );
    }

    /**
     * Create a new {@link APNClient} instance using a custom configured {@link KeyManagerFactory} and
     * {@link TrustManagerFactory} to provide PKIX identity and trust.
     * 
     * <p>
     * This constructor uses the default APNs at
     * <code>{@value #DEFAULT_SBOX_APNS_HOSTNAME}:{@value #DEFAULT_SBOX_APNS_TCP_PORT}</code> and SSL/TLS protocol
     * <code>{@value #DEFAULT_SBOX_APNS_PROTOCOL}</code>.
     * </p>
     * 
     * @param sslProvider
     *            The {@link Provider} which provides the Java Security services.
     * @param trustManagerFactory
     *            The factory that will create the SSL context's {@link TrustManager}s.
     * @param keyManagerFactory
     *            The factory that will create the SSL context's {@link KeyManager}s.
     * @param serverConfig
     *            The {@link APNServerConfig} that determines the host configuration of the Apple Push Notification
     *            server and Feedback service.
     * 
     * @throws KeyManagementException
     *             The SSL context could not be initialized from the provided private keys.
     * @throws NoSuchAlgorithmException
     *             The <code>keyStore</code> provider does not support the necessary algorithms.
     */
    public APNClient(KeyManagerFactory keyManagerFactory, TrustManagerFactory trustManagerFactory,
            APNServerConfig serverConfig) {

        this.keyManagerFactory = keyManagerFactory;
        this.trustManagerFactory = trustManagerFactory;
        this.serverConfig = serverConfig;

        int uninstalledDeviceRecordLength = 0;
        // UTC UNIX Timestamp
        uninstalledDeviceRecordLength += Integer.SIZE / Byte.SIZE;
        // Device Token length
        uninstalledDeviceRecordLength += Short.SIZE / Byte.SIZE;
        // Device Token
        uninstalledDeviceRecordLength += 32;

        feedbackBuffer = ByteBuffer.allocate( uninstalledDeviceRecordLength );
        feedbackDevices = Collections.synchronizedMap( new HashMap<NotificationDevice, Date>() );

        network = new Network();
        network.registerConnectionStateListener( this );
        network.registerDataListener( this );
        network.startThread();
    }

    public synchronized void start() {

        network.bringUp();
        apnQueue.start();
    }

    /**
     * Creates the factory for {@link KeyManager}s which provide the client identity.
     * 
     * <p>
     * Uses private key entries in the given <code>keyStore</code> and unlocks them with the given
     * <code>privateKeyPassword</code>.
     * </p>
     * 
     * @param keyStore
     *            The {@link KeyStore} that provides the private key(s).
     * @param privateKeyPassword
     *            The password that protects the private key data.
     * 
     * @return A {@link KeyManagerFactory}.
     * 
     * @throws NoSuchAlgorithmException
     *             The key's algorithm is not supported by the default key manager's provider.
     * @throws InvalidAlgorithmParameterException
     *             Couldn't load the private key identity into the key manager. Perhaps the <code>keyStore</code> is
     *             uninitialized or corrupted, or perhaps the <code>privateKeyPassword</code> is invalid.
     * @throws UnrecoverableKeyException
     *             The private key could not be accessed from the <code>keyStore</code>. Perhaps the provided
     *             <code>privateKeyPassword</code> is incorrect.
     * @throws KeyStoreException
     *             The <code>keyStore</code> has not been properly loaded/initialized or is corrupt.
     */
    protected static KeyManagerFactory createKeyManagerFactory(KeyStore keyStore, String privateKeyPassword)
            throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance( KeyManagerFactory.getDefaultAlgorithm() );
        keyManagerFactory.init( keyStore, privateKeyPassword.toCharArray() );

        return keyManagerFactory;
    }

    /**
     * Creates the factory for {@link TrustManager}s.
     * 
     * <p>
     * The factory will provide simple trust for each trusted certificate in the given <code>keyStore</code>.<br>
     * No additional optional PKIX validation is performed on the trust path.
     * </p>
     * 
     * @param keyStore
     *            The {@link KeyStore} that provides the certificates of the trusted Certificate Authorities.
     * 
     * @return A {@link TrustManagerFactory}.
     * 
     * @throws NoSuchAlgorithmException
     *             The default trust algorithm is unavailable (see {@link TrustManagerFactory#getDefaultAlgorithm()})
     * @throws KeyStoreException
     *             The <code>keyStore</code> has not been properly loaded/initialized or is corrupt.
     */
    protected static TrustManagerFactory createTrustManagerFactory(KeyStore keyStore)
            throws NoSuchAlgorithmException, KeyStoreException {

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() );
        trustManagerFactory.init( keyStore );

        return trustManagerFactory;
    }

    /**
     * Creates an SSL engine.
     * 
     * @param serverAddress
     *            The hostname and port of the remote server socket to create an SSL engine for.
     * @param protocol
     *            The SSL/TLS protocol to use for secure transport encryption.
     *            <p>
     *            Valid values depend on what is supported by the <code>sslProvider</code>, but generally speaking there
     *            is: <code>SSL, SSLv2, SSLv3, TLS, TLSv1, TLSv1.1, SSLv2Hello</code>.
     *            </p>
     * @param trustManagerFactory
     *            The factory that will create the SSL context's {@link TrustManager}s.
     * @param keyManagerFactory
     *            The factory that will create the SSL context's {@link KeyManager}s.
     * 
     * @return An {@link SSLEngine}.
     * 
     * @throws NoSuchAlgorithmException
     *             The <code>keyStore</code> provider does not support the necessary algorithms.
     * @throws KeyManagementException
     *             The SSL context could not be initialized using the available private keys.
     * 
     * @see http://java.sun.com/javase/6/docs/technotes/guides/security/StandardNames.html#jssenames
     */
    private static SSLEngine createSSLEngine(InetSocketAddress serverAddress, String protocol,
                                             KeyManagerFactory keyManagerFactory,
                                             TrustManagerFactory trustManagerFactory)
            throws NoSuchAlgorithmException, KeyManagementException {

        // Set up an SSL context from identity and trust configurations.
        SSLContext sslContext = SSLContext.getInstance( protocol );
        sslContext.init( keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null );

        return sslContext.createSSLEngine( serverAddress.getHostName(), serverAddress.getPort() );
    }

    /**
     * Queue a notification to be sent to the APNs next time {@link #sendQueuedNotifications()} is called.
     * 
     * <p>
     * This is a convenience variant of the
     * {@link #queueNotification(NotificationDevice, JSONObject, NotificationPayLoad...)} method that does not pass any
     * custom data.
     * </p>
     * 
     * @param device
     *            The device that is the notification's destination.
     * @param notificationPayLoads
     *            The notification payloads. These payloads describe the actual events the notification should trigger
     *            on the device. You can specify multiple notification events, and must specify at least one.
     */
    public void queueNotification(NotificationDevice device, NotificationPayLoad... notificationPayLoads) {

        queueNotification( device, null, notificationPayLoads );
    }

    /**
     * Queue a notification to be sent to the APNs next time {@link #sendQueuedNotifications()} is called.
     * 
     * @param device
     *            The device that is the notification's destination.
     * @param customData
     *            Any optional custom data you want to pass to the application. Remember that the total payload size
     *            (this and the <code>notificationPayLoads</code> combined) is limited to <code>256 bytes</code>, so be
     *            modest. You can specify <code>null</code> here to omit any custom data.
     * @param notificationPayLoads
     *            The notification payloads. These payloads describe the actual events the notification should trigger
     *            on the device. You can specify multiple notification events, and must specify at least one.
     * 
     * @return <code>false</code>: The notification queue is full. Wait for it to get emptied by the {@link APNQueue}
     *         thread before trying again.
     */
    public boolean queueNotification(NotificationDevice device, JSONObject customData,
                                     NotificationPayLoad... notificationPayLoads) {

        // Check Device Token length.
        int tokenLengthInt = device.getToken().length;
        if (tokenLengthInt > Short.MAX_VALUE) {
            logger.err( "Device Token can't be longer than %d bytes; token '%s' is %d bytes.", //
                        Short.MAX_VALUE, device.getToken(), tokenLengthInt );
            throw logger.toError( IllegalArgumentException.class );
        }
        short tokenLength = (short) tokenLengthInt;

        // Check Notification and Custom Payload.
        if (notificationPayLoads == null || notificationPayLoads.length == 0) {
            logger.err( "Must specify at least one notification payload for a notification message." );
            throw logger.toError( IllegalArgumentException.class );
        }

        // Construct the Payload.
        JSONBuilder payLoadStringer = new JSONStringer().object();
        payLoadStringer.key( "aps" );
        if (notificationPayLoads.length == 1)
            payLoadStringer.value( notificationPayLoads[0] );
        else {
            payLoadStringer.array();
            for (NotificationPayLoad notificationPayLoad : notificationPayLoads)
                payLoadStringer.value( notificationPayLoad );
            payLoadStringer.endArray();
        }
        if (customData != null) {
            @SuppressWarnings("unchecked")
            Set<Map.Entry<String, Object>> customDataEntrySet = customData.entrySet();
            for (Map.Entry<String, Object> entry : customDataEntrySet) {
                payLoadStringer.key( entry.getKey() );
                payLoadStringer.value( entry.getValue() );
            }
        }
        String payLoad = payLoadStringer.endObject().toString();

        // Check Payload length.
        ByteBuffer payLoadData = getPayloadEncoding().encode( payLoad );
        int payLoadLengthInt = payLoadData.remaining();
        if (payLoadLengthInt > maxPayloadSize || payLoadLengthInt > Short.MAX_VALUE) {
            logger.err( "Payload can't be larger than %d bytes; passed payload was %d bytes.", //
                        Math.min( maxPayloadSize, Short.MAX_VALUE ), payLoadLengthInt );
            throw logger.toError( IllegalArgumentException.class );
        }
        short payLoadLength = (short) payLoadLengthInt;

        // The interface command.
        byte command = (byte) 0;

        logger.inf( "Sending notification %s to device %s", payLoad, device );

        // Allocate the interface byte buffer.
        int notificationByteSize = getInterfaceByteSize( tokenLength, payLoadLength );
        ByteBuffer notificationData = ByteBuffer.allocate( notificationByteSize ).order( byteOrder );

        // Add Interface Command
        notificationData.put( command );
        // Add Interface Device Token length
        notificationData.putShort( tokenLength );
        // Add Interface Device Token
        notificationData.put( device.getToken() );
        // Add Interface Payload length
        notificationData.putShort( payLoadLength );
        // Add Interface Payload
        notificationData.put( payLoadData );
        payLoadData.flip();

        // All done with adding stuff to the notification data buffer. Flip & queue it.
        notificationData.flip();
        boolean wasQueued = apnQueue.offer( notificationData );

        if (wasQueued) {
            logger.inf( "Queued payload: %s", payloadEncoding.decode( payLoadData ) );
            payLoadData.flip();
        } else {
            logger.wrn( "Queue full, not queueing payload: %s", payloadEncoding.decode( payLoadData ) );
            payLoadData.flip();

        }

        return wasQueued;
    }

    /**
     * Calculate the byte size of the interface for sending a certain notification.
     * 
     * @param tokenLength
     *            The byte size of the {@link NotificationDevice}'s token that identifies the notification's
     *            destination.
     * @param payLoadLength
     *            The byte size of the {@link NotificationPayLoad} when serialized and encoded with the character set as
     *            defined by {@link #getPayloadEncoding()}.
     * 
     * @return The byte size of the notification interface that will be sent to the APNs.
     */
    private int getInterfaceByteSize(short tokenLength, short payLoadLength) {

        int interfaceByteSize = 0;

        // Command: 1 byte.
        interfaceByteSize += 1 * Byte.SIZE / Byte.SIZE;
        // Token length: 1 short.
        interfaceByteSize += 1 * Short.SIZE / Byte.SIZE;
        // Token
        interfaceByteSize += tokenLength;
        // Payload length: 1 short.
        interfaceByteSize += 1 * Short.SIZE / Byte.SIZE;
        // Payload
        interfaceByteSize += payLoadLength;

        return interfaceByteSize;
    }

    /**
     * Dispatch the given notification interface to the APNs.
     * 
     * <p>
     * This operation will establish a connection to the configured APNs if one is not active already. Note that the
     * connection will not be terminated automatically. You are responsible for calling {@link #closeAPNs()} when you
     * determine you won't be dispatching any more messages soon.
     * </p>
     * 
     * @param notificationInterface
     *            The {@link ByteBuffer} containing the raw interface of the notification data to send.
     * 
     * @throws IOException
     *             If the system failed to initiate a connection to the APNs.
     * @throws NoSuchAlgorithmException
     *             The <code>keyStore</code> provider does not support the necessary algorithms.
     * @throws KeyManagementException
     *             The SSL context could not be initialized using the available private keys.
     */
    public void dispatch(ByteBuffer notificationInterface)
            throws IOException, KeyManagementException, NoSuchAlgorithmException {

        if (apnsChannel == null || !apnsChannel.isOpen()) {
            SSLEngine sslEngine = createSSLEngine( serverConfig.getApnsAddress(), serverConfig.getEncryptionProtocol(), //
                                                   keyManagerFactory, trustManagerFactory );
            apnsChannel = network.connect( serverConfig.getApnsAddress(), sslEngine );
        }

        network.queue( notificationInterface, apnsChannel );
    }

    /**
     * Fetch a list from Apple's Feedback service
     * 
     * <p>
     * This operation will establish a connection to the configured APNs and dispatch all queued notifications.
     * </p>
     * 
     * @param callback
     *            The instance to notify when the uninstalled devices have been determined. Use <code>null</code> if
     *            you're not interested in feedback but just want to clear the Apple Push Notification Feedback
     *            Service's data queue.
     * 
     * @throws IOException
     *             If the system failed to initiate a connection to the APNs.
     * @throws NoSuchAlgorithmException
     *             The <code>keyStore</code> provider does not support the necessary algorithms.
     * @throws KeyManagementException
     *             The SSL context could not be initialized using the available private keys.
     */
    public void fetchUninstalledDevices(UninstalledDevicesCallback callback)
            throws IOException, KeyManagementException, NoSuchAlgorithmException {

        if (feedbackChannel == null || !feedbackChannel.isOpen()) {
            feedbackCallback = callback;
            SSLEngine sslEngine = createSSLEngine( serverConfig.getFeedBackAddress(),
                                                   serverConfig.getEncryptionProtocol(), //
                                                   keyManagerFactory, trustManagerFactory );
            feedbackChannel = network.connect( serverConfig.getFeedBackAddress(), sslEngine );
        } else
            logger.wrn( "Feedback Service is already being polled." );
    }

    /**
     * @return The character set used to encode the payload data.
     */
    public Charset getPayloadEncoding() {

        return payloadEncoding;
    }

    /**
     * @param payloadEncoding
     *            The character set used to encode the payload data.
     */
    public void setPayloadEncoding(Charset payloadEncoding) {

        this.payloadEncoding = payloadEncoding;
    }

    /**
     * The Apple specifications (at the time of this writing) define the byte order to be {@link ByteOrder#BIG_ENDIAN}.
     * 
     * @return The {@link ByteOrder} of bytes sent to the Apple Push Notification server's raw interface.
     */
    public ByteOrder getByteOrder() {

        return byteOrder;
    }

    /**
     * <p>
     * <b>Do not modify this property unless you have a very good reason to do so.</b>
     * </p>
     * 
     * @param byteOrder
     *            The byteOrder of this {@link APNClient}.
     */
    public void setByteOrder(ByteOrder byteOrder) {

        this.byteOrder = byteOrder;
    }

    /**
     * <p>
     * <b>Do not modify this property unless you have a very good reason to do so.</b>
     * </p>
     * 
     * @param maxPayloadSize
     *            The maximum allowed byte size of the serialized {@link NotificationPayLoad} encoded with
     *            {@link #getPayloadEncoding()}.
     */
    public void setMaxPayloadSize(short maxPayloadSize) {

        this.maxPayloadSize = maxPayloadSize;
    }

    /**
     * The Apple specifications (at the time of this writing) define the maximum payload byte size to be
     * <code>256 bytes</code>.
     * 
     * @return The maximum allowed byte size of the serialized {@link NotificationPayLoad} encoded with
     *         {@link #getPayloadEncoding()}.
     */
    public short getMaxPayloadSize() {

        return maxPayloadSize;
    }

    /**
     * Obtain a reference to the {@link Network} framework used by this {@link APNClient} for its network connectivity.
     * 
     * <p>
     * <b>Accessing the {@link Network} framework directly is strongly discouraged.</b> {@link APNClient} provides an
     * interface for all features you should need. This method is mostly here for advanced usage. Going behind the back
     * of {@link APNClient} is ill-advised. You have been warned.
     * </p>
     * 
     * @return The network instance of this {@link APNClient}.
     */
    public Network getNetwork() {

        return network;
    }

    /**
     * Register an object for receiving APNs network connection state updates.
     * 
     * <p>
     * This call is a facade to the
     * {@link Network#registerStateListener(com.lyndir.lhunath.lib.network.NetworkServerStateListener)}. However, in the
     * interest of encapsulation, only notifications about the APNs connection will be relayed (usually, that's the only
     * connection on the network instance anyway).
     * </p>
     * 
     * @param listener
     *            The object wishing to be notified of network state changes.
     */
    public void registerConnectionStateListener(NetworkConnectionStateListener listener) {

        stateListeners.add( listener );
    }

    /**
     * Unregister an object from receiving APNs network connection state updates. The object will no longer receive
     * state updates.
     * 
     * @param listener
     *            The object that used to be interested in network state changes but no longer is.
     */
    public void unregisterConnectionStateListener(NetworkConnectionStateListener listener) {

        stateListeners.remove( listener );
    }

    /**
     * {@inheritDoc}
     */
    public void connected(SocketChannel connectionSocket) {

        if (connectionSocket == apnsChannel)
            logger.inf( "Connected to APNs" );

        // Forward this event to our own state listeners if it's about the APNs connection.
        if (connectionSocket == apnsChannel)
            for (NetworkConnectionStateListener stateListener : stateListeners)
                stateListener.connected( connectionSocket );
    }

    /**
     * {@inheritDoc}
     */
    public void closed(SocketChannel connectionSocket, boolean resetByPeer) {

        if (connectionSocket == apnsChannel)
            logger.inf( "Disconnected from APNs" );

        if (connectionSocket == feedbackChannel) {
            logger.inf( "Disconnected from Feedback Service" );
            if (!feedbackDevices.isEmpty() && feedbackCallback != null)
                new Thread( new Runnable() {

                    public void run() {

                        feedbackCallback.detectedUninstalledDevices( feedbackDevices );
                    }
                }, "APN Feedback Service Callback" ).start();
        }

        // Forward this event to our own state listeners if it's about the APNs connection.
        if (connectionSocket == apnsChannel)
            for (NetworkConnectionStateListener stateListener : stateListeners)
                stateListener.closed( connectionSocket, resetByPeer );
    }

    /**
     * {@inheritDoc}
     */
    public void received(ByteBuffer dataBuffer, SocketChannel connectionSocket) {

        if (connectionSocket == feedbackChannel) {
            dataBuffer.order( getByteOrder() );

            // Transfer the data into the feedback buffer and make it ready for reading.
            if (feedbackBuffer.remaining() < dataBuffer.remaining()) {
                ByteBuffer newFeedbackBuffer = ByteBuffer.allocate( feedbackBuffer.position() + dataBuffer.remaining() );
                feedbackBuffer.flip();
                feedbackBuffer = newFeedbackBuffer.put( feedbackBuffer );
            }
            feedbackBuffer.put( dataBuffer ).flip();

            // Visualize the bytes we have in the feedbackBuffer.
            String bytes = getPayloadEncoding().decode( feedbackBuffer ).toString();
            bytes.replaceAll( "\\p{Print}", "." );
            feedbackBuffer.flip();

            StringBuffer bits = new StringBuffer(), hexBytes = new StringBuffer();
            while (feedbackBuffer.remaining() > 0) {
                byte aByte = feedbackBuffer.get();
                bits.append( ' ' ).append( Integer.toBinaryString( aByte ) );
                hexBytes.append( ' ' ).append( Integer.toHexString( aByte ) );
            }
            feedbackBuffer.flip();

            logger.inf( "Received from Feedback Service:" );
            logger.inf( "%s", bits );
            logger.inf( "%s | %s", hexBytes, bytes );

            // Parse the bytes in the feedbackBuffer in as uninstalled device records.
            while (feedbackBuffer.remaining() > 0)
                try {
                    feedbackBuffer.mark();

                    int utcUnixTime = feedbackBuffer.getInt();
                    short deviceTokenLength = feedbackBuffer.getShort();
                    byte[] deviceToken = new byte[deviceTokenLength];
                    feedbackBuffer.get( deviceToken );

                    Date uninstallDate = new Date( utcUnixTime * 1000 );
                    NotificationDevice device = new NotificationDevice( deviceToken );

                    logger.inf( "Feedback service indicated device %s uninstalled the application before %s", //
                                device, uninstallDate );
                    feedbackDevices.put( device, uninstallDate );
                }

                catch (BufferUnderflowException e) {
                    // Not enough bytes in the dataBuffer for a whole record; undo our last read operations.
                    feedbackBuffer.reset();
                    break;
                }

            // Compact what we read out of the buffer.
            feedbackBuffer.compact();
        }
    }

    /**
     * Close active connections to the Apple Push Notification server.
     * 
     * <p>
     * <b>Note:</b> This does not close any possible connections to the Apple Push Notification Feedback service. This
     * connection is expected to close automatically when Apple finished dumping its queue. Use
     * {@link #closeFeedbackService()} if for some reason you want to do this anyway.
     * </p>
     */
    public void closeAPNs() {

        if (apnsChannel != null && apnsChannel.isOpen())
            try {
                network.close( apnsChannel );
                apnsChannel = null;
            }

            catch (IOException e) {
                logger.err( "While closing the Apple Push Notification server connection:", e );
            }
    }

    /**
     * Close any active connection to the Apple Push Notification Feedback service.
     */
    public void closeFeedbackService() {

        if (feedbackChannel != null && feedbackChannel.isOpen())
            try {
                network.close( feedbackChannel );
                feedbackChannel = null;
            }

            catch (IOException e) {
                logger.err( "While closing the Apple Push Notification Feedback service connection:", e );
            }
    }
}
