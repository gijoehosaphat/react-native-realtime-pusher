//
//  Pusher.h
//  RNRealtimePusher
//
//  Created by Mark Jamieson on 2016-11-25.
//  Copyright © 2016 Mark Jamieson. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "RCTBridgeModule.h"
#import "RCTBridge.h"
#import "RCTEventDispatcher.h"
#import "LibPusher.h"

#define kPTPusherClientLibraryVersion 1.6.2

@interface Pusher : NSObject <RCTBridgeModule, PTPusherDelegate>

@end
