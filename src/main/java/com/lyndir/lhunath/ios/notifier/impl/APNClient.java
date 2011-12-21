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
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.gson.*;
import com.lyndir.lhunath.ios.notifier.*;
import com.lyndir.lhunath.ios.notifier.data.*;
import com.lyndir.lhunath.ios.notifier.util.PKIUtils;
import com.lyndir.lhunath.opal.system.logging.Logger;
import java.io.*;
import java.nio.*;
import java.nio.charset.Charset;
import java.security.*;
import java.util.*;
import javax.net.ssl.*;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.*;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.handler.stream.StreamIoHandler;
import org.apache.mina.transport.socket.nio.NioSocketConnector;


/**
 * <h2>{@link APNClient}<br> <sub>An APNs client for queueing and dispatching notifications to the APNs.</sub></h2>
 * <p/>
 * <p> TODO </p>
 * <p/>
 * <p> <i>Jun 18, 2009</i> </p>
 *
 * @author lhunath
 */
public class APNClient implements APNClientService {

    private static final Logger logger = Logger.get( APNClient.class );

    private static final int unreachableDeviceRecordLength =
            // UTC UNIX Timestamp
            Integer.SIZE / Byte.SIZE +
            // Device Token length
            Short.SIZE / Byte.SIZE +
            // Device Token
            32;

    private final APNQueue        apnQueue = new APNQueue( this );

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

    private final NioSocketConnector connector;
    private       IoSession          apnsSession;
    private       IoSession          feedbackSession;

    private APNResponseCallback apnsCallback;

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
     * @throws KeyManagementException    The SSL context could not be initialized using the available private keys.
     */
    public APNClient(final KeyStore keyStore, final String privateKeyPassword, final APNServerConfig serverConfig)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

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
     *
     * @throws NoSuchAlgorithmException The {@code keyStore} provider does not support the necessary algorithms.
     * @throws KeyManagementException   The SSL context could not be initialized using the available private keys.
     */
    public APNClient(final KeyManagerFactory keyManagerFactory, final TrustManagerFactory trustManagerFactory,
                     final APNServerConfig serverConfig)
            throws NoSuchAlgorithmException, KeyManagementException {

        connector = new NioSocketConnector();
        connector.setHandler( new APNsHandler() );
        connector.getFilterChain().addFirst( "ssl", //
                new SslFilter(
                        createSSLContext( getServerConfig().getEncryptionProtocol(), getKeyManagerFactory(), getTrustManagerFactory() ) ) );

        configure( keyManagerFactory, trustManagerFactory, serverConfig );
    }

