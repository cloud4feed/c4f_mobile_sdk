import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';

// =============================================================================
// 🧠 MAIN SDK CLASS
// =============================================================================

class MobileSdkFlutter {
  static const MethodChannel _channel = MethodChannel('cloud4feed_mobilesdk_flutter');

  // ---------------------------------------------------------------------------
  // 🚀 INITIALIZATION & SETUP
  // ---------------------------------------------------------------------------

  /// Initialize the SDK with your API Key. old one
  // static Future<bool> initialize(String apiKey) async {
  //   try {
  //     final bool result = await _channel.invokeMethod('initialize', {'apiKey': apiKey});
  //     return result;
  //   } on PlatformException catch (e) {
  //     throw Exception('Failed to initialize SDK: ${e.message}');
  //   }
  // }

  /// Initialize the SDK with your API Key.
  /// 
  /// Examples:
  /// // 1. Simple initialization (no parameters)
  /// await MobileSdkFlutter.initialize('your-api-key');
  /// 
  /// // 2. With parameter names to look up from storage
  /// await MobileSdkFlutter.initialize('your-api-key', 
  ///   params: ['userID', 'rank', 'language']);
  /// 
  /// // 3. With direct values (key-value pairs)
  /// await MobileSdkFlutter.initialize('your-api-key',
  ///   params: [
  ///     {'userID': '12345'},
  ///     {'userTier': 'premium'}
  ///   ]);
  /// 
  /// // 4. Mixed parameters (some from storage, some direct)
  /// await MobileSdkFlutter.initialize('your-api-key',
  ///   params: [
  ///     'userID',                     // Look up from storage
  ///     {'rank': 'gold'},             // Direct value
  ///     'language',                   // Look up from storage
  ///     {'source': 'flutter_app'},    // Direct value
  ///   ]);
  static Future<bool> initialize(String apiKey, {List<dynamic>? params}) async {
    try {
      final Map<String, dynamic> args = {'apiKey': apiKey};
      
      if (params != null && params.isNotEmpty) {
        args['params'] = params;
      }
      
      final bool result = await _channel.invokeMethod('initialize', args);
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to initialize SDK: ${e.message}');
    }
  }

  /// Backward compatibility - simple initialize without parameters
  static Future<bool> initializeSimple(String apiKey) async {
    return await initialize(apiKey);
  }

  static Future<void> triggerByTabChange(String tabName) async {
    try {
      await _channel.invokeMethod('triggerTabChange', {'tabName': tabName});
    } on PlatformException catch (e) {
      debugPrint('Failed to trigger tab change: ${e.message}');
    }
  }

  /// Enables automatic lifecycle tracking (App Start/Exit).
  /// Note: For Button & Scroll detection, use SurveyTrigger and SurveyScrollView widgets.
  static Future<bool> autoSetup() async {
    try {
      final bool result = await _channel.invokeMethod('autoSetup');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to auto setup: ${e.message}');
    }
  }

  static Future<bool> enableNavigationSafety() async {
    try {
      final bool result = await _channel.invokeMethod('enableNavigationSafety');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to enable navigation safety: ${e.message}');
    }
  }

  static Future<bool> autoSetupSafe() async {
    try {
      final bool result = await _channel.invokeMethod('autoSetupSafe');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to safe auto setup: ${e.message}');
    }
  }

  // ---------------------------------------------------------------------------
  // 🔗 TRIGGER METHODS (Signal Senders)
  // ---------------------------------------------------------------------------

  /// Manually triggers a button survey logic.
  /// Matches a survey with 'buttonTriggerId' == [buttonId].
  static Future<void> triggerButton(String buttonId) async {
    try {
      await _channel.invokeMethod('triggerButton', {'buttonId': buttonId});
    } on PlatformException catch (e) {
      debugPrint('Failed to trigger button: ${e.message}');
    }
  }

  /// Manually triggers navigation logic.
  /// Matches a survey with 'triggerScreens' containing [screenName].
  static Future<void> triggerNavigation(String screenName) async {
    try {
      await _channel.invokeMethod('triggerNavigation', {'screenName': screenName});
    } on PlatformException catch (e) {
      debugPrint('Failed to trigger navigation: ${e.message}');
    }
  }

