/**
 * M3E Live2D - JNI bridge.
 *
 * Asset/file plumbing is derived from the official Live2D Cubism Native
 * Android sample (Samples/OpenGL/Demo/proj.android.cmake), trimmed down and
 * extended with control + performance telemetry entry points.
 */

#pragma once
#include <Type/csmVector.hpp>
#include <Type/csmString.hpp>

class JniBridgeC
{
public:
    static Csm::csmVector<Csm::csmString> GetAssetList(const Csm::csmString& path);
    static char* LoadFileAsBytesFromJava(const char* filePath, unsigned int* outSize);
    static void MoveTaskToBack();
    static void ReportFrame(long long durationNanos);
};
