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

import com.lyndir.lhunath.ios.notifier.data.APNRegistration;
import com.lyndir.lhunath.ios.notifier.data.Payload;
import com.lyndir.lhunath.ios.notifier.impl.APNQueue;
import java.util.Date;
import java.util.Map;
import org.jetbrains.annotations.Nullable;


/**
 * <h2>{@link APNClientService}<br> <sub>[in short] (TODO).</sub></h2>
 * <p/>
 * <p> [description / usage]. </p>
 * <p/>
 * <p> <i>Aug 7, 2009</i> </p>
 *
 * @author lhunath
 */
public interface APNClientService {

    /**
     * Query the Apple push notification feedback service for a list of devices that have become unreachable.  This includes all APN
     * registrations that a notification was sent to but could not be delivered by the APS.
     *
     * @return A map of devices that have become unavailable since the last time the feedback service was polled.  The device is mapped to
     *         the date that the APS noticed that the registration became unreachable.
     *
     * @throws APNException         If the system failed to initiate a connection or write the notification to the APS.
     * @throws InterruptedException If the network was interrupted while connecting or sending the notification to the APS.
     */
    Map<APNRegistration, Date> fetchUnreachableDevices()
            throws InterruptedException, APNException;

    /**
     * Queue a notification to be sent to the APS through the {@link APNQueue}.
     *
     * @param device     The device registration that should receive the notification.
     * @param payload    The payload that describes the notification to send to the client. Remember that the total payload size is limited
     *                   to {@code 256 bytes}, so be modest.
     * @param expiryDate The date and time at which this notification should expire. If the notification cannot be delivered before this
     *                   time, it will be discarded.
     *
     * @return The unique identifier that was assigned to this push message.
     */
    @Nullable
    int queue(APNRegistration device, Payload payload, Date expiryDate);
}