  /// Reports the current scroll position of the screen to the scroll trigger.
  /// [scrollY] is in **physical pixels** (Flutter's logical offset × devicePixelRatio), the
  /// unit of the Configuration Manager's Scroll Threshold. A scroll survey opens once per
  /// screen visit when [scrollY] reaches its threshold. [MobileSdkWrapper] and
  /// [SurveyScrollView] call this for you.
  static Future<void> triggerScroll({int scrollY = 500}) async {
    try {
      await _channel.invokeMethod('triggerScroll', {'scrollY': scrollY});
    } on PlatformException catch (e) {
      debugPrint('Failed to trigger scroll: ${e.message}');
    }
  }
  

  // ---------------------------------------------------------------------------
  // 🛠️ DISPLAY & UTILS
  // ---------------------------------------------------------------------------

  /// Shows the most relevant survey automatically based on priority.
  static Future<bool> showSurvey() async {
    try {
      final bool result = await _channel.invokeMethod('showSurvey');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to show survey: ${e.message}');
    }
  }

  /// Shows a specific survey by its ID.
  static Future<bool> showSurveyById(String surveyId) async {
    try {
      final bool result = await _channel.invokeMethod('showSurveyById', {'surveyId': surveyId});
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to show survey $surveyId: ${e.message}');
    }
  }

  // ---------------------------------------------------------------------------
  // ⚙️ DATA & CONFIGURATION
  // ---------------------------------------------------------------------------

  static Future<bool> setUserProperty(String key, String value) async {
    try {
      final bool result = await _channel.invokeMethod('setUserProperty', {'key': key, 'value': value});
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to set user property: ${e.message}');
    }
  }

  static Future<bool> setSessionData(String key, String value) async {
    try {
      final bool result = await _channel.invokeMethod('setSessionData', {'key': key, 'value': value});
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to set session data: ${e.message}');
    }
  }

  static Future<bool> resetSessionData() async {
    try {
      final bool result = await _channel.invokeMethod('resetSessionData');
      return result;
    } on PlatformException catch (e) {
      throw Exception('Failed to reset session data: ${e.message}');
    }
  }

  // ---------------------------------------------------------------------------
  // 📊 STATUS & DEBUGGING
  // ---------------------------------------------------------------------------

  static Future<String> getDebugStatus() async {
    try {
      final String result = await _channel.invokeMethod('getDebugStatus');
      return result;
    } on PlatformException catch (e) {
      return 'Error: ${e.message}';
    }
  }

  static Future<String> getQueueStatus() async {
    try {
      final String result = await _channel.invokeMethod('getQueueStatus');
      return result;
    } on PlatformException catch (e) {
      return 'Error: ${e.message}';
    }
  }

  static Future<List<dynamic>> getSurveyIds() async {
    try {
      final List<dynamic> result = await _channel.invokeMethod('getSurveyIds');
      return result;
    } on PlatformException {
      return [];
    }
  }

