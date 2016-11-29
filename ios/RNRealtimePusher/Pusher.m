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
NSString *connectionState = @"DISCONNECTED";

NSMutableArray *channels;

PTPusher *client;

@synthesize bridge = _bridge;

RCT_EXPORT_MODULE();

RCT_EXPORT_METHOD(initialize:(NSString *)_host authPath:(NSString *)_authPath  messageSubPath:(NSString *)_messageSubPath appKey:(NSString *)_appKey authToken:(NSString *)_authToken)
{
    authEndPoint = [NSString stringWithFormat:@"%@%@", _host, _authPath];
    authToken = _authToken;
    messageEndPoint = [NSString stringWithFormat:@"%@%@", _host, _messageSubPath];
    appKey = _appKey;
    
    channels = [[NSMutableArray alloc] init];
}

RCT_EXPORT_METHOD(connect)
{
    [self changeConnectionState:@"CONNECTING"];
    client = [PTPusher pusherWithKey:appKey delegate:self encrypted:YES];
    client.channelAuthorizationDelegate = self;
    [client connect];
}

RCT_EXPORT_METHOD(disconnect)
{
    [self changeConnectionState:@"DISCONNECTING"];
    [client disconnect];
}

RCT_EXPORT_METHOD(channelSubscribe:(NSString *)_channelName channelEventName:(NSString *)_channelEventName)
{
    if ([_channelName hasPrefix:@"private-"]){
        PTPusherChannel *channel = [client subscribeToPrivateChannelNamed:[_channelName substringFromIndex:8]];
        
        [channel bindToEventNamed:_channelEventName handleWithBlock:^(PTPusherEvent *event) {
            [self sendEvent:@{@"eventName": event.name, @"channelName": event.channel, @"data": event.data}];
        }];
        
        [channels addObject:channel];
    }
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
        completionHandler(YES, jsonResponse.body.JSONObject, nil);
    }];
}

- (void)pusher:(PTPusher *)pusher connectionDidConnect:(PTPusherConnection *)connection{
    [self changeConnectionState:@"CONNECTED"];
}

- (void)pusher:(PTPusher *)pusher connection:(PTPusherConnection *)connection didDisconnectWithError:(NSError *)error willAttemptReconnect:(BOOL)willAttemptReconnect{
    [self changeConnectionState:@"DISCONNECTED"];
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
