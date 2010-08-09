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

import com.lyndir.lhunath.ipos.notifier.data.NotificationDevice;
import com.lyndir.lhunath.ipos.notifier.data.NotificationPayLoad;
import com.lyndir.lhunath.ipos.notifier.impl.APNQueue;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import net.sf.json.JSONObject;


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
     * @param callback The instance to notify when the unreachable devices have been determined. Use <code>null</code> if you're not
     *                 interested in feedback but just want to clear the Apple Push Notification Feedback Service's data queue.
     *
     * @throws IOException              If the system failed to initiate a connection to the Apple Feedback service.
     * @throws NoSuchAlgorithmException The <code>keyStore</code> provider does not support the necessary algorithms.
     * @throws KeyManagementException   The SSL context could not be initialized using the available private keys.
     */
    void fetchUnreachableDevices(UnreachableDevicesCallback callback)
            throws IOException, KeyManagementException, NoSuchAlgorithmException;

    /**
     * Queue a notification to be sent to the APNs through the {@link APNQueue}.
     *
     * <p> This is a convenience variant of the {@link #queueNotification(NotificationDevice, JSONObject, NotificationPayLoad...)} method
     * that does not pass any custom data. </p>
     *
     * @param device               The device that is the notification's destination.
     * @param notificationPayLoads The notification payloads. These payloads describe the actual events the notification should trigger on
     *                             the device. You can specify multiple notification events, and must specify at least one.
     *
     * @return <code>false</code>: The notification queue is full. Wait for it to get emptied by the {@link APNQueue} thread before trying
     *         again.
     */
    boolean queueNotification(NotificationDevice device, NotificationPayLoad... notificationPayLoads);

    /**
     * Queue a notification to be sent to the APNs through the {@link APNQueue}.
     *
     * @param device               The device that is the notification's destination.
     * @param customData           Any optional custom data you want to pass to the application. Remember that the total payload size (this
     *                             and the <code>notificationPayLoads</code> combined) is limited to <code>256 bytes</code>, so be modest.
     *                             You can specify <code>null</code> here to omit any custom data.
     * @param notificationPayLoads The notification payloads. These payloads describe the actual events the notification should trigger on
     *                             the device. You can specify multiple notification events, and must specify at least one.
     *
     * @return <code>false</code>: The notification queue is full. Wait for it to get emptied by the {@link APNQueue} thread before trying
     *         again.
     */
    boolean queueNotification(NotificationDevice device, JSONObject customData, NotificationPayLoad... notificationPayLoads);

    /**
     * Queue a notification to be sent to the APNs through the {@link APNQueue}.
     *
     * <p> You should probably use {@link #queueNotification(NotificationDevice, JSONObject, NotificationPayLoad...)} instead. </p>
     *
     * @param device               The device that is the notification's destination.
     * @param customData           Any optional custom data you want to pass to the application. Remember that the total payload size (this
     *                             and the <code>notificationPayLoads</code> combined) is limited to <code>256 bytes</code>, so be modest.
     *                             You can specify <code>null</code> here to omit any custom data.
     * @param notificationPayLoads The JSON representation of the notification payloads. These payloads describe the actual events the
     *                             notification should trigger on the device. You can specify multiple notification events, and must specify
     *                             at least one.
     *
     * @return <code>false</code>: The notification queue is full. Wait for it to get emptied by the {@link APNQueue} thread before trying
     *         again.
     */
    boolean queueNotification(NotificationDevice device, JSONObject customData, JSONObject... notificationPayLoads);
}
