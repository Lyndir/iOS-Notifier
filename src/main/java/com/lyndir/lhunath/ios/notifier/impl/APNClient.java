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
package com.lyndir.lhunath.ios.notifier.impl;

import static com.google.common.base.Preconditions.*;

import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.gson.*;
import com.lyndir.lhunath.ios.notifier.*;
import com.lyndir.lhunath.ios.notifier.data.*;
import com.lyndir.lhunath.ios.notifier.util.PKIUtils;
import com.lyndir.lhunath.opal.network.*;
import com.lyndir.lhunath.opal.system.logging.Logger;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.*;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.security.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import javax.net.ssl.*;
import org.jetbrains.annotations.Nullable;


/**
 * <h2>{@link APNClient}<br> <sub>An APNs client for queueing and dispatching notifications to the APNs.</sub></h2>
 * <p/>
 * <p> TODO </p>
 * <p/>
 * <p> <i>Jun 18, 2009</i> </p>
 *
 * @author lhunath
 */
public class APNClient implements APNClientService, NetworkConnectionStateListener, NetworkDataListener {

    private static final Logger  logger        = Logger.get( APNClient.class );
    private static final Pattern NON_PRINTABLE = Pattern.compile( "\\p{Print}" );

    private final Collection<NetworkConnectionStateListener> stateListeners = new HashSet<NetworkConnectionStateListener>();
    private final ExecutorService                            executor       = Executors.newCachedThreadPool();
    private final APNQueue                                   apnQueue       = new APNQueue( this );

    private Gson      gson            = new GsonBuilder() //
            .disableHtmlEscaping()
            .disableInnerClassSerialization()
            .setFieldNamingPolicy( FieldNamingPolicy.LOWER_CASE_WITH_DASHES )
            .create();
    private Charset   payloadEncoding = Charsets.UTF_8;
    private ByteOrder byteOrder       = ByteOrder.BIG_ENDIAN;
    private short     maxPayloadSize  = 256;

    private Supplier<Integer> identifierSupplier = new Supplier<Integer>() {
        private final Random random = new Random();

        @Override
        public Integer get() {

            return random.nextInt();
        }
    };

    private KeyManagerFactory   keyManagerFactory;
    private TrustManagerFactory trustManagerFactory;
    private APNServerConfig     serverConfig;

    private final Network       network;
    private       SocketChannel apnsChannel;
    private       SocketChannel feedbackChannel;
    private final ByteBuffer    apnResponseBuffer;
    private       ByteBuffer    feedbackBuffer;

    private final Map<NotificationDevice, Date> feedbackDevices;
    private       UnreachableDevicesCallback    feedbackCallback;
    private       APNResponseCallback           apnsCallback;

    /**
     * Create a new {@link APNClient} instance by setting up the PKIX identity and trust to reasonable defaults from the given parameters.
     *
     * @param keyStore           The keystore which provides all required SSL keys and certificates.
     * @param privateKeyPassword The password which protects the required {@code keyStore}'s private key.
     * @param serverConfig       The {@link APNServerConfig} that determines the host configuration of the Apple Push Notification server
     *                           and Feedback service.
     *
     * @throws UnrecoverableKeyException The private key could not be accessed from the {@code keyStore}. Perhaps the provided
     *                                   {@code privateKeyPassword} is incorrect.
     * @throws NoSuchAlgorithmException  The {@code keyStore} provider does not support the necessary algorithms.
     * @throws KeyStoreException         The {@code keyStore} had not been properly loaded/initialized or is corrupt.
     */
    public APNClient(final KeyStore keyStore, final String privateKeyPassword, final APNServerConfig serverConfig)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {

        this( PKIUtils.createKeyManagerFactory( keyStore, privateKeyPassword ), PKIUtils.createTrustManagerFactory( keyStore ),
                serverConfig );
    }

