package com.gijoehosaphat.pusher;

import android.app.Activity;
import android.app.Application;
import android.view.WindowManager;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Callback;
import okhttp3.Call;
import okhttp3.MediaType;

import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.User;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.PrivateChannel;
import com.pusher.client.channel.PresenceChannel;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.channel.PrivateChannelEventListener;
import com.pusher.client.channel.PresenceChannelEventListener;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.util.HttpAuthorizer;

import org.json.JSONObject;
import org.json.JSONStringer;

import java.util.Date;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.io.IOException;

public class PusherModule extends ReactContextBaseJavaModule {
    private Activity mActivity = null;
    private ReactApplicationContext mContext = null;
    private String connectionState = null;

    private String authEndPoint = null;
    private String authToken = null;
    private String messageEndPoint = null;
    private String appKey = null;
    private Pusher pusher = null;
    private List<String> channels = new ArrayList<String>();

    public PusherModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.mActivity = getCurrentActivity();
        this.mContext = reactContext;
    }

    @Override
    public String getName() {
        return "Pusher";
    }

    @ReactMethod
    public void initialize(String host, String authPath, String messageSubPath, String appKey, String authToken, Promise promise) {
        this.authEndPoint = host + authPath;
        this.authToken = authToken;
        this.messageEndPoint = host + messageSubPath;
        this.appKey = appKey;

        WritableMap map = Arguments.createMap();
        map.putString("host", host);
        map.putString("authPath", authPath);
        map.putString("messageSubPath", messageSubPath);
        map.putString("appKey", appKey);
        map.putString("authToken", authToken);
        promise.resolve(map);
    }

    @ReactMethod
    public void connect(Promise promise) {
        try {
          //Define our authorization headers...
          HashMap<String, String> authHeaders = new HashMap<>();
          authHeaders.put("Content-Type", "application/x-www-form-urlencoded");
          authHeaders.put("Authorization", this.authToken);

          //Set up our HttpAuthorizer
          HttpAuthorizer authorizer = new HttpAuthorizer(this.authEndPoint);
          authorizer.setHeaders(authHeaders);

          //Apply to pusher options and create our Pusher object.
          PusherOptions options = new PusherOptions().setEncrypted(true).setAuthorizer(authorizer);
          this.pusher = new Pusher(appKey, options);

          //Connect and handle events...
          this.pusher.connect(new ConnectionEventListener() {
              @Override
              public void onConnectionStateChange(ConnectionStateChange change) {
                  setConnectionState(change.getCurrentState().toString());
                  WritableMap params = Arguments.createMap();
                  params.putString("eventName", "connectionStateChange");
                  params.putString("currentState", change.getCurrentState().toString());
                  params.putString("previousState", change.getPreviousState().toString());
                  sendEvent(params);
              }

              @Override
              public void onError(String message, String code, Exception e) {
                  WritableMap params = Arguments.createMap();
                  params.putString("eventName", "connectionStateChange");
                  params.putString("message", message);
                  params.putString("code", code);
                  sendEvent(params);
              }
          }, ConnectionState.ALL);

          promise.resolve(true);
        } catch(Exception ex) {
          promise.reject(ex);
        }
    }

    @ReactMethod
    public void disconnect(Promise promise) {
        try {
          //Unsubscribe to all channels...
          for (int i = 0; i < channels.size(); i++) {
              this.pusher.unsubscribe(channels.get(i));
          }
          channels = new ArrayList<String>();

          //Disconnect
          this.pusher.disconnect();
          promise.resolve(true);
        } catch (Exception ex) {
          promise.reject(ex);
        }
    }

    //Set our connectionState on the instance...
    private void setConnectionState(String newConnectionState) {
        this.connectionState = newConnectionState;
    }

    //Allow for ReactNative component to use this value to seed their initial
    //state. Default value of Null or not we still report this value.
    @ReactMethod
    public void getConnectionState(Promise promise) {
        promise.resolve(this.connectionState);
    }

    @ReactMethod
    public void channelSubscribe(String channelName, String channelEventName, Promise promise) {
        try {
            if (channelName.startsWith("private-")) {
                channelPrivateSubscribe(channelName, channelEventName);
            } else if (channelName.startsWith("presence-")) {
                channelPresenceSubscribe(channelName, channelEventName);
            } else {
                channelPublicSubscribe(channelName, channelEventName);
            }
            WritableMap map = Arguments.createMap();
            map.putString("channelName", channelName);
            map.putString("channelEventName", channelEventName);
            promise.resolve(map);
        } catch (Exception ex) {
            promise.reject(ex);
        }
    }

    private void channelPublicSubscribe(final String channelName, final String channelEventName) {
        if (!channelIsSubscribed(channelName) && this.pusher != null) {
            Channel channel = this.pusher.subscribe(channelName, new ChannelEventListener() {
                @Override
                public void onSubscriptionSucceeded(String channelName) {
                    onChannelSubscriptionSucceeded(channelName);
                }

                @Override
                public void onEvent(String channelName, String eventName, final String data) {
                    onChannelEvent(channelName, eventName, data);
                }
            }, channelEventName);
            channels.add(channelName);
        }
    }

    private void channelPrivateSubscribe(final String channelName, final String channelEventName) {
        if (!channelPrivateIsSubscribed(channelName) && this.pusher != null) {
            PrivateChannel channel = this.pusher.subscribePrivate(channelName, new PrivateChannelEventListener() {
                @Override
                public void onSubscriptionSucceeded(String channelName) {
                    onChannelSubscriptionSucceeded(channelName);
                }

                @Override
                public void onAuthenticationFailure(String message, Exception e) {
                    onChannelAuthenticationFailure(message, channelName, e);
                }

                @Override
                public void onEvent(String channelName, String eventName, final String data) {
                    onChannelEvent(channelName, eventName, data);
                }
            }, channelEventName);
            channels.add(channelName);
        }
    }

    private void channelPresenceSubscribe(final String channelName, final String channelEventName) {
        if (!channelPresenceIsSubscribed(channelName) && this.pusher != null) {
            PresenceChannel channel = this.pusher.subscribePresence(channelName, new PresenceChannelEventListener() {
                @Override
                public void onSubscriptionSucceeded(String channelName) {
                    onChannelSubscriptionSucceeded(channelName);
                }

                @Override
                public void onAuthenticationFailure(String message, Exception e) {
                    onChannelAuthenticationFailure(message, channelName, e);
                }

                @Override
                public void onEvent(String channelName, String eventName, final String data) {
                    onChannelEvent(channelName, eventName, data);
                }

                @Override
                public void onUsersInformationReceived(final String channelName, final Set<User> users) {
                    WritableArray usersArray = Arguments.createArray();
                    Iterator<User> iterator = users.iterator();
                    while(iterator.hasNext()) {
                        User user = iterator.next();
                        WritableMap userMap = Arguments.createMap();
                        userMap.putString("id", user.getId());
                        userMap.putString("data", user.getInfo());
                        usersArray.pushMap(userMap);
                    }
                    WritableMap params = Arguments.createMap();
                    params.putString("eventName", "onUsersInformationReceived");
                    params.putString("channelName", channelName);
                    params.putArray("users", usersArray);
                    sendEvent(params);
                }

                @Override
                public void userSubscribed(final String channelName, final User user) {
                    WritableMap userMap = Arguments.createMap();
                    userMap.putString("id", user.getId());
                    userMap.putString("data", user.getInfo());
                    WritableMap params = Arguments.createMap();
                    params.putString("eventName", "userSubscribed");
                    params.putString("channelName", channelName);
                    params.putMap("user", userMap);
                    sendEvent(params);
                }

                @Override
                public void userUnsubscribed(final String channelName, final User user) {
                    WritableMap userMap = Arguments.createMap();
                    userMap.putString("id", user.getId());
                    userMap.putString("data", user.getInfo());
                    WritableMap params = Arguments.createMap();
                    params.putString("eventName", "userUnsubscribed");
                    params.putString("channelName", channelName);
                    params.putMap("user", userMap);
                    sendEvent(params);
                }
            }, channelEventName);
            channels.add(channelName);
        }
    }

    @ReactMethod
    public void channelUnsubscribe(String channelName, Promise promise) {
        try {
          this.pusher.unsubscribe(channelName);
          WritableMap map = Arguments.createMap();
          map.putString("channelName", channelName);
          promise.resolve(map);
        } catch (Exception ex) {
          promise.reject(ex);
        }
    }

    private Boolean channelIsSubscribed(String channelName) {
        Channel channel = null;
        if (this.pusher != null) {
            channel = this.pusher.getChannel(channelName);
        }
        return channel == null ? false : channel.isSubscribed();
    }

    private Boolean channelPrivateIsSubscribed(String channelName) {
        PrivateChannel channel = null;
        if (this.pusher != null) {
            channel = this.pusher.getPrivateChannel(channelName);
        }
        return channel == null ? false : channel.isSubscribed();
    }

    private Boolean channelPresenceIsSubscribed(String channelName) {
        PresenceChannel channel = null;
        if (this.pusher != null) {
            channel = this.pusher.getPresenceChannel(channelName);
        }
        return channel == null ? false : channel.isSubscribed();
    }

    @ReactMethod
    public void messagePost(ReadableMap messageObject, final String channelName, final String channelEvent, Promise promise) {
        try {
          Map<String, Object> map = recursivelyDeconstructReadableMap(messageObject);
          Gson gson = new Gson();
          String json = gson.toJson(map);

          OkHttpClient client = new OkHttpClient();
          Request request = new Request.Builder()
              .url(this.messageEndPoint + "/" + channelName + "/" + channelEvent)
              .header("Authorization", this.authToken)
              .addHeader("Content-Type", "application/json")
              .post(RequestBody.create(MediaType.parse("application/json"), json))
              .build();

          client.newCall(request).enqueue(new Callback() {
              @Override public void onResponse(Call call, Response response) throws IOException {
                  if (!response.isSuccessful()) {
                      WritableMap params = Arguments.createMap();
                      params.putString("eventName", "onMessageFailure");
                      params.putString("channelName", channelName);
                      params.putString("exception", response.toString());
                      sendEvent(params);
                  }

                  WritableMap params = Arguments.createMap();
                  params.putString("eventName", "onMessageSuccess");
                  params.putString("channelName", channelName);
                  sendEvent(params);
              }

              @Override public void onFailure(Call call, IOException e) {
                  WritableMap params = Arguments.createMap();
                  params.putString("eventName", "onMessageFailure");
                  params.putString("error", e.getMessage());
                  params.putString("channelName", channelName);
                  sendEvent(params);
              }
          });
          WritableMap responseMap = Arguments.createMap();
          responseMap.putString("channelName", channelName);
          responseMap.putString("channelEvent", channelEvent);
          promise.resolve(responseMap);
        } catch (Exception ex) {
          promise.reject(ex);
        }
    }

    //A general Channel event...
    private void onChannelEvent(String channelName, String eventName, final String data) {
        Gson gson = new Gson();
        LinkedTreeMap<String, Object> mapFields = gson.fromJson(data, LinkedTreeMap.class);
        WritableMap writableMap = recursivelyDeconstructMap(mapFields);

        WritableMap params = Arguments.createMap();
        params.putString("eventName", eventName);
        params.putString("channelName", channelName);
        params.putMap("data", writableMap);
        sendEvent(params);
    }

    //When a channel successfully subscribes...
    private void onChannelSubscriptionSucceeded(String channelName) {
        WritableMap params = Arguments.createMap();
        params.putString("eventName", "onSubscriptionSucceeded");
        params.putString("channelName", channelName);
        sendEvent(params);
    }

    //When authentication fails trying to subscribe to a private or presence channel
    private void onChannelAuthenticationFailure(String message, String channelName, Exception e) {
        WritableMap params = Arguments.createMap();
        params.putString("eventName", "onAuthenticationFailure");
        params.putString("channelName", channelName);
        params.putString("message", message);
        params.putString("exception", e.toString());
        sendEvent(params);
    }

    //Send our events to the RN client
    private void sendEvent(WritableMap params) {
        this.mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("pusher-event", params);
    }

    private WritableMap recursivelyDeconstructMap(LinkedTreeMap<String, Object> map) {
        WritableMap writableMap = Arguments.createMap();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() == null) {
                writableMap.putNull(entry.getKey());
            } else if (entry.getValue().getClass() == String.class) {
                writableMap.putString(entry.getKey(), (String)entry.getValue());
            } else if (entry.getValue().getClass() == Double.class) {
                writableMap.putDouble(entry.getKey(), (Double)entry.getValue());
            } else if (entry.getValue().getClass() == Integer.class) {
                writableMap.putInt(entry.getKey(), (Integer)entry.getValue());
            } else if (entry.getValue().getClass() == LinkedTreeMap.class) {
                writableMap.putMap(entry.getKey(), recursivelyDeconstructMap((LinkedTreeMap)entry.getValue()));
            } else if (entry.getValue().getClass() == ArrayList.class) {
                writableMap.putArray(entry.getKey(), recursivelyDeconstructList((ArrayList)entry.getValue()));
            } else {
                System.out.println("Class: " + entry.getValue().getClass().toString());
            }
        }
        return writableMap;
    }

    private WritableArray recursivelyDeconstructList(ArrayList<Object> list) {
        WritableArray writableArray = Arguments.createArray();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null) {
                writableArray.pushNull();
            } else if (list.get(i).getClass() == String.class) {
                writableArray.pushString((String)list.get(i));
            } else if (list.get(i).getClass() == Double.class) {
                writableArray.pushDouble((Double)list.get(i));
            } else if (list.get(i).getClass() == Integer.class) {
                writableArray.pushInt((Integer)list.get(i));
            } else if (list.get(i).getClass() == LinkedTreeMap.class) {
                writableArray.pushMap(recursivelyDeconstructMap((LinkedTreeMap)list.get(i)));
            } else if (list.get(i).getClass() == ArrayList.class) {
                writableArray.pushArray(recursivelyDeconstructList((ArrayList)list.get(i)));
            } else {
                System.out.println("Class: " + list.get(i).getClass().toString());
            }
        }
        return writableArray;
    }

    private Map<String, Object> recursivelyDeconstructReadableMap(ReadableMap readableMap) {
        ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
        Map<String, Object> deconstructedMap = new HashMap<>();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            ReadableType type = readableMap.getType(key);
            switch (type) {
            case Null:
                deconstructedMap.put(key, null);
                break;
            case Boolean:
                deconstructedMap.put(key, readableMap.getBoolean(key));
                break;
            case Number:
                deconstructedMap.put(key, readableMap.getDouble(key));
                break;
            case String:
                deconstructedMap.put(key, readableMap.getString(key));
                break;
            case Map:
                deconstructedMap.put(key, recursivelyDeconstructReadableMap(readableMap.getMap(key)));
                break;
            case Array:
                deconstructedMap.put(key, recursivelyDeconstructReadableArray(readableMap.getArray(key)));
                break;
            default:
                throw new IllegalArgumentException("Could not convert object with key: " + key + ".");
            }

        }
        return deconstructedMap;
    }

    private List<Object> recursivelyDeconstructReadableArray(ReadableArray readableArray) {
        List<Object> deconstructedList = new ArrayList<>(readableArray.size());
        for (int i = 0; i < readableArray.size(); i++) {
            ReadableType indexType = readableArray.getType(i);
            switch(indexType) {
            case Null:
                deconstructedList.add(i, null);
                break;
            case Boolean:
                deconstructedList.add(i, readableArray.getBoolean(i));
                break;
            case Number:
                deconstructedList.add(i, readableArray.getDouble(i));
                break;
            case String:
                deconstructedList.add(i, readableArray.getString(i));
                break;
            case Map:
                deconstructedList.add(i, recursivelyDeconstructReadableMap(readableArray.getMap(i)));
                break;
            case Array:
                deconstructedList.add(i, recursivelyDeconstructReadableArray(readableArray.getArray(i)));
                break;
            default:
                throw new IllegalArgumentException("Could not convert object at index " + i + ".");
            }
        }
        return deconstructedList;
    }
}
