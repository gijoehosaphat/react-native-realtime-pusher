# react-native-realtime-pusher
Implementing the Pusher Realtime API for Android and iOS.

## Installation Android ##

`yarn add react-native-realtime-pusher`

### In `settings.gradle` add the following lines:

```groovy
include ':Pusher'
project(':Pusher').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-realtime-pusher/android')
```

### In `build.gradle` add the following line:

```groovy
compile project(':Pusher')
```

### In `MainApplication.java` add the following lines:

```java
import com.gijoehosaphat.pusher.PusherPackage;
```

```java
new PusherPackage()
```

# Example usage:

```javascript
import Pusher from 'react-native-realtime-pusher'
...
// These values determined by *your* setup on *your* backend.
// hostname ~= http://myserver.com
// authPath ~= /auth
// channelPath ~= /channel
Pusher.initialize(hostname, authPath, channelPath, appKey, token) //This is a Promise
...
//Connect (There is also disconnect)
Pusher.connect() //This is a Promise
...
//Subscribe to a channel (There is also channelUnsubscribe)
Pusher.channelSubscribe(channel, channelEventName) //This is a Promise
...
//Send a message
Pusher.messagePost(messageObject, channelName, channelEvent) //This is a promise
...
//You will want to listen for events:
componentDidMount() {
  DeviceEventEmitter.addListener('pusher-event', this.yourEventHandler)
}
componentWillUnmount() {
  DeviceEventEmitter.removeAllListeners('pusher-event')
}
```
