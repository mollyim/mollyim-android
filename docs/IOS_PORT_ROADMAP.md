# EMMA iOS Port Roadmap

**Porting EMMA Security & Translation to iOS/Signal-iOS**

---

## Executive Summary

This document outlines the strategy for porting EMMA-Android to iOS, maintaining feature parity with Android while adapting to iOS-specific security models and constraints.

**Target Platform:** iOS 15.0+ (Signal-iOS fork)
**Language:** Swift 5.9+, Objective-C++, C++ (for native components)
**Timeline:** 6-9 months (3 engineers)
**Complexity:** High (iOS security model differences)

---

## Architecture Overview

### Current Android Stack

```
┌─────────────────────────────────────┐
│         Molly/Signal Android         │
├─────────────────────────────────────┤
│   Kotlin UI + Security Manager       │
├─────────────────────────────────────┤
│  security-lib    │  translation-lib  │
│   (Kotlin/JNI)   │   (Kotlin/JNI)    │
├──────────────────┴───────────────────┤
│         Native C++ Libraries          │
│  - EL2 Detection  - Translation       │
│  - Countermeasures - Network Client   │
└─────────────────────────────────────┘
```

### Target iOS Stack

```
┌─────────────────────────────────────┐
│          Signal-iOS (Fork)           │
├─────────────────────────────────────┤
│    SwiftUI + Security Manager        │
├─────────────────────────────────────┤
│  SecurityKit     │  TranslationKit   │
│  (Swift/Obj-C++)  │ (Swift/Obj-C++)  │
├──────────────────┴───────────────────┤
│         Native C++ Libraries          │
│  - EL2/Hypervisor - Translation       │
│  - Countermeasures - Network Client   │
└─────────────────────────────────────┘
```

**Key Change:** Replace JNI with Objective-C++ bridges

---

## Phase 1: Foundation (Months 1-2)

### 1.1 Signal-iOS Fork Setup

**Tasks:**
- Fork Signal-iOS from `signalapp/Signal-iOS`
- Create EMMA-iOS repository structure
- Set up build system (Xcode + CocoaPods/SPM)
- Configure CI/CD (GitHub Actions)

**Deliverables:**
- Working Signal-iOS fork
- Build scripts
- Development environment guide

### 1.2 Native C++ Port

**Tasks:**
- Port C++ libraries from Android (95% reusable)
- Replace Android-specific APIs:
  - `__android_log_print` → `os_log` (iOS logging)
  - `perf_event_open` → iOS performance APIs
- Create Objective-C++ bridges

**Files to Port:**

**security-lib:**
```
el2_detector.{h,cpp}          → Same (ARM64)
performance_counters.{h,cpp}  → Needs iOS adaptation
cache_operations.{h,cpp}      → Same (ARM64)
memory_scrambler.{h,cpp}      → Same
timing_obfuscation.{h,cpp}    → Same
```

**translation-lib:**
```
translation_engine.{h,cpp}    → Same
int8_quantizer.cpp            → Same
model_loader.cpp              → Adapt for iOS bundle
```

**iOS-Specific Changes:**

```cpp
// Android
#include <android/log.h>
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

// iOS
#include <os/log.h>
static os_log_t log_obj = os_log_create("im.molly.emma", "Security");
#define LOGD(...) os_log(log_obj, __VA_ARGS__)
```

### 1.3 Performance Counter Adaptation

**Challenge:** iOS doesn't expose `perf_event_open`

**Solution:** Use iOS performance APIs

```cpp
// iOS Alternative for Performance Monitoring
#include <mach/mach.h>
#include <mach/mach_time.h>

class IOSPerformanceCounters {
public:
    // Use mach_absolute_time() for timing
    uint64_t read_cycles();

    // Use task_info() for CPU/memory stats
    bool get_task_info(task_vm_info_data_t& info);

    // Use IOKit for cache metrics (limited)
    bool read_cache_metrics();
};
```

