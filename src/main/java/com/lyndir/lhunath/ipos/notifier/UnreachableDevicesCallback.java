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
import com.lyndir.lhunath.ipos.notifier.impl.APNClient;
import java.util.Date;
import java.util.Map;


/**
 * <h2>{@link UnreachableDevicesCallback}<br> <sub>Callback interface which is used to provide information about devices that have become
 * unreachable.</sub></h2>
 *
 * <p> <i>Jun 26, 2009</i> </p>
 *
 * @author lhunath
 */
public interface UnreachableDevicesCallback {

    /**
     * Invoked by {@link APNClient} in response to network events caused by the {@link APNClient#fetchUnreachableDevices(UnreachableDevicesCallback)}.
     *
     * <p> When the Apple Push Notification Feedback Service reports devices that did not respond to recent push notifications directed at
     * them, these devices are collected in a {@link Map} along with the time at which the Apple Push Notification server first noticed
     * these devices having become unreachable. </p>
     *
     * <p> The returned {@link Map} is a direct reference to the {@link Map} used internally. It is synchronized, so it should be
     * thread-safe provided you obey synchronization rules (if you iterate over this map and/or modify it, make sure to do so in a
     * synchronized block which uses the map as mutex). </p>
     *
     * <p> This method is invoked as soon as the connection to the Apple Push Notification Feedback Service ends (indicating Apple has
     * finished sending us all of their data). Its execution runs in a new thread (so, not the thread in which you performed {@link
     * APNClient#fetchUnreachableDevices(UnreachableDevicesCallback)} and not the networking thread!). </p>
     *
     * <p> As soon as you've handled any of the entries in <code>uninstalledDevices</code> you should remove them from the map so that any
     * subsequent calls of this method provide current data. </p>
     *
     * <p> <b>Remember that the timestamp mapped by the {@link NotificationDevice} is only an indication. It only guarantees that at that
     * time the application was <i>unavailable</i> on the device. It does not guarantee the application was removed, and if it was, it does
     * not guarantee that the application hasn't been reinstalled since. Take this into account when handling these events!</b> </p>
     *
     * @param uninstalledDevices The {@link Map} that contains the devices on which the application was unreachable. It maps the notified
     *                           device to the date on which Apple first determined the notified application to be unreachable.
     */
    void foundUnreachableDevices(Map<NotificationDevice, Date> uninstalledDevices);
}
