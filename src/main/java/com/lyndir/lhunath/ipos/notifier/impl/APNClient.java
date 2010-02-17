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
package com.lyndir.lhunath.ipos.notifier.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

import com.lyndir.lhunath.ipos.notifier.APNClientService;
import com.lyndir.lhunath.ipos.notifier.UnreachableDevicesCallback;
import com.lyndir.lhunath.ipos.notifier.data.APNServerConfig;
import com.lyndir.lhunath.ipos.notifier.data.NotificationDevice;
import com.lyndir.lhunath.ipos.notifier.data.NotificationPayLoad;
import com.lyndir.lhunath.ipos.notifier.util.PKIUtils;
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
public class APNClient implements APNClientService, NetworkConnectionStateListener, NetworkDataListener {

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
    UnreachableDevicesCallback                  feedbackCallback;


    /**
     * Create a new {@link APNClient} instance by setting up the PKIX identity and trust to reasonable defaults from the
     * given parameters.
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
     *             The <code>keyStore</code> had not been properly loaded/initialized or is corrupt.
     */
    public APNClient(KeyStore keyStore, String privateKeyPassword, APNServerConfig serverConfig)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {

        this( PKIUtils.createKeyManagerFactory( keyStore, privateKeyPassword ),
                PKIUtils.createTrustManagerFactory( keyStore ), serverConfig );
    }

    /**
     * Create a new {@link APNClient} instance using a custom configured {@link KeyManagerFactory} and
     * {@link TrustManagerFactory} to provide PKIX identity and trust.
     * 
     * @param trustManagerFactory
     *            The factory that will create the SSL context's {@link TrustManager}s.
     * @param keyManagerFactory
     *            The factory that will create the SSL context's {@link KeyManager}s.
     * @param serverConfig
     *            The {@link APNServerConfig} that determines the host configuration of the Apple Push Notification
     *            server and Feedback service.
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

    /**
     * Bring the networking framework up and start the {@link APNQueue}.
     */
    public synchronized void start() {

        network.bringUp();
        apnQueue.start();
    }

    /**
     * Shut down the APN client and all frameworks it initialized.
     */
    public void stop() {

        network.stopThread();
        apnQueue.stop();
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
     * @see <a href="http://java.sun.com/javase/6/docs/technotes/guides/security/StandardNames.html#jssenames">JSSE
     *      Protocol Names</a>
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
     * {@inheritDoc}
     */
    public boolean queueNotification(NotificationDevice device, NotificationPayLoad... notificationPayLoads) {

        return queueNotification( device, null, notificationPayLoads );
    }

    /**
     * {@inheritDoc}
     */
    public boolean queueNotification(NotificationDevice device, JSONObject customData,
                                     NotificationPayLoad... notificationPayLoads) {

        List<JSONObject> jsonNotificationPayLoads = new ArrayList<JSONObject>( notificationPayLoads.length );
        for (NotificationPayLoad notificationPayLoad : notificationPayLoads)
            jsonNotificationPayLoads.add( JSONObject.fromObject( notificationPayLoad ) );

        return queueNotification( device, customData,
                jsonNotificationPayLoads.toArray( new JSONObject[jsonNotificationPayLoads.size()] ) );
    }

    /**
     * {@inheritDoc}
     */
    public boolean queueNotification(NotificationDevice device, JSONObject customData,
                                     JSONObject... notificationPayLoads) {

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
            for (JSONObject notificationPayLoad : notificationPayLoads)
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
        boolean wasQueued = false;
        synchronized (apnQueue) {
            wasQueued = apnQueue.offer( notificationData );
        }

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
            SSLEngine sslEngine = createSSLEngine( getServerConfig().getApnsAddress(),
                    getServerConfig().getEncryptionProtocol(), //
                    getKeyManagerFactory(), getTrustManagerFactory() );
            apnsChannel = network.connect( getServerConfig().getApnsAddress(), sslEngine );
        }

        network.queue( notificationInterface, apnsChannel );
    }

    /**
     * {@inheritDoc}
     */
    public void fetchUnreachableDevices(UnreachableDevicesCallback callback)
            throws IOException, KeyManagementException, NoSuchAlgorithmException {

        if (feedbackChannel == null || !feedbackChannel.isOpen()) {
            feedbackCallback = callback;
            SSLEngine sslEngine = createSSLEngine( getServerConfig().getFeedBackAddress(),
                    getServerConfig().getEncryptionProtocol(), //
                    getKeyManagerFactory(), getTrustManagerFactory() );
            feedbackChannel = network.connect( getServerConfig().getFeedBackAddress(), sslEngine );
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
     * The key factory configured for the APN client.
     * 
     * <p>
     * Uses private key entries from the <code>keyStore</code> it was created with to provide client authentication with
     * the server.
     * </p>
     * 
     * @return The factory for {@link KeyManager}s which provide the client identity.
     */
    public KeyManagerFactory getKeyManagerFactory() {

        return keyManagerFactory;
    }

    /**
     * Change the {@link KeyManagerFactory} that is used for client identification during SSL negotiation.
     * 
     * @param keyManagerFactory
     *            The factory for {@link KeyManager}s which provide the client identity.
     */
    public void setKeyManagerFactory(KeyManagerFactory keyManagerFactory) {

        this.keyManagerFactory = keyManagerFactory;

        closeAPNs();
        closeFeedbackService();
    }

    /**
     * The trust factory configured for the APN client.
     * 
     * <p>
     * The trust factory provides simple trust for each trusted certificate in the <code>keyStore</code> it was created
     * with.
     * </p>
     * 
     * @return The factory for {@link TrustManager}s.
     */
    public TrustManagerFactory getTrustManagerFactory() {

        return trustManagerFactory;
    }

    /**
     * Change the {@link TrustManagerFactory} that is used to provide trust of server identification during SSL
     * negotiation.
     * 
     * @param trustManagerFactory
     *            The factory for {@link TrustManager}s.
     */
    public void setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {

        this.trustManagerFactory = trustManagerFactory;

        closeAPNs();
        closeFeedbackService();
    }

    /**
     * @return The server configuration that is used to establish communication to the Apple services.
     */
    public APNServerConfig getServerConfig() {

        return serverConfig;
    }

    /**
     * @param serverConfig
     *            The server configuration that is used to establish communication to the Apple services.
     */
    public void setServerConfig(APNServerConfig serverConfig) {

        this.serverConfig = serverConfig;

        closeAPNs();
        closeFeedbackService();
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
     * This call is a facade to the {@link Network#registerConnectionStateListener(NetworkConnectionStateListener)}.
     * However, in the interest of encapsulation, only notifications about the connection to the APNs will be relayed.
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

                        feedbackCallback.foundUnreachableDevices( feedbackDevices );
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
                logger.err( e, "While closing the Apple Push Notification server connection:" );
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
                logger.err( e, "While closing the Apple Push Notification Feedback service connection:" );
            }
    }
}