**Trade-offs:**
- Less detailed metrics than Android
- No direct perf counters access
- Must use Apple's provided APIs

---

## Phase 2: Security Framework (Months 3-4)

### 2.1 SecurityKit Framework

**Create iOS Framework:**

```
SecurityKit.framework/
├── Headers/
│   ├── SecurityKit.h
│   ├── EL2Detector.h
│   ├── ThreatAnalysis.h
│   ├── CacheOperations.h
│   └── AdaptiveCountermeasures.h
├── Modules/
│   └── module.modulemap
└── SecurityKit (binary)
```

**Swift API:**

```swift
import SecurityKit

public class EL2Detector {
    public static let shared = EL2Detector()

    public func initialize() -> Bool {
        return __em_detector_init()  // Calls C++
    }

    public func analyzeThreat() -> ThreatAnalysis? {
        var analysis = em_threat_analysis_t()
        guard __em_analyze_threat(&analysis) else {
            return nil
        }
        return ThreatAnalysis(from: analysis)
    }
}

public struct ThreatAnalysis {
    public let threatLevel: Float
    public let category: ThreatCategory
    public let hypervisorConfidence: Float
    // ...

    public func getChaosIntensity() -> Int {
        switch category {
        case .low: return 10
        case .medium: return 60
        case .high: return 100
        case .critical: return 150
        case .nuclear: return 200
        }
    }
}
```

**Objective-C++ Bridge:**

```objc++
// EL2DetectorBridge.mm
#import "EL2DetectorBridge.h"
#include "el2_detector.h"

using namespace molly::security;

@implementation EL2DetectorBridge

+ (EL2Detector *)sharedDetector {
    static EL2Detector *instance = new EL2Detector();
    return instance;
}

- (BOOL)initialize {
    return [[EL2DetectorBridge sharedDetector] initialize];
}

- (nullable EMThreatAnalysis *)analyzeThreat {
    ThreatAnalysis analysis = [[EL2DetectorBridge sharedDetector] analyze_threat];

    return [[EMThreatAnalysis alloc] initWithCppAnalysis:analysis];
}

@end
```

### 2.2 iOS Security Constraints

**Challenges:**

1. **Sandbox Restrictions:**
   - No direct kernel access
   - Limited process inspection
   - No arbitrary memory access

2. **App Store Guidelines:**
   - No private APIs
   - No jailbreak detection bypass
   - Limited background execution

3. **Entitlements Required:**
   - `com.apple.developer.kernel.extended-virtual-addressing`
   - `com.apple.security.network.client`
   - May need special approval from Apple

**Adaptations:**

```swift
// iOS-specific security posture
class IOSSecurityManager {
    func isJailbroken() -> Bool {
        // Check for jailbreak indicators
        // (Use standard checks, not private APIs)
    }

    func detectDebugger() -> Bool {
        // Use sysctl to detect debugger
        var info = kinfo_proc()
        var mib : [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        // ...
    }

    func checkCodeSigning() -> Bool {
        // Verify app code signature integrity
    }
}
```

---

## Phase 3: Translation Framework (Months 4-5)

### 3.1 TranslationKit Framework

**Create iOS Framework:**

```swift
import TranslationKit

public class TranslationEngine {
    public static let shared = TranslationEngine()

    public func initialize(modelPath: String) -> Bool {
        return __em_translation_init(modelPath)
    }

    public func translate(
        text: String,
        from sourceLang: String = "da",
        to targetLang: String = "en"
    ) async -> TranslationResult? {
        return await withCheckedContinuation { continuation in
            __em_translate(text, sourceLang, targetLang) { result in
                continuation.resume(returning: result)
            }
        }
    }
}

public struct TranslationResult {
    public let translatedText: String
    public let confidence: Float
    public let inferenceTimeUs: Int64
    public let usedNetwork: Bool
}
```

### 3.2 Model Integration

**iOS Model Packaging:**

```
EMMA.app/
└── Contents/
    └── Resources/
        └── models/
            └── opus-mt-da-en-int8.mlmodel  # CoreML format
```

