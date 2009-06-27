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

import java.util.Date;
import java.util.Map;


/**
 * <h2>{@link UninstalledDevicesCallback}<br>
 * <sub>Callback interface which is used to provide information about uninstalled applications.</sub></h2>
 * 
 * <p>
 * <i>Jun 26, 2009</i>
 * </p>
 * 
 * @author lhunath
 */
public interface UninstalledDevicesCallback {

    /**
     * Invoked by {@link APNClient} in response to network events caused by the
     * {@link APNClient#fetchUninstalledDevices(UninstalledDevicesCallback)}.
     * 
     * <p>
     * When the Apple Push Notification Feedback Service reports devices where the application has been uninstalled,
     * these devices are collected in a {@link Map} along with the time at which Apple noticed the application had
     * disappeared.
     * </p>
     * 
     * <p>
     * The returned {@link Map} is a direct reference to the {@link Map} used internally. It is synchronized, so it
     * should be thread-safe provided you obey synchronization rules (if you iterate over this map and/or modify it,
     * make sure to do so in a synchronized block which uses the map as mutex).
     * </p>
     * 
     * <p>
     * This method is invoked as soon as the responsible connection to the Apple Push Notification Feedback Service ends
     * (indicating Apple has finished sending us all of their data). It is invoked in a new thread (so, not the thread
     * in which you performed {@link APNClient#fetchUninstalledDevices(UninstalledDevicesCallback)} and not the
     * networking thread!).
     * </p>
     * 
     * <p>
     * After you've handled any events for one of the <code>uninstalledDevices</code>, you should remove it from this
     * map so that any subsequent calls of this method provide current data.
     * </p>
     * 
     * <p>
     * <b>Remember that the timestamp mapped by the {@link NotificationDevice} is only an indication. It only guarantees
     * that at that time the application was <i>unavailable</i> on the device. It does not guarantee the application was
     * removed, and if it was, it does not guarantee that the application hasn't been reinstalled since. Take this into
     * account when handling these events!</b>
     * </p>
     * 
     * @param uninstalledDevices
     *            The {@link Map} that is filled up from reported uninstalled devices. It maps the device on which the
     *            application used to be installed to the date on which Apple determined the application was no longer
     *            available.
     */
    public void detectedUninstalledDevices(Map<NotificationDevice, Date> uninstalledDevices);
}