    private void updateSSL()
            throws NoSuchAlgorithmException, KeyManagementException {

        connector.getFilterChain().replace( "ssl", //
                new SslFilter(
                        createSSLContext( getServerConfig().getEncryptionProtocol(), getKeyManagerFactory(), getTrustManagerFactory() ) ) );

        closeAPNs();
        closeFeedbackService();
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
     * @return <code>this</code> for chaining.
     *
     * @throws UnrecoverableKeyException The private key could not be accessed from the {@code keyStore}. Perhaps the provided
     *                                   {@code privateKeyPassword} is incorrect.
     * @throws NoSuchAlgorithmException  The {@code keyStore} provider does not support the necessary algorithms.
     * @throws KeyStoreException         The {@code keyStore} had not been properly loaded/initialized or is corrupt.
     * @throws KeyManagementException    The SSL context could not be initialized using the available private keys.
     */
    public APNClient configure(final KeyStore keyStore, final String privateKeyPassword, final APNServerConfig serverConfig)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

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
     *
     * @return <code>this</code> for chaining.
     *
     * @throws NoSuchAlgorithmException The {@code keyStore} provider does not support the necessary algorithms.
     * @throws KeyManagementException   The SSL context could not be initialized using the available private keys.
     */
    public synchronized APNClient configure(final KeyManagerFactory keyManagerFactory, final TrustManagerFactory trustManagerFactory,
                                            final APNServerConfig serverConfig)
            throws NoSuchAlgorithmException, KeyManagementException {

        this.keyManagerFactory = keyManagerFactory;
        this.trustManagerFactory = trustManagerFactory;
        this.serverConfig = serverConfig;

        updateSSL();
        return this;
    }

    /**
     * Bring the networking framework up and start the {@link APNQueue}.
     */
    public synchronized void start() {

        apnQueue.start();
    }

    /**
     * Shut down the APN client and all frameworks it initialized.
     */
    public void stop() {

        connector.dispose();
        apnQueue.stop();
    }

    /**
     * Creates the SSL context that will be used when connecting to the APNs.
     *
     * @param protocol            The SSL/TLS protocol to use for secure transport encryption. <p> Valid values depend on what is supported
     *                            by the <code>sslProvider</code>, but generally speaking there is: {@code SSL, SSLv2, SSLv3, TLS, TLSv1,}
     *                            TLSv1.1, SSLv2Hello</code>. </p>
     * @param keyManagerFactory   The factory that will create the SSL context's {@link KeyManager}s.
     * @param trustManagerFactory The factory that will create the SSL context's {@link TrustManager}s.
     *
     * @return An ssl context configured with the given parameters.
     *
     * @throws NoSuchAlgorithmException The {@code keyStore} provider does not support the necessary algorithms.
     * @throws KeyManagementException   The SSL context could not be initialized using the available private keys.
     * @see <a href="http://java.sun.com/javase/6/docs/technotes/guides/security/StandardNames.html#jssenames">JSSE Protocol Names</a>
     */
    private static SSLContext createSSLContext(final String protocol, final KeyManagerFactory keyManagerFactory,
                                               final TrustManagerFactory trustManagerFactory)
            throws NoSuchAlgorithmException, KeyManagementException {

        // Set up an SSL context from identity and trust configurations.
        SSLContext sslContext = SSLContext.getInstance( protocol );
        sslContext.init( keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null );

        return sslContext;
    }

    @Override
    public int queue(final APNRegistration device, final Payload payload, final Date expiryDate) {

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
        synchronized (apnQueue) {
            apnQueue.offer( notificationData );
        }

        logger.inf( "Queued payload: %s", payloadEncoding.decode( payLoadData ) );
        payLoadData.flip();

        return identifier;
    }

    /**
     * Calculate the byte size of the interface for sending a certain notification.
     *
     * @param tokenLength   The byte size of the {@link APNRegistration}'s token that identifies the notification's destination.
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
     * @throws APNException         If the system failed to initiate a connection or write the notification to the APNs.
     * @throws InterruptedException If the network was interrupted while connecting or sending the notification to the APNs.
     */
    public synchronized void send(final ByteBuffer notificationInterface)
            throws InterruptedException, APNException {

        if (apnsSession == null || !apnsSession.isConnected()) {
            apnsSession = null;

            ConnectFuture connectFuture = connector.connect( getServerConfig().getApnsAddress() ).await();
            Throwable connectException = connectFuture.getException();
            if (connectException != null)
                throw new APNException( connectException );

            apnsSession = connectFuture.getSession();
            if (apnsSession == null)
                throw new APNException( "Connection failed for an unknown reason." );
        }

        WriteFuture writeFuture = apnsSession.write( notificationInterface ).await();
        Throwable writeException = writeFuture.getException();
        if (writeException != null)
            throw new APNException( writeException );
        if (!writeFuture.isWritten())
            throw new APNException( "Write failed for an unknown reason." );
    }

    @Override
    public Map<APNRegistration, Date> fetchUnreachableDevices()
            throws InterruptedException, APNException {

        if (feedbackSession != null && feedbackSession.isConnected())
            throw new APNException( "Feedback Service is already being polled." );

        ConnectFuture connectFuture = connector.connect( getServerConfig().getFeedBackAddress() ).await();
        Throwable connectException = connectFuture.getException();
        if (connectException != null)
            throw new APNException( connectException );

        feedbackSession = connectFuture.getSession();
        if (feedbackSession == null)
            throw new APNException( "Connection failed for an unknown reason." );

        Map<APNRegistration, Date> feedbackDevices = Maps.newHashMap();
        IoBuffer feedbackBuffer = IoBuffer.allocate( unreachableDeviceRecordLength ).order( getByteOrder() );
        ReadFuture readFuture;
        do {
            readFuture = feedbackSession.read().await();
            if (readFuture.isRead()) {

                // Transfer the data into the feedback buffer and make it ready for reading.
                feedbackBuffer.put( (IoBuffer) readFuture.getMessage() ).flip();

                // Parse the bytes in the feedbackBuffer as unreachable device records.
                while (feedbackBuffer.remaining() > 0)
                    try {
                        feedbackBuffer.mark();

                        int utcUnixTime = feedbackBuffer.getInt();
                        short deviceTokenLength = feedbackBuffer.getShort();
                        byte[] deviceToken = new byte[deviceTokenLength];
                        feedbackBuffer.get( deviceToken );

                        Date unreachableDate = new Date( (long) utcUnixTime * 1000 );
                        APNRegistration device = new APNRegistration( deviceToken );

                        logger.inf( "Device registration (%s) became unreachable before: %s", device, unreachableDate );
                        feedbackDevices.put( device, unreachableDate );
                    }

                    catch (BufferUnderflowException ignored) {
                        // Not enough bytes in the feedbackBuffer for a whole record; undo our last read operations.
                        feedbackBuffer.reset();
                        break;
                    }

                // Compact what we read out of the buffer.
                feedbackBuffer.compact();
            }
        }
        while (!readFuture.isClosed());

        if (feedbackBuffer.position() > 0)
            logger.wrn( "Not all feedback bytes were consumed.  Bytes left: %d", feedbackBuffer.position() );

        return feedbackDevices;
    }

    /**
     * @return The character set used to encode the payload data.
     */
    public Charset getPayloadEncoding() {

        return payloadEncoding;
    }

    /**
     * @param payloadEncoding The character set used to encode the payload data.
     *
     * @return <code>this</code> for chaining.
     */
    public APNClient setPayloadEncoding(final Charset payloadEncoding) {

        this.payloadEncoding = payloadEncoding;
        return this;
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
     *
     * @return <code>this</code> for chaining.
     *
     * @throws NoSuchAlgorithmException The {@code keyStore} provider does not support the necessary algorithms.
     * @throws KeyManagementException   The SSL context could not be initialized using the available private keys.
     */
    public synchronized APNClient setKeyManagerFactory(final KeyManagerFactory keyManagerFactory)
            throws NoSuchAlgorithmException, KeyManagementException {

        this.keyManagerFactory = keyManagerFactory;

        updateSSL();
        return this;
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
     *
     * @return <code>this</code> for chaining.
     *
     * @throws NoSuchAlgorithmException The {@code keyStore} provider does not support the necessary algorithms.
     * @throws KeyManagementException   The SSL context could not be initialized using the available private keys.
     */
    public synchronized APNClient setTrustManagerFactory(final TrustManagerFactory trustManagerFactory)
            throws NoSuchAlgorithmException, KeyManagementException {

        this.trustManagerFactory = trustManagerFactory;

        updateSSL();
        return this;
    }

    /**
     * @return The server configuration that is used to establish communication to the Apple services.
     */
    public synchronized APNServerConfig getServerConfig() {

        return serverConfig;
    }

    /**
     * @param serverConfig The server configuration that is used to establish communication to the Apple services.
     *
     * @return <code>this</code> for chaining.
     *
     * @throws NoSuchAlgorithmException The {@code keyStore} provider does not support the necessary algorithms.
     * @throws KeyManagementException   The SSL context could not be initialized using the available private keys.
     */
    public synchronized APNClient setServerConfig(final APNServerConfig serverConfig)
            throws NoSuchAlgorithmException, KeyManagementException {

        this.serverConfig = serverConfig;

        updateSSL();
        return this;
    }

    /**
     * @param apnsCallback A callback that should be invoked when an APNs response is received.
     *
     * @return <code>this</code> for chaining.
     */
    public APNClient setResponseCallback(final APNResponseCallback apnsCallback) {

        this.apnsCallback = apnsCallback;
        return this;
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
     *
     * @return <code>this</code> for chaining.
     */
    public APNClient setGson(final Gson gson) {

        this.gson = gson;
        return this;
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
     *
     * @return <code>this</code> for chaining.
     */
    public APNClient setIdentifierSupplier(final Supplier<Integer> identifierSupplier) {

        this.identifierSupplier = identifierSupplier;
        return this;
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
     *
     * @return <code>this</code> for chaining.
     */
    public APNClient setByteOrder(final ByteOrder byteOrder) {

        this.byteOrder = byteOrder;
        return this;
    }

    /**
     * <p> <b>Do not modify this property unless you have a very good reason to do so.</b> </p>
     *
     * @param maxPayloadSize The maximum allowed byte size of the serialized {@link APSPayload} encoded with {@link #getPayloadEncoding()}.
     *
     * @return <code>this</code> for chaining.
     */
    public APNClient setMaxPayloadSize(final short maxPayloadSize) {

        this.maxPayloadSize = maxPayloadSize;
        return this;
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
     * Obtain a reference to the {@link NioSocketConnector} used by this {@link APNClient} for its network connectivity.
     * <p/>
     * <p> <b>Accessing the {@link NioSocketConnector} directly is strongly discouraged.</b> {@link APNClient} should provide all features
     * need. This method is here mostly for extensibility and advanced usage. Be careful when going behind the back of {@link APNClient} is
     * ill-advised. You have been warned. </p>
     *
     * @return The network connector used by this {@link APNClient}.
     */
    public NioSocketConnector getConnector() {

        return connector;
    }

    /**
     * Close active connections to the Apple Push Notification server.
     * <p/>
     * <p> <b>Note:</b> This does not close any possible connections to the Apple Push Notification Feedback service. This connection is
     * expected to close automatically when Apple finished dumping its queue. Use {@link #closeFeedbackService()} if for some reason you
     * want to do this anyway. </p>
     */
    public synchronized void closeAPNs() {

        if (apnsSession == null || !apnsSession.isConnected() || apnsSession.isClosing())
            return;

        apnsSession.close( false );
        apnsSession = null;
    }

    /**
     * Close any active connection to the Apple Push Notification Feedback service.
     */
    public void closeFeedbackService() {

        if (feedbackSession == null || !feedbackSession.isConnected() || feedbackSession.isClosing())
            return;

        feedbackSession.close( false );
        feedbackSession = null;
    }

    private class APNsHandler extends StreamIoHandler {

        private final IoBuffer responseBuffer = IoBuffer.allocate(
                // Command
                Byte.SIZE / Byte.SIZE +
                // Status
                Byte.SIZE / Byte.SIZE +
                // Identifier
                Integer.SIZE / Byte.SIZE );

        @Override
        public void sessionOpened(final IoSession session) {

            logger.inf( "Connected to APNs" );

            responseBuffer.clear();
            responseBuffer.order( getByteOrder() );

            super.sessionOpened( session );
        }

        @Override
        public void sessionClosed(final IoSession session)
                throws Exception {

            logger.inf( "Disconnected from APNs" );

            responseBuffer.flip();
            byte command = responseBuffer.get();

            if (command == (byte) 8) {
                byte status = responseBuffer.get();
                int identifier = responseBuffer.getInt();
                final APNResponse response = new APNResponse( status, identifier );
                logger.inf( "Received APN response: %s", response );

                if (apnsCallback != null)
                    apnsCallback.responseReceived( response );
            } else {
                logger.wrn( "APN response command not implemented: %d", Byte.valueOf( command ).intValue() );
                responseBuffer.position( responseBuffer.limit() );
            }

            responseBuffer.compact();

            super.sessionClosed( session );
        }

        @Override
        protected void processStreamIo(final IoSession session, final InputStream in, final OutputStream out) {

            try {
                logger.dbg( "Received %d bytes of APNs response", in.available() );
                responseBuffer.put( ByteStreams.toByteArray( in ) );
            }
            catch (IOException e) {
                throw logger.bug( e );
            }
        }
    }
}