**Convert OPUS-MT to CoreML:**

```python
import coremltools as ct
from transformers import MarianMTModel

# Load PyTorch model
model = MarianMTModel.from_pretrained("Helsinki-NLP/opus-mt-da-en")

# Convert to CoreML
coreml_model = ct.convert(
    model,
    inputs=[ct.TensorType(shape=(1, ct.RangeDim(1, 512)))],
    compute_units=ct.ComputeUnit.ALL  # Use Neural Engine
)

# Save
coreml_model.save("opus-mt-da-en-int8.mlmodel")
```

**Benefits:**
- Leverages Apple Neural Engine
- Optimized for Apple Silicon
- Better than custom INT8 implementation

### 3.3 Network Translation Client

**iOS Implementation:**

```swift
import Network

class NetworkTranslationClient {
    private let nwBrowser: NWBrowser

    init() {
        // mDNS/Bonjour service discovery
        let parameters = NWParameters()
        self.nwBrowser = NWBrowser(
            for: .bonjour(type: "_emma-translate._tcp", domain: nil),
            using: parameters
        )
    }

    func startDiscovery() {
        nwBrowser.stateUpdateHandler = { state in
            switch state {
            case .ready:
                print("Browser ready")
            case .failed(let error):
                print("Browser failed: \(error)")
            default:
                break
            }
        }

        nwBrowser.browseResultsChangedHandler = { results, changes in
            for result in results {
                if case .service(let name, let type, let domain, _) = result.endpoint {
                    print("Found server: \(name).\(type)\(domain)")
                    self.resolveService(result)
                }
            }
        }

        nwBrowser.start(queue: .main)
    }

    func translateViaNetwork(
        _ text: String,
        from sourceLang: String,
        to targetLang: String
    ) async throws -> TranslationResult {
        // Connect to server
        // Perform Kyber key exchange
        // Send encrypted request
        // Receive encrypted response
    }
}
```

---

## Phase 4: UI & Integration (Months 5-6)

### 4.1 SwiftUI Mil-Spec Theme

**Color Palette:**

```swift
extension Color {
    // EMMA MIL-SPEC Colors
    static let emmaBlackPrimary = Color(hex: "0A0E12")
    static let emmaBlackSecondary = Color(hex: "121820")
    static let emmaTacticalGreen = Color(hex: "00FF88")
    static let emmaTacticalAmber = Color(hex: "FFB800")

    static let emmaThreatLow = Color(hex: "00FF88")
    static let emmaThreatMedium = Color(hex: "FFB800")
    static let emmaThreatHigh = Color(hex: "FF6B00")
    static let emmaThreatCritical = Color(hex: "FF3B30")
    static let emmaThreatNuclear = Color(hex: "FF006B")
}
```

**Threat Indicator Widget:**

```swift
struct ThreatLevelIndicator: View {
    let threatLevel: Float
    let category: ThreatCategory

    @State private var animatedLevel: Float = 0

    var body: some View {
        ZStack {
            // Background arc
            Circle()
                .trim(from: 0.25, to: 1.0)
                .stroke(Color.emmaGreyDark.opacity(0.3), lineWidth: 8)
                .frame(width: 120, height: 120)

            // Threat arc
            Circle()
                .trim(from: 0.25, to: 0.25 + (animatedLevel * 0.75))
                .stroke(threatColor, lineWidth: 8)
                .frame(width: 120, height: 120)
                .animation(.easeInOut(duration: 0.8), value: animatedLevel)

            // Percentage text
            VStack {
                Text("\(Int(threatLevel * 100))%")
                    .font(.system(size: 24, weight: .bold, design: .monospaced))
                    .foregroundColor(threatColor)

                Text(category.rawValue)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(.emmaTextSecondary)
            }
        }
        .onAppear {
            animatedLevel = threatLevel
        }
    }

    var threatColor: Color {
        switch category {
        case .low: return .emmaThreatLow
        case .medium: return .emmaThreatMedium
        case .high: return .emmaThreatHigh
        case .critical: return .emmaThreatCritical
        case .nuclear: return .emmaThreatNuclear
        }
    }
}
```