    /**
     * Create a new {@link APNClient} instance using a custom configured {@link KeyManagerFactory} and {@link TrustManagerFactory} to
     * provide PKIX identity and trust.
     *
     * @param trustManagerFactory The factory that will create the SSL context's {@link TrustManager}s.
     * @param keyManagerFactory   The factory that will create the SSL context's {@link KeyManager}s.
     * @param serverConfig        The {@link APNServerConfig} that determines the host configuration of the Apple Push Notification server
     *                            and Feedback service.
     */
    public APNClient(final KeyManagerFactory keyManagerFactory, final TrustManagerFactory trustManagerFactory,
                     final APNServerConfig serverConfig) {

        int apnResponseRecordLength = 0;
        // Command
        apnResponseRecordLength += Byte.SIZE / Byte.SIZE;
        // Status
        apnResponseRecordLength += Byte.SIZE / Byte.SIZE;
        // Identifier
        apnResponseRecordLength += Integer.SIZE / Byte.SIZE;

        int uninstalledDeviceRecordLength = 0;
        // UTC UNIX Timestamp
        uninstalledDeviceRecordLength += Integer.SIZE / Byte.SIZE;
        // Device Token length
        uninstalledDeviceRecordLength += Short.SIZE / Byte.SIZE;
        // Device Token
        uninstalledDeviceRecordLength += 32;

        apnResponseBuffer = ByteBuffer.allocate( apnResponseRecordLength );
        feedbackBuffer = ByteBuffer.allocate( uninstalledDeviceRecordLength );
        feedbackDevices = Collections.synchronizedMap( new HashMap<NotificationDevice, Date>() );

        network = new Network();
        network.registerConnectionStateListener( this );
        network.registerDataListener( this );

        configure( keyManagerFactory, trustManagerFactory, serverConfig );
    }

    /**
     * Update the configuration of the {@link APNClient} instance by setting up the PKIX identity and trust to reasonable defaults from the
     * given parameters.
     * <p>Note: The APN queue will be closed and restarted if it is open.</p>
     *
     * @param keyStore           The keystore which provides all required SSL keys and certificates.
     * @param privateKeyPassword The password which protects the required {@code keyStore}'s private key.
     * @param serverConfig       The {@link APNServerConfig} that determines the host configuration of the Apple Push Notification server
     *                           and Feedback service.
     *
     * @throws UnrecoverableKeyException The private key could not be accessed from the {@code keyStore}. Perhaps the provided
     *                                   {@code privateKeyPassword} is incorrect.
     * @throws NoSuchAlgorithmException  The {@code keyStore} provider does not support the necessary algorithms.
     * @throws KeyStoreException         The {@code keyStore} had not been properly loaded/initialized or is corrupt.
     */
    public APNClient configure(final KeyStore keyStore, final String privateKeyPassword, final APNServerConfig serverConfig)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {

        return configure( PKIUtils.createKeyManagerFactory( keyStore, privateKeyPassword ), PKIUtils.createTrustManagerFactory( keyStore ),
                serverConfig );
    }

