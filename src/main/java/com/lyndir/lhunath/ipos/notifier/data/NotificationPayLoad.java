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
package com.lyndir.lhunath.ipos.notifier.data;

import net.sf.json.JSONString;
import net.sf.json.util.JSONBuilder;
import net.sf.json.util.JSONStringer;


/**
 * <h2>{@link NotificationPayLoad}<br>
 * <sub>The payload for an APNs notification message.</sub></h2>
 * 
 * <p>
 * The payload describes the parameters for the actual notification. This involves whether to display an alert message,
 * whether to set a badge on the application icon or whether to play a sound.
 * </p>
 * <p>
 * <i>Jun 23, 2009</i>
 * </p>
 * 
 * 
 * @see <a
 *      href="http://developer.apple.com/iphone/library/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/ApplePushService/ApplePushService.html#//apple_ref/doc/uid/TP40008194-CH100-SW1">Apple
 *      Push Notification Service Programming Guide: The Notification Payload</a>
 * 
 * @author lhunath
 */
public class NotificationPayLoad implements JSONString {

    /**
     * Create a payload for sending a notification that shows an alert message with a static body.
     * 
     * @param alertBody
     *            The static string to show in the alert message.
     * 
     * @return A notification payload with just an alert that has just a body and a single button.
     */
    public static NotificationPayLoad createSimpleAlert(String alertBody) {

        NotificationPayLoad notificationPayLoad = new NotificationPayLoad();
        Alert alert = new Alert();

        alert.setBody( alertBody );
        notificationPayLoad.setAlert( alert );

        return notificationPayLoad;
    }

    /**
     * Create a payload for sending a notification that shows an alert message with a localized body.
     * 
     * @param alertBodyKey
     *            The localization key (from the application's <code>Localizable.strings</code>) that maps this alert
     *            message's body.
     * @param alertBodyArgs
     *            The arguments to use for expanding format specifiers in the mapped alert message's body.
     * 
     * @return A notification payload with just an alert that has just a body and a single button.
     */
    public static NotificationPayLoad createLocalizedAlert(String alertBodyKey, Object... alertBodyArgs) {

        NotificationPayLoad notificationPayLoad = new NotificationPayLoad();
        Alert alert = new Alert();

        alert.setLocKey( alertBodyKey );
        alert.setLocArgs( alertBodyArgs );
        notificationPayLoad.setAlert( alert );

        return notificationPayLoad;
    }

    /**
     * Create a payload for sending a notification that causes a sound effect to be played on the destination device.
     * 
     * @param soundPath
     *            The application's bundle path to the sound file that contains the sound effect that should be played.
     * 
     * @return A notification payload which just plays a sound effect.
     */
    public static NotificationPayLoad createSound(String soundPath) {

        NotificationPayLoad notificationPayLoad = new NotificationPayLoad();

        notificationPayLoad.setSound( soundPath );

        return notificationPayLoad;
    }

    /**
     * Create a payload for sending a notification that sets the application's badge count.
     * 
     * @param badgeCount
     *            The count to display in the application icon's badge on the user's home screen.
     * 
     * @return A notification payload which just sets the application's badge count.
     */
    public static NotificationPayLoad createBadge(Integer badgeCount) {

        NotificationPayLoad notificationPayLoad = new NotificationPayLoad();

        notificationPayLoad.setBadge( badgeCount );

        return notificationPayLoad;
    }


    /**
     * <h2>{@link Alert}<br>
     * <sub>An alert message to display as a result of an APNs notification.</sub></h2>
     * 
     * <p>
     * Alert messages can contain just a body or they can specify localization keys as defined in the destination
     * application's <code>Localizable.strings</code>.
     * </p>
     * 
     * <p>
     * <i>Jun 23, 2009</i>
     * </p>
     * 
     * @see <a
     *      href="http://developer.apple.com/iphone/library/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/ApplePushService/ApplePushService.html#//apple_ref/doc/uid/TP40008194-CH100-SW20">Apple
     *      Push Notification Service Programming Guide: The Notification Payload: Table 2-2 Child properties of the
     *      alert property</a>
     * 
     * @author lhunath
     */
    public static class Alert {

        private String   body;
        private String   actionLocKey;
        private String   locKey;
        private Object[] locArgs;


        /**
         * @param body
         *            A static, non-localized message to display in the alert message.
         */
        public void setBody(String body) {

            this.body = body;
        }