**Security HUD:**

```swift
struct SecurityHUD: View {
    @ObservedObject var securityManager: SecurityManager

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header
            HStack {
                Text("EMMA SECURITY")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundColor(.emmaTacticalGreen)

                Spacer()

                Text(currentTime)
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(.emmaTextTertiary)
            }

            // Threat Indicator
            ThreatLevelIndicator(
                threatLevel: securityManager.threatLevel,
                category: securityManager.threatCategory
            )
            .frame(maxWidth: .infinity, alignment: .center)

            Divider()
                .background(Color.emmaDivider)

            // Metrics
            HStack {
                VStack(alignment: .leading) {
                    Text("CHAOS")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(.emmaTextSecondary)

                    Text("\(securityManager.chaosIntensity)%")
                        .font(.system(size: 18, weight: .bold, design: .monospaced))
                        .foregroundColor(.emmaTacticalAmber)
                }

                Spacer()

                VStack(alignment: .trailing) {
                    Text("STATUS")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(.emmaTextSecondary)

                    Text("ACTIVE")
                        .font(.system(size: 18, weight: .bold, design: .monospaced))
                        .foregroundColor(.emmaTacticalGreen)
                }
            }

            Divider()
                .background(Color.emmaDivider)

            // Active Countermeasures
            Text("ACTIVE COUNTERMEASURES")
                .font(.system(size: 10, design: .monospaced))
                .foregroundColor(.emmaTextSecondary)

            Text(securityManager.activeCountermeasures.joined(separator: " • "))
                .font(.system(size: 11, design: .monospaced))
                .foregroundColor(.emmaTextPrimary)
        }
        .padding()
        .background(Color.emmaHudBg)
        .cornerRadius(2)
        .overlay(
            RoundedRectangle(cornerRadius: 2)
                .stroke(Color.emmaHudBorder, lineWidth: 1)
        )
    }

    var currentTime: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss.SSS"
        return formatter.string(from: Date())
    }
}
```

### 4.2 Signal-iOS Integration

**Modify Signal Conversation View:**

```swift
// ConversationViewController.swift

import SecurityKit

class ConversationViewController: UIViewController {
    private let securityManager = SecurityManager.shared
    private var securityHUD: SecurityHUDView?

    override func viewDidLoad() {
        super.viewDidLoad()

        // Initialize security for this conversation
        if isIntimateProtectionEnabled(threadId) {
            activateMaximumSecurity()
        }

        // Add security HUD overlay
        setupSecurityHUD()

        // Monitor threat level
        observeThreatLevel()
    }

    private func setupSecurityHUD() {
        // Add HUD to view hierarchy
        // Position in top-right corner
        // Auto-hide when threat low
    }

    private func activateMaximumSecurity() {
        securityManager.enableIntimateProtection(threadId: threadId, enabled: true)
    }
}
```

---

## Phase 5: Testing & Optimization (Months 7-8)

### 5.1 Device Testing

**Target Devices:**
- iPhone 13 Pro (A15 Bionic)
- iPhone 14 Pro (A16 Bionic)
- iPhone 15 Pro (A17 Pro)

**Test Matrix:**

| Feature | iPhone 13 Pro | iPhone 14 Pro | iPhone 15 Pro |
|---------|--------------|--------------|--------------|
| EL2 Detection | ✓ | ✓ | ✓ |
| Cache Operations | ✓ | ✓ | ✓ |
| Memory Scrambling | ✓ | ✓ | ✓ |
| Translation (CoreML) | ~80ms | ~60ms | ~40ms |
| Network Offload | ~150ms | ~150ms | ~150ms |

### 5.2 Performance Benchmarks

**Metrics to Track:**
- CPU usage during countermeasures
- Battery impact at different threat levels
- Translation latency (on-device vs network)
- Memory footprint
- Frame rate during HUD rendering

