#include "JniBridgeC.hpp"

#include <jni.h>
#include <android/log.h>
#include <sched.h>
#include <sys/resource.h>
#include <unistd.h>
#include <GLES2/gl2.h>

#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <string>
#include <utility>
#include <vector>

#include "LAppDefine.hpp"
#include "LAppDelegate.hpp"
#include "LAppLive2DManager.hpp"
#include "LAppView.hpp"
#include "LAppModel.hpp"
#include "LAppTextureManager.hpp"

using namespace Csm;

namespace {
constexpr const char* kLogTag = "M3ELive2D";
constexpr const char* kBridgeClass = "com/winkmoon/xinran/Live2DNative";

JavaVM* g_JVM = nullptr;
jclass g_BridgeClass = nullptr;
jmethodID g_GetAssetsMethodId = nullptr;
jmethodID g_LoadFileMethodId = nullptr;
jmethodID g_MoveTaskToBackMethodId = nullptr;
jmethodID g_ReportFrameMethodId = nullptr;
bool g_AdpfEnabled = false;

JNIEnv* GetEnv()
{
    if (g_JVM == nullptr)
    {
        return nullptr;
    }
    JNIEnv* env = nullptr;
    g_JVM->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    return env;
}

void ApplyRenderThreadTuning()
{
    std::vector<std::pair<long, int>> cores;
    char path[128];
    for (int cpu = 0; cpu < 128; ++cpu)
    {
        std::snprintf(path, sizeof(path),
                      "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", cpu);
        FILE* fp = std::fopen(path, "r");
        if (fp == nullptr)
        {
            continue;
        }
        long freq = 0;
        if (std::fscanf(fp, "%ld", &freq) == 1 && freq > 0)
        {
            cores.emplace_back(freq, cpu);
        }
        std::fclose(fp);
    }

    if (!cores.empty())
    {
        std::sort(cores.begin(), cores.end(),
                  [](const auto& a, const auto& b) { return a.first > b.first; });
        const size_t count = std::min<size_t>(4, std::max<size_t>(2, cores.size() / 2));
        cpu_set_t set;
        CPU_ZERO(&set);
        for (size_t i = 0; i < count; ++i)
        {
            CPU_SET(cores[i].second, &set);
        }
        if (sched_setaffinity(0, sizeof(set), &set) == 0)
        {
            __android_log_print(ANDROID_LOG_INFO, kLogTag,
                                "render thread tid=%d pinned to top %zu cores", gettid(), count);
        }
        else
        {
            __android_log_print(ANDROID_LOG_WARN, kLogTag,
                                "sched_setaffinity failed (errno=%d)", errno);
        }
    }

    // Raising nice requires CAP_SYS_NICE. It is optional and silently ignored.
    setpriority(PRIO_PROCESS, 0, -10);
}

void AppendJsonEscaped(std::string& out, const char* value)
{
    if (value == nullptr)
    {
        return;
    }
    for (const char* p = value; *p; ++p)
    {
        switch (*p)
        {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: out += *p; break;
        }
    }
}

jstring NewString(JNIEnv* env, const char* value)
{
    return env->NewStringUTF(value != nullptr ? value : "");
}

jobjectArray ToStringArray(JNIEnv* env, const std::vector<std::string>& values)
{
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(values.size()), stringClass, nullptr);
    for (size_t i = 0; i < values.size(); ++i)
    {
        jstring value = env->NewStringUTF(values[i].c_str());
        env->SetObjectArrayElement(array, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    env->DeleteLocalRef(stringClass);
    return array;
}
} // namespace

jint JNICALL JNI_OnLoad(JavaVM* vm, void*)
{
    g_JVM = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK)
    {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass(kBridgeClass);
    if (clazz == nullptr)
    {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Live2DNative class not found");
        return JNI_ERR;
    }
    g_BridgeClass = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
    env->DeleteLocalRef(clazz);

    g_GetAssetsMethodId = env->GetStaticMethodID(
        g_BridgeClass, "GetAssetList", "(Ljava/lang/String;)[Ljava/lang/String;");
    g_LoadFileMethodId = env->GetStaticMethodID(
        g_BridgeClass, "LoadFile", "(Ljava/lang/String;)[B");
    g_MoveTaskToBackMethodId = env->GetStaticMethodID(
        g_BridgeClass, "MoveTaskToBack", "()V");
    g_ReportFrameMethodId = env->GetStaticMethodID(
        g_BridgeClass, "ReportFrame", "(J)V");
    if (env->ExceptionCheck())
    {
        env->ExceptionClear();
    }

    return JNI_VERSION_1_6;
}

void JNICALL JNI_OnUnload(JavaVM*, void*)
{
    JNIEnv* env = GetEnv();
    if (env != nullptr && g_BridgeClass != nullptr)
    {
        env->DeleteGlobalRef(g_BridgeClass);
        g_BridgeClass = nullptr;
    }
}

Csm::csmVector<Csm::csmString> JniBridgeC::GetAssetList(const Csm::csmString& path)
{
    Csm::csmVector<Csm::csmString> list;
    JNIEnv* env = GetEnv();
    if (env == nullptr || g_GetAssetsMethodId == nullptr)
    {
        return list;
    }

    jstring jpath = env->NewStringUTF(path.GetRawString());
    jobjectArray obj = reinterpret_cast<jobjectArray>(
        env->CallStaticObjectMethod(g_BridgeClass, g_GetAssetsMethodId, jpath));
    env->DeleteLocalRef(jpath);
    if (obj == nullptr || env->ExceptionCheck())
    {
        env->ExceptionClear();
        return list;
    }

    const jsize size = env->GetArrayLength(obj);
    for (jsize i = 0; i < size; ++i)
    {
        jstring jstr = reinterpret_cast<jstring>(env->GetObjectArrayElement(obj, i));
        const char* chars = env->GetStringUTFChars(jstr, nullptr);
        list.PushBack(Csm::csmString(chars));
        env->ReleaseStringUTFChars(jstr, chars);
        env->DeleteLocalRef(jstr);
    }
    env->DeleteLocalRef(obj);
    return list;
}

char* JniBridgeC::LoadFileAsBytesFromJava(const char* filePath, unsigned int* outSize)
{
    JNIEnv* env = GetEnv();
    if (env == nullptr || g_LoadFileMethodId == nullptr || outSize == nullptr)
    {
        return nullptr;
    }

    jstring jpath = env->NewStringUTF(filePath);
    jbyteArray obj = reinterpret_cast<jbyteArray>(
        env->CallStaticObjectMethod(g_BridgeClass, g_LoadFileMethodId, jpath));
    env->DeleteLocalRef(jpath);
    if (obj == nullptr || env->ExceptionCheck())
    {
        env->ExceptionClear();
        return nullptr;
    }

    *outSize = static_cast<unsigned int>(env->GetArrayLength(obj));
    char* buffer = new char[*outSize];
    env->GetByteArrayRegion(obj, 0, static_cast<jsize>(*outSize),
                            reinterpret_cast<jbyte*>(buffer));
    env->DeleteLocalRef(obj);
    return buffer;
}

void JniBridgeC::MoveTaskToBack()
{
    JNIEnv* env = GetEnv();
    if (env != nullptr && g_MoveTaskToBackMethodId != nullptr)
    {
        env->CallStaticVoidMethod(g_BridgeClass, g_MoveTaskToBackMethodId);
    }
}

void JniBridgeC::ReportFrame(long long durationNanos)
{
    if (!g_AdpfEnabled || g_ReportFrameMethodId == nullptr)
    {
        return;
    }
    JNIEnv* env = GetEnv();
    if (env != nullptr)
    {
        env->CallStaticVoidMethod(g_BridgeClass, g_ReportFrameMethodId,
                                  static_cast<jlong>(durationNanos));
    }
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeOnStart(JNIEnv*, jclass)
{
    LAppDelegate::GetInstance()->OnStart();
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeOnPause(JNIEnv*, jclass)
{
    LAppDelegate::GetInstance()->OnPause();
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeOnStop(JNIEnv*, jclass)
{
    LAppDelegate::GetInstance()->OnStop();
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeOnDestroy(JNIEnv*, jclass)
{
    LAppDelegate::GetInstance()->OnDestroy();
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeOnSurfaceCreated(JNIEnv*, jclass)
{
    LAppDelegate::GetInstance()->OnSurfaceCreate();
    ApplyRenderThreadTuning();
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeOnSurfaceChanged(JNIEnv*, jclass, jint width, jint height)
{
    LAppDelegate::GetInstance()->OnSurfaceChanged(static_cast<float>(width),
                                                  static_cast<float>(height));
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeOnDrawFrame(JNIEnv*, jclass)
{
    LAppDelegate::GetInstance()->Run();
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeOnTouchesBegan(JNIEnv*, jclass, jfloat x, jfloat y)
{
    LAppDelegate::GetInstance()->OnTouchBegan(x, y);
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeOnTouchesEnded(JNIEnv*, jclass, jfloat x, jfloat y)
{
    LAppDelegate::GetInstance()->OnTouchEnded(x, y);
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeOnTouchesMoved(JNIEnv*, jclass, jfloat x, jfloat y)
{
    LAppDelegate::GetInstance()->OnTouchMoved(x, y);
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeOnTouchesCancelled(JNIEnv*, jclass)
{
    auto* view = LAppDelegate::GetInstance()->GetView();
    if (view != nullptr)
    {
        view->OnTouchesCancelled();
    }
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeSetModelScale(JNIEnv*, jclass, jfloat scale)
{
    LAppLive2DManager::GetInstance()->SetModelScale(scale);
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeTranslateModel(JNIEnv*, jclass, jfloat dx, jfloat dy)
{
    LAppLive2DManager::GetInstance()->TranslateModel(dx, dy);
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeResetModelPosition(JNIEnv*, jclass)
{
    LAppLive2DManager::GetInstance()->ResetModelPosition();
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeSetImportedRoot(JNIEnv* env, jclass, jstring path)
{
    if (path == nullptr)
    {
        LAppLive2DManager::SetImportedRoot("");
        return;
    }
    const char* chars = env->GetStringUTFChars(path, nullptr);
    LAppLive2DManager::SetImportedRoot(chars);
    env->ReleaseStringUTFChars(path, chars);
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeRefreshModels(JNIEnv*, jclass)
{
    LAppLive2DManager::GetInstance()->RefreshModels();
}

JNIEXPORT jobjectArray JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeGetModelNames(JNIEnv* env, jclass)
{
    std::vector<std::string> names;
    const auto* manager = LAppLive2DManager::GetInstance();
    const auto& dirs = manager->GetModelDirs();
    for (csmUint32 i = 0; i < dirs.GetSize(); ++i)
    {
        names.emplace_back(dirs[i].GetRawString());
    }
    return ToStringArray(env, names);
}

JNIEXPORT jint JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeGetModelCount(JNIEnv*, jclass)
{
    return static_cast<jint>(LAppLive2DManager::GetInstance()->GetModelNum());
}

JNIEXPORT jint JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeGetCurrentModelIndex(JNIEnv*, jclass)
{
    return static_cast<jint>(LAppLive2DManager::GetInstance()->GetCurrentModelIndex());
}

JNIEXPORT jboolean JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeSetModel(JNIEnv*, jclass, jint index)
{
    auto* manager = LAppLive2DManager::GetInstance();
    if (index < 0 || index >= static_cast<jint>(manager->GetModelNum()))
    {
        return JNI_FALSE;
    }
    manager->SetModel(index);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeNextModel(JNIEnv*, jclass)
{
    LAppLive2DManager::GetInstance()->NextScene();
    return JNI_TRUE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeGetMotionGroups(JNIEnv* env, jclass)
{
    std::vector<std::string> groups;
    auto* model = LAppLive2DManager::GetInstance()->GetCurrentModel();
    if (model != nullptr)
    {
        for (int i = 0; i < model->GetMotionGroupCount(); ++i)
        {
            groups.emplace_back(model->GetMotionGroupName(i));
        }
    }
    return ToStringArray(env, groups);
}

JNIEXPORT jint JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeGetMotionCount(JNIEnv* env, jclass, jstring group)
{
    auto* model = LAppLive2DManager::GetInstance()->GetCurrentModel();
    if (model == nullptr || group == nullptr)
    {
        return 0;
    }
    const char* chars = env->GetStringUTFChars(group, nullptr);
    const int count = model->GetMotionCount(chars);
    env->ReleaseStringUTFChars(group, chars);
    return count;
}

JNIEXPORT jboolean JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeStartMotion(JNIEnv* env, jclass, jstring group, jint index)
{
    auto* model = LAppLive2DManager::GetInstance()->GetCurrentModel();
    if (model == nullptr || group == nullptr || index < 0)
    {
        return JNI_FALSE;
    }
    const char* chars = env->GetStringUTFChars(group, nullptr);
    const auto handle = model->StartMotion(chars, index, LAppDefine::PriorityForce);
    env->ReleaseStringUTFChars(group, chars);
    return handle != Csm::InvalidMotionQueueEntryHandleValue ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeStartRandomMotion(JNIEnv* env, jclass, jstring group)
{
    auto* model = LAppLive2DManager::GetInstance()->GetCurrentModel();
    if (model == nullptr || group == nullptr)
    {
        return JNI_FALSE;
    }
    const char* chars = env->GetStringUTFChars(group, nullptr);
    const auto handle = model->StartRandomMotion(chars, LAppDefine::PriorityForce);
    env->ReleaseStringUTFChars(group, chars);
    return handle != Csm::InvalidMotionQueueEntryHandleValue ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeGetExpressions(JNIEnv* env, jclass)
{
    std::vector<std::string> expressions;
    auto* model = LAppLive2DManager::GetInstance()->GetCurrentModel();
    if (model != nullptr)
    {
        for (int i = 0; i < model->GetExpressionCount(); ++i)
        {
            expressions.emplace_back(model->GetExpressionName(i));
        }
    }
    return ToStringArray(env, expressions);
}

JNIEXPORT jboolean JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeSetExpression(JNIEnv* env, jclass, jstring name)
{
    auto* model = LAppLive2DManager::GetInstance()->GetCurrentModel();
    if (model == nullptr || name == nullptr)
    {
        return JNI_FALSE;
    }
    const char* chars = env->GetStringUTFChars(name, nullptr);
    model->SetExpression(chars);
    env->ReleaseStringUTFChars(name, chars);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeSetParameter(JNIEnv* env, jclass, jstring name, jfloat value)
{
    auto* model = LAppLive2DManager::GetInstance()->GetCurrentModel();
    if (model == nullptr || name == nullptr)
    {
        return;
    }
    const char* chars = env->GetStringUTFChars(name, nullptr);
    model->SetParameterValue(chars, value);
    env->ReleaseStringUTFChars(name, chars);
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeResetParameters(JNIEnv*, jclass)
{
    auto* model = LAppLive2DManager::GetInstance()->GetCurrentModel();
    if (model != nullptr)
    {
        model->ResetParameters();
    }
}

JNIEXPORT jstring JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeGetModelInfoJson(JNIEnv* env, jclass)
{
    auto* manager = LAppLive2DManager::GetInstance();
    auto* model = manager->GetCurrentModel();

    std::string json = "{";
    char buffer[256];
    std::snprintf(buffer, sizeof(buffer), "\"index\":%d,\"models\":%u",
                  static_cast<int>(manager->GetCurrentModelIndex()),
                  static_cast<unsigned>(manager->GetModelNum()));
    json += buffer;

    if (model != nullptr)
    {
        json += ",\"name\":\"";
        const auto& dirs = manager->GetModelDirs();
        const auto index = manager->GetCurrentModelIndex();
        if (index < dirs.GetSize())
        {
            AppendJsonEscaped(json, dirs[index].GetRawString());
        }
        json += "\"";

        std::snprintf(buffer, sizeof(buffer),
                      ",\"drawables\":%d,\"textures\":%d,\"textureMB\":%.2f",
                      model->GetDrawableCount(),
                      model->GetTextureCount(),
                      model->GetTextureMemoryMB());
        json += buffer;

        json += ",\"motions\":[";
        for (int i = 0; i < model->GetMotionGroupCount(); ++i)
        {
            if (i > 0)
            {
                json += ",";
            }
            json += "{\"group\":\"";
            AppendJsonEscaped(json, model->GetMotionGroupName(i));
            std::snprintf(buffer, sizeof(buffer), "\",\"count\":%d}",
                          model->GetMotionCount(model->GetMotionGroupName(i)));
            json += buffer;
        }
        json += "],\"expressions\":[";
        for (int i = 0; i < model->GetExpressionCount(); ++i)
        {
            if (i > 0)
            {
                json += ",";
            }
            json += "\"";
            AppendJsonEscaped(json, model->GetExpressionName(i));
            json += "\"";
        }
        json += "]";
    }
    json += "}";
    return NewString(env, json.c_str());
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeGetStats(JNIEnv* env, jclass, jfloatArray out)
{
    if (out == nullptr || env->GetArrayLength(out) < 12)
    {
        return;
    }
    auto* manager = LAppLive2DManager::GetInstance();
    auto* model = manager->GetCurrentModel();
    const float values[12] = {
        LAppDelegate::GetInstance()->GetFps(),
        LAppDelegate::GetInstance()->GetFrameTimeMs(),
        LAppDelegate::GetInstance()->GetCpuTimeMs(),
        model != nullptr ? static_cast<float>(model->GetLastDrawCallCount()) : 0.0f,
        model != nullptr ? static_cast<float>(model->GetLastVertexCount()) : 0.0f,
        static_cast<float>(LAppDelegate::GetInstance()->GetWindowWidth()),
        static_cast<float>(LAppDelegate::GetInstance()->GetWindowHeight()),
        model != nullptr ? model->GetTextureMemoryMB() : 0.0f,
        static_cast<float>(manager->GetCurrentModelIndex()),
        static_cast<float>(manager->GetModelNum()),
        model != nullptr ? model->GetMaskScale() : 1.0f,
        static_cast<float>(LAppDelegate::GetInstance()->GetRenderThreadTid())
    };
    env->SetFloatArrayRegion(out, 0, 12, values);
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeSetBackgroundColor(JNIEnv*, jclass,
                                                          jfloat r, jfloat g, jfloat b)
{
    LAppDelegate::GetInstance()->SetBackgroundColor(r, g, b, 1.0f);
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeSetMaskScale(JNIEnv*, jclass, jfloat scale)
{
    auto* manager = LAppLive2DManager::GetInstance();
    for (csmUint32 i = 0; i < manager->GetModelNum(); ++i)
    {
        auto* model = manager->GetModel(i);
        if (model != nullptr)
        {
            model->SetMaskScale(scale);
        }
    }
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeSetMipmaps(JNIEnv*, jclass, jboolean enabled)
{
    auto* manager = LAppDelegate::GetInstance()->GetTextureManager();
    if (manager != nullptr)
    {
        manager->SetMipmapsEnabled(enabled != JNI_FALSE);
    }
}

JNIEXPORT jint JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeGetRenderThreadTid(JNIEnv*, jclass)
{
    return static_cast<jint>(LAppDelegate::GetInstance()->GetRenderThreadTid());
}

JNIEXPORT void JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeSetAdpfEnabled(JNIEnv*, jclass, jboolean enabled)
{
    g_AdpfEnabled = enabled != JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_winkmoon_xinran_Live2DNative_nativeGetGlVersion(JNIEnv* env, jclass)
{
    const char* version = reinterpret_cast<const char*>(glGetString(GL_VERSION));
    const char* renderer = reinterpret_cast<const char*>(glGetString(GL_RENDERER));
    std::string text = version != nullptr ? version : "unknown";
    text += " | ";
    text += renderer != nullptr ? renderer : "unknown";
    return NewString(env, text.c_str());
}

} // extern "C"