        /**
         * @return A static, non-localized message to display in the alert message.
         */
        public String getBody() {

            return body;
        }

        /**
         * @param actionLocKey
         *            The localization key for the action button in the alert message that opens the application when
         *            tapped. When <code>null</code> only one button is shown that dismisses the notification.
         */
        public void setActionLocKey(String actionLocKey) {

            this.actionLocKey = actionLocKey;
        }

        /**
         * @return The localization key for the action button in the alert message that opens the application when
         *         tapped. When <code>null</code> only one button is shown that dismisses the notification.
         */
        public String getActionLocKey() {

            return actionLocKey;
        }

        /**
         * @param locKey
         *            The localization key for the body message to display in this alert message. Use this instead of
         *            {@link #setBody(String)} if you want to display a localizable alert message. The localization
         *            value can contain format specifiers which will be expanded from the values in
         *            {@link #getLocArgs()}.
         */
        public void setLocKey(String locKey) {

            this.locKey = locKey;
        }

        /**
         * @return The localization key for the body message to display in this alert message.
         */
        public String getLocKey() {

            return locKey;
        }

        /**
         * @param locArgs
         *            The arguments that expand the format specifiers in the localization value referenced by
         *            {@link #getLocKey()}. See {@link JSONBuilder#value(Object)} for a reference of the types allowed
         *            for this argument's value.
         */
        public void setLocArgs(Object... locArgs) {

            this.locArgs = locArgs;
        }

        /**
         * @return The arguments that expand the format specifiers in the localization value referenced by
         *         {@link #getLocKey()}.
         */
        public Object[] getLocArgs() {

            return locArgs;
        }
    }


    private Alert   alert;
    private Integer badge;
    private String  sound;


    /**
     * @param alert
     *            The alert message to display as a result of this notification or <code>null</code> if no alert message
     *            should be displayed.
     */
    public void setAlert(Alert alert) {

        this.alert = alert;
    }

    /**
     * @return The alert message to display as a result of this notification or <code>null</code> if no alert message
     *         should be displayed.
     */
    public Alert getAlert() {

        return alert;
    }

    /**
     * @param badge
     *            The number to display on the application's badge or <code>null</code> if this notification should
     *            remove the badge.
     */
    public void setBadge(Integer badge) {

        this.badge = badge;
    }

    /**
     * @return The number to display on the application's badge or <code>null</code> if this notification should remove
     *         the badge.
     */
    public Integer getBadge() {

        return badge;
    }

    /**
     * @param sound
     *            The application bundle path of the sound file to play for this notification or <code>null</code> if
     *            this notification doesn't play a sound.
     */
    public void setSound(String sound) {

        this.sound = sound;
    }

    /**
     * @return The application bundle path of the sound file to play for this notification or <code>null</code> if this
     *         notification doesn't play a sound.
     */
    public String getSound() {

        return sound;
    }

    /**
     * {@inheritDoc}
     */
    public String toJSONString() {

        JSONBuilder jsonStringer = new JSONStringer().object();

        if (alert != null)
            // Attach the alert message.
            if (alert.getBody() != null && alert.getActionLocKey() == null && alert.getLocKey() == null)
                // Alert message has only a body.
                jsonStringer.key( "alert" ).value( alert.getBody() );
            else {
                // Alert message has either no body or also other keys.
                jsonStringer.key( "alert" ).object();

                if (alert.getBody() != null)
                    jsonStringer.key( "body" ).value( alert.getBody() );

                if (alert.getActionLocKey() != null)
                    jsonStringer.key( "action-loc-key" ).value( alert.getActionLocKey() );

                if (alert.getLocKey() != null)
                    jsonStringer.key( "loc-key" ).value( alert.getLocKey() );

                if (alert.getLocArgs() != null && alert.getLocArgs().length > 0) {
                    jsonStringer.key( "loc-args" ).array();
                    for (Object arg : alert.getLocArgs())
                        jsonStringer.value( arg );
                    jsonStringer.endArray();
                }

                jsonStringer.endObject();
            }

        if (getBadge() != null)
            // Attach the badge count.
            jsonStringer.key( "badge" ).value( getBadge() );

        if (getSound() != null)
            // Attach the sound path.
            jsonStringer.key( "sound" ).value( getSound() );

        return jsonStringer.endObject().toString();
    }
}
