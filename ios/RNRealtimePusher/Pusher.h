//
//  Pusher.h
//  RNRealtimePusher
//
//  Created by Mark Jamieson on 2016-11-25.
//  Copyright © 2016 Mark Jamieson. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTBridge.h>
#import <React/RCTEventDispatcher.h>
#import "LibPusher.h"

#define kPTPusherClientLibraryVersion 1.6.2

@interface Pusher : NSObject <RCTBridgeModule, PTPusherDelegate>

@end
