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
package com.lyndir.lhunath.ios.notifier.data;

import com.google.gson.annotations.Expose;
import com.lyndir.lhunath.opal.system.util.ObjectMeta;


/**
 * <h2>{@link APSSimpleAlertPayload}<br> <sub>The payload for an APNs notification message.</sub></h2>
 *
 * <p> The payload describes the parameters for the actual notification. This involves whether to display an alert message, whether to set a
 * badge on the application icon or whether to play a sound. </p> <p> <i>Jun 23, 2009</i> </p>
 *
 * @author lhunath
 * @see <a href="http://developer.apple.com/iphone/library/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/ApplePushService/ApplePushService.html#//apple_ref/doc/uid/TP40008194-CH100-SW1">Apple
 *      Push Notification Service Programming Guide: The Notification Payload</a>
 */
@ObjectMeta
public class APSSimpleAlertPayload extends APSPayload {

    @Expose
    private final String alert;

    public APSSimpleAlertPayload(final String alert, final Integer badge, final String sound) {

        super( badge, sound );

        this.alert = alert;
    }
}
