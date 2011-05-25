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
import com.lyndir.lhunath.opal.system.util.ObjectUtils;


/**
 * <h2>{@link APSPayload}<br> <sub>The payload for an APNs notification message.</sub></h2>
 *
 * <p> The payload describes the parameters for the actual notification. This involves whether to display an alert message, whether to set a
 * badge on the application icon or whether to play a sound. </p> <p> <i>Jun 23, 2009</i> </p>
 *
 * @author lhunath
 * @see <a href="http://developer.apple.com/iphone/library/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/ApplePushService/ApplePushService.html#//apple_ref/doc/uid/TP40008194-CH100-SW1">Apple
 *      Push Notification Service Programming Guide: The Notification Payload</a>
 */
@ObjectMeta
public abstract class APSPayload {

    /**
     * Create a payload for sending a notification that shows an alert message with a static body.
     *
     * @param alertBody The static string to show in the alert message.
     *
     * @return A notification payload with just an alert that has just a body and a single button.
     */
    public static APSPayload showSimpleAlert(final String alertBody) {

        return new APSSimpleAlertPayload( alertBody, null, null );
    }

    /**
     * Create a payload for sending a notification that shows an alert message with a localized body.
     *
     * @param alertActionKey The localization key (from the application's {@code Localizable.strings}) that maps this alert message's
     *                       "View" button text.
     * @param alertBodyKey   The localization key (from the application's {@code Localizable.strings}) that maps this alert message's
     *                       body.
     * @param alertBodyArgs  The arguments to use for expanding format specifiers in the mapped alert message's body.
     *
     * @return A notification payload with just an alert that has just a body and a single button.
     */
    public static APSPayload showLocalizedAlert(final String alertActionKey, final String alertBodyKey, final Object... alertBodyArgs) {

        return new APSLocalizedAlertPayload( new APSLocalizedAlertPayload.Alert( null, alertActionKey, alertBodyKey, alertBodyArgs ), null,
                null );
    }

    /**
     * Create a payload for sending a notification that causes a sound effect to be played on the destination device.
     *
     * @param sound The application's bundle path to the sound file that contains the sound effect that should be played.
     *
     * @return A notification payload which just plays a sound effect.
     */
    public static APSPayload playSound(final String sound) {

        return new APSSimpleAlertPayload( null, null, sound );
    }

    /**
     * Create a payload for sending a notification that sets the application's badge count.
     *
     * @param badge The count to display in the application icon's badge on the user's home screen.
     *
     * @return A notification payload which just sets the application's badge count.
     */
    public static APSPayload setBadge(final Integer badge) {

        return new APSSimpleAlertPayload( null, badge, null );
    }

    @Expose
    private final Integer badge;
    @Expose
    private final String  sound;

    protected APSPayload(final Integer badge, final String sound) {

        this.badge = badge;
        this.sound = sound;
    }

    @Override
    public String toString() {

        return ObjectUtils.toString( this );
    }

    @Override
    public int hashCode() {

        return ObjectUtils.hashCode( this );
    }

    @Override
    public boolean equals(final Object obj) {

        return ObjectUtils.equals( this, obj );
    }
}
