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
package com.lyndir.lhunath.ios.notifier;

import com.lyndir.lhunath.ios.notifier.data.NotificationDevice;
import com.lyndir.lhunath.ios.notifier.data.Payload;
import com.lyndir.lhunath.ios.notifier.impl.APNQueue;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import org.jetbrains.annotations.Nullable;


/**
 * <h2>{@link APNClientService}<br> <sub>[in short] (TODO).</sub></h2>
 *
 * <p> [description / usage]. </p>
 *
 * <p> <i>Aug 7, 2009</i> </p>
 *
 * @author lhunath
 */
public interface APNClientService {

    /**
     * Fetch unreachable devices from Apple's Feedback service.
     *
     * @param callback The instance to notify when the unreachable devices have been determined. Use {@code null} if you're not
     *                 interested in feedback but just want to clear the Apple Push Notification Feedback Service's data queue.
     *
     * @throws IOException              If the system failed to initiate a connection to the Apple Feedback service.
     * @throws NoSuchAlgorithmException The {@code keyStore} provider does not support the necessary algorithms.
     * @throws KeyManagementException   The SSL context could not be initialized using the available private keys.
     */
    void fetchUnreachableDevices(UnreachableDevicesCallback callback)
            throws IOException, KeyManagementException, NoSuchAlgorithmException;

    /**
     * Queue a notification to be sent to the APNs through the {@link APNQueue}.
     *
     * @param device     The device that is the notification's destination.
     * @param payload    The payload that describes the notification to send to the client. Remember that the total payload size is limited
     *                   to {@code 256 bytes}, so be modest.
     * @param expiryDate The date and time at which this notification should expire. If the notification cannot be delivered before this
     *                   time, it will be discarded.
     *
     * @return The unique identifier that was assigned to this push message, or {@code null} if the notification queue is full.  If the
     *         queue is full, you should wait for it to get emptied by the {@link APNQueue} thread before trying again.
     */
    @Nullable
    Integer queueNotification(NotificationDevice device, Payload payload, Date expiryDate);
}