    /**
     * Update the configuration of the {@link APNClient} instance using a custom configured {@link KeyManagerFactory} and {@link
     * TrustManagerFactory} to
     * provide PKIX identity and trust.
     * <p>Note: The APN queue will be closed and restarted if it is open.</p>
     *
     * @param trustManagerFactory The factory that will create the SSL context's {@link TrustManager}s.
     * @param keyManagerFactory   The factory that will create the SSL context's {@link KeyManager}s.
     * @param serverConfig        The {@link APNServerConfig} that determines the host configuration of the Apple Push Notification server
     *                            and Feedback service.
     */
    public synchronized APNClient configure(final KeyManagerFactory keyManagerFactory, final TrustManagerFactory trustManagerFactory,
                                            final APNServerConfig serverConfig) {

        this.keyManagerFactory = keyManagerFactory;
        this.trustManagerFactory = trustManagerFactory;
        this.serverConfig = serverConfig;

        closeAPNs();
        closeFeedbackService();

        return this;
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
     * @param serverAddress       The hostname and port of the remote server socket to create an SSL engine for.
     * @param protocol            The SSL/TLS protocol to use for secure transport encryption. <p> Valid values depend on what is supported
     *                            by the <code>sslProvider</code>, but generally speaking there is: {@code SSL, SSLv2, SSLv3, TLS, TLSv1,}
     *                            TLSv1.1, SSLv2Hello</code>. </p>
     * @param trustManagerFactory The factory that will create the SSL context's {@link TrustManager}s.
     * @param keyManagerFactory   The factory that will create the SSL context's {@link KeyManager}s.
     *
     * @return An {@link SSLEngine}.
     *
     * @throws NoSuchAlgorithmException The {@code keyStore} provider does not support the necessary algorithms.
     * @throws KeyManagementException   The SSL context could not be initialized using the available private keys.
     * @see <a href="http://java.sun.com/javase/6/docs/technotes/guides/security/StandardNames.html#jssenames">JSSE Protocol Names</a>
     */
    private static SSLEngine createSSLEngine(final InetSocketAddress serverAddress, final String protocol,
                                             final KeyManagerFactory keyManagerFactory, final TrustManagerFactory trustManagerFactory)
            throws NoSuchAlgorithmException, KeyManagementException {

        // Set up an SSL context from identity and trust configurations.
        SSLContext sslContext = SSLContext.getInstance( protocol );
        sslContext.init( keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null );

        return sslContext.createSSLEngine( serverAddress.getHostName(), serverAddress.getPort() );
    }

    @Nullable
    @Override
    public Integer queueNotification(final NotificationDevice device, final Payload payload, final Date expiryDate) {

        checkNotNull( payload, //
                "Missing notification payload." );

        int tokenLengthInt = device.getToken().length;
        checkArgument( tokenLengthInt <= Short.MAX_VALUE, //
                "Device Token can't be longer than %s bytes; token '%s' is %s bytes.", //
                Short.MAX_VALUE, device.getToken(), tokenLengthInt );
        short tokenLength = (short) tokenLengthInt;

        String jsonPayload = gson.toJson( payload );
        ByteBuffer payLoadData = getPayloadEncoding().encode( jsonPayload );

        int payLoadLengthInt = payLoadData.remaining();
        checkArgument( payLoadLengthInt <= maxPayloadSize && payLoadLengthInt <= Short.MAX_VALUE, //
                "Payload can't be larger than %s bytes; passed payload was %s bytes.", //
                Math.min( maxPayloadSize, Short.MAX_VALUE ), payLoadLengthInt );
        short payLoadLength = (short) payLoadLengthInt;

        // The interface command.
        byte command = (byte) 1;
        int identifier = identifierSupplier.get();
        int expiryTime = (int) (expiryDate.getTime() / 1000);

        logger.inf( "Sending notification: %s, to device: %s", jsonPayload, device );

        // Allocate the interface byte buffer.
        int notificationByteSize = getInterfaceByteSize( tokenLength, payLoadLength );
        ByteBuffer notificationData = ByteBuffer.allocate( notificationByteSize ).order( byteOrder );

        // Add Interface Command
        notificationData.put( command );
        // Add Interface Identifier
        notificationData.putInt( identifier );
        // Add Interface Expiry Timestamp
        notificationData.putInt( expiryTime );
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
        boolean wasQueued;
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

        return wasQueued? identifier: null;
    }

    /**
     * Calculate the byte size of the interface for sending a certain notification.
     *
     * @param tokenLength   The byte size of the {@link NotificationDevice}'s token that identifies the notification's destination.
     * @param payLoadLength The byte size of the {@link APSPayload} when serialized and encoded with the character set as defined by {@link
     *                      #getPayloadEncoding()}.
     *
     * @return The byte size of the notification interface that will be sent to the APNs.
     */
    private static int getInterfaceByteSize(final short tokenLength, final short payLoadLength) {

        int interfaceByteSize = 0;

        // Command: a byte.
        interfaceByteSize += Byte.SIZE / Byte.SIZE;
        // Identifier: an int.
        interfaceByteSize += Integer.SIZE / Byte.SIZE;
        // Expiry: an int.
        interfaceByteSize += Integer.SIZE / Byte.SIZE;
        // Token length: a short.
        interfaceByteSize += Short.SIZE / Byte.SIZE;
        // Token
        interfaceByteSize += tokenLength;
        // Payload length: a short.
        interfaceByteSize += Short.SIZE / Byte.SIZE;
        // Payload
        interfaceByteSize += payLoadLength;

        return interfaceByteSize;
    }

    /**
     * Dispatch the given notification interface to the APNs.
     * <p/>
     * <p> This operation will establish a connection to the configured APNs if one is not active already. Note that the connection will
     * not
     * be terminated automatically. You are responsible for calling {@link #closeAPNs()} when you determine you won't be dispatching any
     * more messages soon. </p>
     *
     * @param notificationInterface The {@link ByteBuffer} containing the raw interface of the notification data to send.
     *
     * @throws IOException              If the system failed to initiate a connection to the APNs.
     * @throws NoSuchAlgorithmException The {@code keyStore} provider does not support the necessary algorithms.
     * @throws KeyManagementException   The SSL context could not be initialized using the available private keys.
     */
    public synchronized void dispatch(final ByteBuffer notificationInterface)
            throws IOException, KeyManagementException, NoSuchAlgorithmException {

        if (apnsChannel == null || !apnsChannel.isOpen()) {
            SSLEngine sslEngine = createSSLEngine( getServerConfig().getApnsAddress(), getServerConfig().getEncryptionProtocol(), //
                    getKeyManagerFactory(), getTrustManagerFactory() );
            apnsChannel = network.connect( getServerConfig().getApnsAddress(), sslEngine );
        }

        network.queue( notificationInterface, apnsChannel );
    }

    @Override
    public void fetchUnreachableDevices(final UnreachableDevicesCallback callback)
            throws IOException, KeyManagementException, NoSuchAlgorithmException {

        if (feedbackChannel == null || !feedbackChannel.isOpen()) {
            feedbackCallback = callback;
            SSLEngine sslEngine = createSSLEngine( getServerConfig().getFeedBackAddress(), getServerConfig().getEncryptionProtocol(), //
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
     * @param payloadEncoding The character set used to encode the payload data.
     */
    public void setPayloadEncoding(final Charset payloadEncoding) {

        this.payloadEncoding = payloadEncoding;
    }

    /**
     * The key factory configured for the APN client.
     * <p/>
     * <p> Uses private key entries from the {@code keyStore} it was created with to provide client authentication with the server.
     * </p>
     *
     * @return The factory for {@link KeyManager}s which provide the client identity.
     */
    public synchronized KeyManagerFactory getKeyManagerFactory() {

        return keyManagerFactory;
    }

    /**
     * Change the {@link KeyManagerFactory} that is used for client identification during SSL negotiation.
     *
     * @param keyManagerFactory The factory for {@link KeyManager}s which provide the client identity.
     */
    public synchronized void setKeyManagerFactory(final KeyManagerFactory keyManagerFactory) {

        this.keyManagerFactory = keyManagerFactory;

        closeAPNs();
        closeFeedbackService();
    }

    /**
     * The trust factory configured for the APN client.
     * <p/>
     * <p> The trust factory provides simple trust for each trusted certificate in the {@code keyStore} it was created with. </p>
     *
     * @return The factory for {@link TrustManager}s.
     */
    public synchronized TrustManagerFactory getTrustManagerFactory() {

        return trustManagerFactory;
    }

    /**
     * Change the {@link TrustManagerFactory} that is used to provide trust of server identification during SSL negotiation.
     *
     * @param trustManagerFactory The factory for {@link TrustManager}s.
     */
    public synchronized void setTrustManagerFactory(final TrustManagerFactory trustManagerFactory) {

        this.trustManagerFactory = trustManagerFactory;

        closeAPNs();
        closeFeedbackService();
    }

    /**
     * @return The server configuration that is used to establish communication to the Apple services.
     */
    public synchronized APNServerConfig getServerConfig() {

        return serverConfig;
    }

    /**
     * @param serverConfig The server configuration that is used to establish communication to the Apple services.
     */
    public synchronized void setServerConfig(final APNServerConfig serverConfig) {

        this.serverConfig = serverConfig;

        closeAPNs();
        closeFeedbackService();
    }

    /**
     * @param apnsCallback A callback that should be invoked when an APNs response is received.
     */
    public void setResponseCallback(final APNResponseCallback apnsCallback) {

        this.apnsCallback = apnsCallback;
    }

    /**
     * @return The JSON serializer used to construct JSON data out of the {@link Payload}.
     */
    public Gson getGson() {

        return gson;
    }

    /**
     * <p> <b>Do not modify this property unless you have a very good reason to do so.</b> </p>
     *
     * @param gson The JSON serializer of this {@link APNClient}.
     */
    public void setGson(final Gson gson) {

        this.gson = gson;
    }

    /**
     * @return The supplier that provides us with identifiers to use for the push notification payload.
     */
    public Supplier<Integer> getIdentifierSupplier() {

        return identifierSupplier;
    }

    /**
     * <p> <b>Do not modify this property unless you have a very good reason to do so.</b> </p>
     *
     * @param identifierSupplier The supplier that provides us with identifiers to use for the push notification payload.
     */
    public void setIdentifierSupplier(final Supplier<Integer> identifierSupplier) {

        this.identifierSupplier = identifierSupplier;
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
     * <p> <b>Do not modify this property unless you have a very good reason to do so.</b> </p>
     *
     * @param byteOrder The byteOrder of this {@link APNClient}.
     */
    public void setByteOrder(final ByteOrder byteOrder) {

        this.byteOrder = byteOrder;
    }

    /**
     * <p> <b>Do not modify this property unless you have a very good reason to do so.</b> </p>
     *
     * @param maxPayloadSize The maximum allowed byte size of the serialized {@link APSPayload} encoded with {@link #getPayloadEncoding()}.
     */
    public void setMaxPayloadSize(final short maxPayloadSize) {

        this.maxPayloadSize = maxPayloadSize;
    }

    /**
     * The Apple specifications (at the time of this writing) define the maximum payload byte size to be {@code 256 bytes}.
     *
     * @return The maximum allowed byte size of the serialized {@link APSPayload} encoded with {@link #getPayloadEncoding()}.
     */
    public short getMaxPayloadSize() {

        return maxPayloadSize;
    }

    /**
     * Obtain a reference to the {@link Network} framework used by this {@link APNClient} for its network connectivity.
     * <p/>
     * <p> <b>Accessing the {@link Network} framework directly is strongly discouraged.</b> {@link APNClient} provides an interface for all
     * features you should need. This method is mostly here for advanced usage. Going behind the back of {@link APNClient} is ill-advised.
     * You have been warned. </p>
     *
     * @return The network instance of this {@link APNClient}.
     */
    public Network getNetwork() {

        return network;
    }

    /**
     * Register an object for receiving APNs network connection state updates.
     * <p/>
     * <p> This call is a facade to the {@link Network#registerConnectionStateListener(NetworkConnectionStateListener)}. However, in the
     * interest of encapsulation, only notifications about the connection to the APNs will be relayed. </p>
     *
     * @param listener The object wishing to be notified of network state changes.
     */
    public void registerConnectionStateListener(final NetworkConnectionStateListener listener) {

        stateListeners.add( listener );
    }

    /**
     * Unregister an object from receiving APNs network connection state updates. The object will no longer receive state updates.
     *
     * @param listener The object that used to be interested in network state changes but no longer is.
     */
    public void unregisterConnectionStateListener(final NetworkConnectionStateListener listener) {

        stateListeners.remove( listener );
    }

    @Override
    public void connected(final SocketChannel channel) {

        if (apnsChannel.equals( channel )) {
            logger.inf( "Connected to APNs" );

            // Forward this event to our own state listeners if it's about the APNs connection.
            for (final NetworkConnectionStateListener stateListener : stateListeners)
                stateListener.connected( channel );
        }
        if (feedbackChannel.equals( channel )) {
            logger.inf( "Connected to Feedback Service" );
        }
    }

    @Override
    public void closed(final SocketChannel channel, final boolean resetByPeer) {

        if (feedbackChannel.equals( channel )) {
            logger.inf( "Disconnected from Feedback Service" );

            if (!feedbackDevices.isEmpty() && feedbackCallback != null)
                executor.submit( new Runnable() {

                    @Override
                    public void run() {

                        feedbackCallback.foundUnreachableDevices( feedbackDevices );
                    }
                } );
        }

        // Forward this event to our own state listeners if it's about the APNs connection.
        if (apnsChannel.equals( channel )) {
            logger.inf( "Disconnected from APNs" );

            for (final NetworkConnectionStateListener stateListener : stateListeners)
                stateListener.closed( channel, resetByPeer );
        }
    }

    @Override
    public void received(final ByteBuffer dataBuffer, final SocketChannel channel) {

        if (apnsChannel.equals( channel )) {
            logger.dbg( "Received %d bytes of APNs response", dataBuffer.remaining() );
            dataBuffer.order( getByteOrder() );

            // Transfer the data into the APN response buffer and make it ready for reading.
            apnResponseBuffer.put( dataBuffer );
            logger.dbg( "apnResponseBuffer is now %d bytes, %d remaining", apnResponseBuffer.position(), apnResponseBuffer.remaining() );

            // Parse the bytes in the APN response buffer in as uninstalled device records.
            if (apnResponseBuffer.remaining() == 0) {
                apnResponseBuffer.flip();
                byte command = apnResponseBuffer.get();

                if (command == (byte) 8) {
                    byte status = apnResponseBuffer.get();
                    int identifier = apnResponseBuffer.getInt();
                    final APNResponse response = new APNResponse( status, identifier );
                    logger.inf( "Received APN response: %s", response );

                    if (apnsCallback != null)
                        executor.submit( new Runnable() {

                            @Override
                            public void run() {

                                apnsCallback.responseReceived( response );
                            }
                        } );
                } else {
                    logger.wrn( "APN response command not implemented: %d", Byte.valueOf( command ).intValue() );
                    apnResponseBuffer.position( apnResponseBuffer.limit() );
                }

                apnResponseBuffer.compact();
            }
        }

        if (feedbackChannel.equals( channel )) {
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
            NON_PRINTABLE.matcher( bytes ).replaceAll( "." );
            feedbackBuffer.flip();

            StringBuffer bits = new StringBuffer(), hexBytes = new StringBuffer();
            while (feedbackBuffer.remaining() > 0) {
                byte aByte = feedbackBuffer.get();
                bits.append( ' ' ).append( Integer.toBinaryString( aByte ) );
                hexBytes.append( ' ' ).append( Integer.toHexString( aByte ) );
            }
            feedbackBuffer.flip();

            logger.dbg( "Received from Feedback Service:" );
            logger.dbg( "%s", bits );
            logger.dbg( "%s | %s", hexBytes, bytes );

            // Parse the bytes in the feedbackBuffer in as uninstalled device records.
            while (feedbackBuffer.remaining() > 0)
                try {
                    feedbackBuffer.mark();

                    int utcUnixTime = feedbackBuffer.getInt();
                    short deviceTokenLength = feedbackBuffer.getShort();
                    byte[] deviceToken = new byte[deviceTokenLength];
                    feedbackBuffer.get( deviceToken );

                    Date uninstallDate = new Date( (long) utcUnixTime * 1000 );
                    NotificationDevice device = new NotificationDevice( deviceToken );

                    logger.inf( "Feedback service indicated device %s uninstalled the application before %s", //
                            device, uninstallDate );
                    feedbackDevices.put( device, uninstallDate );
                }

                catch (BufferUnderflowException ignored) {
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
     * <p/>
     * <p> <b>Note:</b> This does not close any possible connections to the Apple Push Notification Feedback service. This connection is
     * expected to close automatically when Apple finished dumping its queue. Use {@link #closeFeedbackService()} if for some reason you
     * want to do this anyway. </p>
     */
    public synchronized void closeAPNs() {

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
