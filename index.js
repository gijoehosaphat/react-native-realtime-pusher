'use strict'

import { NativeModules } from 'react-native'

export const ConnectionState = {
  CONNECTING: 'CONNECTING',
  CONNECTED: 'CONNECTED',
  DISCONNECTING: 'DISCONNECTING',
  DISCONNECTED: 'DISCONNECTED'
}

async function initialize(host, authPath, messageSubPath, appKey, authToken) {
  return await NativeModules.Pusher.initialize(host, authPath, messageSubPath, appKey, authToken)
}

async function getConnectionState() {
  return await NativeModules.Pusher.getConnectionState()
}

async function connect() {
  return await NativeModules.Pusher.connect()
}

async function disconnect() {
  return await NativeModules.Pusher.disconnect()
}

async function channelSubscribe(channel, channelEvent) {
  return await NativeModules.Pusher.channelSubscribe(channel, channelEvent)
}

async function channelUnsubscribe(channel) {
  return await NativeModules.Pusher.channelUnsubscribe(channel)
}

async function messagePost(messageObject, channelName, channelEvent) {
  return await NativeModules.Pusher.messagePost(messageObject, channelName, channelEvent)
}

const Pusher = {
  initialize,
  getConnectionState,
  connect,
  disconnect,
  channelSubscribe,
  channelUnsubscribe,
  messagePost
}

export default Pusher
