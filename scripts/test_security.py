#!/usr/bin/env python3

"""
EMMA-Android Security Test Suite
Tests EL2 detection and countermeasure effectiveness
"""

import subprocess
import sys
import time
import re
from typing import Optional

class SecurityTester:
    def __init__(self, device_serial: Optional[str] = None):
        self.device = device_serial
        self.adb_cmd = ["adb", "-s", device_serial] if device_serial else ["adb"]

    def run_adb(self, args: list) -> str:
        """Run adb command and return output"""
        cmd = self.adb_cmd + args
        result = subprocess.run(cmd, capture_output=True, text=True)
        return result.stdout

    def test_el2_detection(self) -> bool:
        """Test EL2 hypervisor detection"""
        print("🔍 Testing EL2 Detection...")

        # Trigger EL2 detection
        self.run_adb([
            "shell", "am", "broadcast",
            "-a", "im.molly.security.TEST_EL2"
        ])

        time.sleep(2)

        # Read logcat for detection results
        log = self.run_adb([
            "logcat", "-d", "-s", "EL2Detector:*"
        ])

        if "Threat analysis complete" in log:
            print("  ✓ EL2 detection functional")

            # Extract threat level
            match = re.search(r"threat_level=([\d.]+)", log)
            if match:
                threat = float(match.group(1))
                print(f"  Detected threat level: {threat:.1%}")

            return True
        else:
            print("  ✗ EL2 detection failed")
            return False

    def test_cache_poisoning(self) -> bool:
        """Test cache poisoning countermeasure"""
        print("🔍 Testing Cache Poisoning...")

        self.run_adb([
            "shell", "am", "broadcast",
            "-a", "im.molly.security.TEST_CACHE_POISON",
            "--ei", "intensity", "50"
        ])

        time.sleep(1)

        log = self.run_adb([
            "logcat", "-d", "-s", "CacheOperations:*", "-t", "10"
        ])

        if "Cache poisoned" in log:
            print("  ✓ Cache poisoning functional")
            return True
        else:
            print("  ✗ Cache poisoning failed")
            return False

    def test_memory_scrambling(self) -> bool:
        """Test memory scrambling"""
        print("🔍 Testing Memory Scrambling...")

        self.run_adb([
            "shell", "am", "broadcast",
            "-a", "im.molly.security.TEST_MEMORY_SCRAMBLE"
        ])

        time.sleep(2)

        log = self.run_adb([
            "logcat", "-d", "-s", "MemoryScrambler:*", "-t", "10"
        ])

        if "decoy patterns" in log.lower():
            print("  ✓ Memory scrambling functional")
            return True
        else:
            print("  ✗ Memory scrambling failed")
            return False

    def test_timing_obfuscation(self) -> bool:
        """Test timing obfuscation"""
        print("🔍 Testing Timing Obfuscation...")

        # Measure baseline timing
        start = time.time()
        self.run_adb([
            "shell", "am", "broadcast",
            "-a", "im.molly.security.TEST_TIMING"
        ])
        baseline = time.time() - start

        # Measure with obfuscation
        start = time.time()
        self.run_adb([
            "shell", "am", "broadcast",
            "-a", "im.molly.security.TEST_TIMING",
            "--ei", "chaos", "100"
        ])
        obfuscated = time.time() - start

        variance = abs(obfuscated - baseline) / baseline

        if variance > 0.1:  # 10% variance
            print(f"  ✓ Timing obfuscation functional ({variance:.1%} variance)")
            return True
        else:
            print(f"  ⚠ Low timing variance ({variance:.1%})")
            return False

    def test_threat_simulation(self, level: int) -> bool:
        """Test threat level simulation"""
        print(f"🔍 Testing Threat Simulation ({level}%)...")

        self.run_adb([
            "shell", "am", "broadcast",
            "-a", "im.molly.security.SIMULATE_THREAT",
            "--ei", "threat_level", str(level)
        ])

        time.sleep(1)

        log = self.run_adb([
            "logcat", "-d", "-s", "AdaptiveCountermeasures:*", "-t", "10"
        ])

        if "countermeasures" in log.lower():
            print(f"  ✓ Threat simulation functional at {level}%")
            return True
        else:
            print(f"  ✗ Threat simulation failed")
            return False

    def run_all_tests(self):
        """Run comprehensive test suite"""
        print("=" * 60)
        print("EMMA-Android Security Test Suite")
        print("=" * 60)
        print()

        tests = [
            ("EL2 Detection", self.test_el2_detection),
            ("Cache Poisoning", self.test_cache_poisoning),
            ("Memory Scrambling", self.test_memory_scrambling),
            ("Timing Obfuscation", self.test_timing_obfuscation),
            ("Threat Simulation (50%)", lambda: self.test_threat_simulation(50)),
            ("Threat Simulation (85%)", lambda: self.test_threat_simulation(85)),
        ]

        results = []
        for name, test in tests:
            try:
                passed = test()
                results.append((name, passed))
            except Exception as e:
                print(f"  ✗ Error: {e}")
                results.append((name, False))
            print()

        # Summary
        print("=" * 60)
        print("Test Summary")
        print("=" * 60)

        passed = sum(1 for _, result in results if result)
        total = len(results)

        for name, result in results:
            status = "✓ PASS" if result else "✗ FAIL"
            print(f"{status:8} {name}")

        print()
        print(f"Passed: {passed}/{total} ({passed/total:.1%})")

        return passed == total

def main():
    if len(sys.argv) > 1:
        device = sys.argv[1]
    else:
        device = None

    tester = SecurityTester(device)
    success = tester.run_all_tests()

    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
