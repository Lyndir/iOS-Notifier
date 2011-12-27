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
 * <h2>{@link APSLocalizedAlertPayload}<br> <sub>The payload for an Apple push notification message.</sub></h2>
 * <p/>
 * <p> The payload describes the parameters for the actual notification. This involves whether to display an alert message, whether to set
 * a
 * badge on the application icon or whether to play a sound. </p> <p> <i>Jun 23, 2009</i> </p>
 *
 * @author lhunath
 * @see <a href="http://developer.apple.com/iphone/library/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/ApplePushService/ApplePushService.html#//apple_ref/doc/uid/TP40008194-CH100-SW1">Apple
 *      Push Notification Service Programming Guide: The Notification Payload</a>
 */
@ObjectMeta
public class APSLocalizedAlertPayload extends APSPayload {

    /**
     * <h2>{@link Alert}<br> <sub>An alert message to display as a result of a notification.</sub></h2>
     * <p/>
     * <p> Alert messages can contain just a body or they can specify localization keys as defined in the destination application's
     * {@code Localizable.strings}. </p>
     * <p/>
     * <p> <i>Jun 23, 2009</i> </p>
     *
     * @author lhunath
     * @see <a href="http://developer.apple.com/iphone/library/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/ApplePushService/ApplePushService.html#//apple_ref/doc/uid/TP40008194-CH100-SW20">Apple
     *      Push Notification Service Programming Guide: The Notification Payload: Table 2-2 Child properties of the alert property</a>
     */
    @ObjectMeta
    public static class Alert {

        @Expose
        private final String   body;
        @Expose
        private final String   actionLocKey;
        @Expose
        private final String   locKey;
        @Expose
        private final Object[] locArgs;

        public Alert(final String body, final String actionLocKey, final String locKey, final Object... locArgs) {

            this.body = body;
            this.actionLocKey = actionLocKey;
            this.locKey = locKey;
            this.locArgs = locArgs;
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


    @Expose
    private final Alert alert;

    public APSLocalizedAlertPayload(final Alert alert, final Integer badge, final String sound) {

        super( badge, sound );

        this.alert = alert;
    }
}
