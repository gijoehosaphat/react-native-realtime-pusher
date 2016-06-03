'use strict'

var { NativeModules, DeviceEventEmitter } = require('react-native')

const Pusher = {
  initialize(host, authPath, messageSubPath, appKey, authToken) {
    NativeModules.Pusher.initialize(host, authPath, messageSubPath, appKey, authToken)
  },
  connect(eventHandler) {
    NativeModules.Pusher.connect()
    DeviceEventEmitter.addListener('pusher-event', eventHandler)
  },
  disconnect(eventHandler) {
    NativeModules.Pusher.disconnect()
    DeviceEventEmitter.removeAllListeners('pusher-event')
  },
  channelSubscribe(channel, channelEvent) {
    NativeModules.Pusher.channelSubscribe(channel, channelEvent)
  },
  channelUnsubscribe(channel) {
    NativeModules.Pusher.channelUnsubscribe(channel)
  },
  messagePost(messageObject, channelName, channelEvent) {
    NativeModules.Pusher.messagePost(messageObject, channelName, channelEvent)
  }
}

export default Pusher