### 5.3 App Store Preparation

**Challenges:**
1. **Private API Usage**: Ensure no private APIs used
2. **Entitlements**: Request special entitlements if needed
3. **Export Compliance**: Crypto export documentation
4. **Review Guidelines**: Security app compliance

---

## Phase 6: Deployment & Distribution (Month 9)

### 6.1 Distribution Options

**Option A: TestFlight Beta**
- Internal testing (100 testers)
- External testing (10,000 testers)
- Requires Apple Developer Program

**Option B: Enterprise Distribution**
- Internal deployment
- No App Store review
- Requires Enterprise Developer Program ($299/year)

**Option C: App Store**
- Public distribution
- Requires App Store review
- May face rejection for "security app" concerns

**Recommended:** Start with TestFlight, evaluate App Store feasibility

### 6.2 Documentation

**Required Documentation:**
- User Guide (iOS-specific features)
- Security Architecture Document
- API Documentation
- Migration Guide (Android → iOS)

---

## Technical Challenges & Solutions

### Challenge 1: iOS Sandbox Limitations

**Problem:** iOS apps run in strict sandbox, limited system access

**Solutions:**
- Use public APIs only
- Focus on app-level security
- Adapt countermeasures to iOS model

### Challenge 2: No perf_event_open

**Problem:** iOS doesn't expose performance counters

**Solutions:**
- Use `mach_absolute_time()` for timing
- Use `task_info()` for task metrics
- Accept reduced granularity

### Challenge 3: Background Execution

**Problem:** iOS limits background processing

**Solutions:**
- Use Background Tasks framework
- Request specific background modes
- Optimize for foreground operation

### Challenge 4: Neural Engine Access

**Problem:** Apple doesn't expose Neural Engine API

**Solutions:**
- Use CoreML (automatic ANE usage)
- Let iOS decide compute unit
- Profile with Instruments

---

## Resource Requirements

### Team

- **iOS Engineer** (Senior): Native development, C++ integration
- **Security Engineer**: Port security features, adapt to iOS
- **ML Engineer**: CoreML conversion, optimization
- **QA Engineer**: Testing, benchmarking

### Timeline

- **Months 1-2**: Foundation & C++ port
- **Months 3-4**: Security framework
- **Months 4-5**: Translation framework
- **Months 5-6**: UI & integration
- **Months 7-8**: Testing & optimization
- **Month 9**: Deployment

### Budget Estimate

- **Personnel**: 4 engineers × 9 months × $15k/month = $540k
- **Apple Developer Program**: $99/year
- **Testing Devices**: $5k (3× iPhone Pro)
- **Total**: ~$545k

---

## Success Criteria

### Functional Parity

- ✅ EL2/Hypervisor detection
- ✅ Adaptive countermeasures
- ✅ Memory protection
- ✅ Cache operations
- ✅ Timing obfuscation
- ✅ Danish-English translation
- ✅ Network offloading
- ✅ Mil-spec UI

### Performance Targets

- Translation latency < 100ms (on-device, A16+)
- CPU usage < 40% at maximum threat
- Battery impact < 30% reduction
- 60 FPS UI rendering

### App Store Readiness

- No private APIs
- Passes all automated checks
- Crypto export compliance
- User privacy compliance

---

## Conclusion

Porting EMMA to iOS is feasible but requires significant adaptation to iOS security model and APIs. The core C++ code is highly portable, but iOS constraints necessitate creative solutions for system-level features.

**Recommendation:** Proceed with Phase 1 foundation work, evaluate feasibility at each phase milestone before committing to App Store submission.

**Next Steps:**
1. Prototype iOS security framework (2 weeks)
2. Evaluate performance counter alternatives (1 week)
3. CoreML translation POC (1 week)
4. Decision point: Continue or pivot strategy

---

**Document Version:** 1.0
**Last Updated:** 2025-11-06
**Author:** EMMA Development Team
