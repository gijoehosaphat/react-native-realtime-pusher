//
//  Pusher.m
//  RNRealtimePusher
//
//  Created by Mark Jamieson on 2016-11-25.
//  Copyright © 2016 Mark Jamieson. All rights reserved.
//

#import "Pusher.h"
#import "UNIRest.h"

@implementation Pusher

NSString *authEndPoint;
NSString *authToken;
NSString *messageEndPoint;
NSString *appKey;
NSString *cluster;
NSString *connectionState = @"DISCONNECTED";

PTPusher *client;

@synthesize bridge = _bridge;

RCT_EXPORT_MODULE();

RCT_EXPORT_METHOD(initialize:(NSString *)_host authPath:(NSString *)_authPath  messageSubPath:(NSString *)_messageSubPath appKey:(NSString *)_appKey cluster:(NSString *)_cluster authToken:(NSString *)_authToken resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    @try{
        connectionState = @"DISCONNECTED";
        authEndPoint = [NSString stringWithFormat:@"%@%@", _host, _authPath];
        authToken = _authToken;
        messageEndPoint = [NSString stringWithFormat:@"%@%@", _host, _messageSubPath];
        appKey = _appKey;
        cluster = _cluster;
        resolve(@{
                  @"host": _host,
                  @"authPath": _authPath,
                  @"messageSubPath": _messageSubPath,
                  @"appKey": _appKey,
                  @"cluster": _cluster,
                  @"authToken": _authToken ? _authToken : [NSNull null]
                  });
    }
    @catch (NSException *exception){
        reject(@"initialize_failed", @"Initialize failed", exception);
    }
}

RCT_EXPORT_METHOD(connect:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        [self changeConnectionState:@"CONNECTING"];
        client = [PTPusher pusherWithKey:appKey delegate:self encrypted:YES cluster:cluster];
        client.channelAuthorizationDelegate = self;
        [client connect];
        resolve(@YES);
    }
    @catch (NSException *exception){
        reject(@"connect_failed", @"Connect failed", exception);
    }
}

RCT_EXPORT_METHOD(disconnect:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        [self changeConnectionState:@"DISCONNECTING"];
        [client disconnect];
        resolve(@YES);
    }
    @catch (NSException *exception){
        reject(@"disconnect_failed", @"Disconnect failed", exception);
    }
}

RCT_EXPORT_METHOD(channelSubscribe:(NSString *)_channelName channelEventName:(NSString *)_channelEventName resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        PTPusherChannel *channel;
        if ([_channelName hasPrefix:@"private-"]){
            channel = [client subscribeToPrivateChannelNamed:[_channelName substringFromIndex:8]];
        } else if ([_channelName hasPrefix:@"presence-"]){
            channel = [client subscribeToPresenceChannelNamed:[_channelName substringFromIndex:9]];
        } else {
            channel = [client subscribeToChannelNamed:_channelName];
        }

        [channel bindToEventNamed:_channelEventName handleWithBlock:^(PTPusherEvent *event) {
            [self sendEvent:@{@"eventName": event.name, @"channelName": event.channel, @"data": event.data}];
        }];
        resolve(@{
                  @"channelName": _channelName,
                  @"channelEventName": _channelEventName
                  });
    }
    @catch (NSException *exception){
        reject(@"channel_subscribe_failed", @"Channel subscribe failed", exception);
    }

}

RCT_EXPORT_METHOD(channelUnsubscribe:(NSString *)_channelName resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    @try{
        [[client channelNamed:_channelName] unsubscribe];
        resolve(@{
                  @"channelName": _channelName
                  });
    }
    @catch (NSException *exception){
        reject(@"channel_unsubscribe_failed", @"Channel Unsubscribe failed", exception);
    }
}

RCT_EXPORT_METHOD(messagePost:(NSDictionary *)_messageObject channelName:(NSString *)_channelName channelEvent:(NSString *)_channelEvent resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    @try {
        NSString *url = [NSString stringWithFormat:@"%@/%@/%@", messageEndPoint, _channelName, _channelEvent];
        NSDictionary* headers = @{@"Content-Type": @"application/json", @"Authorization": authToken};

        UNIHTTPJsonResponse *response = [[UNIRest postEntity:^(UNIBodyRequest *request) {
            [request setUrl:url];
            [request setHeaders:headers];
            [request setBody:[NSJSONSerialization dataWithJSONObject:_messageObject options:0 error:nil]];
        }] asStringAsync:^(UNIHTTPStringResponse *stringResponse, NSError *error) {
            if (error == nil){
                [self sendEvent:@{@"eventName": @"onMessageSuccess", @"channelName":_channelName}];
            } else {
                [self sendEvent:@{@"eventName": @"onMessageFailure", @"channelName":_channelName, @"error":error.localizedDescription}];
            }
        }];
        resolve(@{
                  @"channelName": _channelName,
                  @"channelEvent": _channelEvent
                  });
    }
    @catch (NSException *exception){
        reject(@"message_post_failed", @"Message post failed", exception);
    }
}

RCT_EXPORT_METHOD(getConnectionState:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    resolve(connectionState);
}


- (void)pusherChannel:(PTPusherChannel *)channel requiresAuthorizationForSocketID:(NSString *)socketID completionHandler:(void(^)(BOOL isAuthorized, NSDictionary *authData, NSError *error))completionHandler
{
    NSDictionary* headers = @{@"Content-Type": @"application/x-www-form-urlencoded", @"Authorization": authToken};
    NSDictionary* params = @{@"socket_id": socketID, @"channel_name": channel.name};
    [[UNIRest post:^(UNISimpleRequest *request) {
        [request setUrl:authEndPoint];
        [request setHeaders:headers];
        [request setParameters:params];
    }] asJsonAsync:^(UNIHTTPJsonResponse *jsonResponse, NSError *error) {
        completionHandler(jsonResponse.code == 200, jsonResponse.body.JSONObject, nil);
    }];
}

- (void)pusher:(PTPusher *)pusher connectionDidConnect:(PTPusherConnection *)connection{
    [self changeConnectionState:@"CONNECTED"];
}

- (void)pusher:(PTPusher *)pusher connection:(PTPusherConnection *)connection didDisconnectWithError:(NSError *)error willAttemptReconnect:(BOOL)willAttemptReconnect{
    [self changeConnectionState:@"DISCONNECTED"];
}

- (BOOL)pusher:(PTPusher *)pusher connectionWillAutomaticallyReconnect:(PTPusherConnection *)connection afterDelay:(NSTimeInterval)delay
{
    return NO;
}

- (void)pusher:(PTPusher *)pusher didSubscribeToChannel:(PTPusherChannel *)channel{
    [self sendEvent:@{@"eventName": @"onSubscriptionSucceeded", @"channelName":channel.name}];
}

- (void)pusher:(PTPusher *)pusher didFailToSubscribeToChannel:(PTPusherChannel *)channel withError:(NSError *)error{
    [self sendEvent:@{@"eventName": @"onAuthenticationFailure", @"channelName":channel.name, @"error": error.localizedDescription}];
}

- (void) changeConnectionState:(NSString *)newState{
    NSString *prevState = [NSString stringWithString:connectionState];
    connectionState = newState;
    NSLog(@"CONNECTION CHANGE: %@ -> %@", prevState, connectionState);
    [self sendEvent:@{
        @"eventName": @"connectionStateChange",
        @"currentState": connectionState,
        @"previousState": prevState
    }];
}

- (void) sendEvent:(NSDictionary *)params{
    [self.bridge.eventDispatcher sendAppEventWithName:@"pusher-event" body:params];
}

@end
