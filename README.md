# react-native-realtime-pusher
Implementing the Pusher Realtime API.

***CURRENTLY ANDROID ONLY***

## Installation ##

`npm install react-native-realtime-pusher --save`

### In `settings.gradle` add the following lines:

```groovy
include ':Pusher'
project(':Pusher').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-realtime-pusher/android')
```

### In `build.gradle` add the following line:

```groovy
compile project(':Pusher')
```

### In `MainActivity.java` add the following lines:

```java
import com.gijoehosaphat.pusher.PusherPackage;
```

```java
new PusherPackage(this)
```

## Example usage:

```javascript
import Pusher from 'react-native-realtime-pusher'
```

## TODO:
1. Add similar support to iOS