  static Future<bool> isSDKEnabled() async {
    try {
      final bool result = await _channel.invokeMethod('isSDKEnabled');
      return result;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> cleanup() async {
    try {
      final bool result = await _channel.invokeMethod('cleanup');
      return result;
    } on PlatformException {
      return false;
    }
  }
  
  static Future<bool> resetTriggers() async {
    try {
      final bool result = await _channel.invokeMethod('resetTriggers');
      return result;
    } on PlatformException {
      return false;
    }
  }
  
  static Future<bool> clearSurveyQueue() async {
    try {
      final bool result = await _channel.invokeMethod('clearSurveyQueue');
      return result;
    } on PlatformException {
      return false;
    }
  }
  
  // New Methods from your previous list
  static Future<bool> isUserExcluded() async {
    try {
      final bool result = await _channel.invokeMethod('isUserExcluded');
      return result;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> isUserExcludedForSurvey(String surveyId) async {
    try {
      final bool result = await _channel.invokeMethod('isUserExcludedForSurvey', {'surveyId': surveyId});
      return result;
    } on PlatformException {
      return false;
    }
  }
  
  static Future<bool> isConfigurationLoaded() async {
    try {
      final bool result = await _channel.invokeMethod('isConfigurationLoaded');
      return result;
    } on PlatformException {
      return false;
    }
  }
  
  static Future<String> getConfigForDebug() async {
    try {
      final String result = await _channel.invokeMethod('getConfigForDebug');
      return result;
    } on PlatformException {
      return "Error";
    }
  }
}

// =============================================================================
// 🧱 WIDGETS FOR AUTO-DETECTION
// =============================================================================

/// 1. BUTTON TRIGGER WIDGET (Fixed with Listener)
/// Wraps any widget (Button, Icon, Container) to enable auto-detection.
class SurveyTrigger extends StatelessWidget {
  final String triggerId;
  final Widget child;

  const SurveyTrigger({
    super.key,
    required this.triggerId,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    // ✅ Uses Listener to catch touches even if child button consumes them
    return Listener(
      onPointerUp: (_) {
        debugPrint("👆 [MobileSDK] SurveyTrigger detected click on: $triggerId");
        MobileSdkFlutter.triggerButton(triggerId);
      },
      behavior: HitTestBehavior.translucent, 
      child: child,
    );
  }
}

/// 2. NAVIGATION OBSERVER
/// Add this to your MaterialApp to enable auto-navigation detection.
class SurveyNavigationObserver extends NavigatorObserver {
  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    super.didPush(route, previousRoute);
    _checkRoute(route);
  }

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) {
    super.didReplace(newRoute: newRoute, oldRoute: oldRoute);
    if (newRoute != null) _checkRoute(newRoute);
  }

  void _checkRoute(Route<dynamic> route) {
    final String? screenName = route.settings.name;
    if (screenName != null) {
      debugPrint("📍 [MobileSDK] NavigationObserver detected: $screenName");
      MobileSdkFlutter.triggerNavigation(screenName);
    }
  }
}

// =============================================================================
// 🧱 WIDGETS FOR AUTO-DETECTION
// =============================================================================

/// 3. 🛡️ AUTOMATIC SDK WRAPPER (Global Scroll Detection)
/// Wraps the entire application to automatically detect scrolls 
/// without requiring the developer to write manual trigger code.
class MobileSdkWrapper extends StatefulWidget {
  final Widget child;
  const MobileSdkWrapper({super.key, required this.child});

  @override
  State<MobileSdkWrapper> createState() => _MobileSdkWrapperState();
}

class _MobileSdkWrapperState extends State<MobileSdkWrapper> {
  final _SurveyScrollReporter _reporter = _SurveyScrollReporter();

  @override
  Widget build(BuildContext context) {
    return NotificationListener<ScrollNotification>(
      onNotification: (ScrollNotification notification) {
        if (notification is ScrollUpdateNotification &&
            notification.metrics.axis == Axis.vertical) {
          _reporter.report(context, notification.metrics.pixels);
        }
        return false; // Let the scroll continue normally
      },
      child: widget.child,
    );
  }
}

/// Converts Flutter's logical scroll offset to physical pixels (the unit of the Configuration
/// Manager's Scroll Threshold, also used by the native SDKs) and reports it to the native SDK
/// when it changed by at least [_minStepPixels]. The native SDK compares it with the threshold
/// and opens a scroll survey at most once per screen visit.
class _SurveyScrollReporter {
  static const int _minStepPixels = 50;
  int? _lastSent;

  void report(BuildContext context, double logicalOffset) {
    if (logicalOffset <= 0) return;
    final ratio = MediaQuery.maybeOf(context)?.devicePixelRatio ?? 1.0;
    final pixels = (logicalOffset * ratio).round();
    final last = _lastSent;
    if (last != null && (pixels - last).abs() < _minStepPixels) return;
    _lastSent = pixels;
    MobileSdkFlutter.triggerScroll(scrollY: pixels);
  }
}

/// 3. LOCAL SCROLL TRIGGER WIDGET
/// Wraps content in a SingleChildScrollView that reports its scroll position. The survey opens
/// at the Scroll Threshold set in the Configuration Manager (physical pixels), once per screen
/// visit — the same as native Android / iOS apps.
class SurveyScrollView extends StatefulWidget {
  final Widget child;

  /// No longer used: the Configuration Manager's Scroll Threshold decides (since 1.3.2).
  /// Kept so existing code still compiles.
  @Deprecated('Ignored. The Scroll Threshold from the Configuration Manager is used.')
  final int? threshold;

  const SurveyScrollView({
    super.key,
    required this.child,
    @Deprecated('Ignored. The Scroll Threshold from the Configuration Manager is used.')
    this.threshold,
  });

  @override
  State<SurveyScrollView> createState() => _SurveyScrollViewState();
}

class _SurveyScrollViewState extends State<SurveyScrollView> {
  final ScrollController _controller = ScrollController();
  final _SurveyScrollReporter _reporter = _SurveyScrollReporter();

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onScroll);
  }

  void _onScroll() {
    if (!mounted || !_controller.hasClients) return;
    _reporter.report(context, _controller.offset);
  }

  @override
  void dispose() {
    _controller.removeListener(_onScroll);
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      controller: _controller,
      child: widget.child,
    );
  }
}