/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "LAppDelegate.hpp"

#include <GLES2/gl2.h>
#include <unistd.h>

#include <algorithm>
#include <cmath>
#include <ctime>

#include "JniBridgeC.hpp"
#include "LAppDefine.hpp"
#include "LAppLive2DManager.hpp"
#include "LAppModel.hpp"
#include "LAppPal.hpp"
#include "LAppTextureManager.hpp"
#include "LAppView.hpp"

#include <Rendering/OpenGL/CubismShader_OpenGLES2.hpp>

using namespace Csm;
using namespace std;
using namespace LAppDefine;

namespace {
LAppDelegate* s_instance = NULL;

double NowSeconds()
{
    timespec res;
    clock_gettime(CLOCK_MONOTONIC, &res);
    return static_cast<double>(res.tv_sec) + static_cast<double>(res.tv_nsec) * 1e-9;
}
} // namespace

LAppDelegate* LAppDelegate::GetInstance()
{
    if (s_instance == NULL)
    {
        s_instance = new LAppDelegate();
    }
    return s_instance;
}

void LAppDelegate::ReleaseInstance()
{
    if (s_instance != NULL)
    {
        delete s_instance;
    }
    s_instance = NULL;
}

void LAppDelegate::OnStart()
{
    _isActive = true;
}

void LAppDelegate::OnPause()
{
}

void LAppDelegate::OnStop()
{
    // Keep GL/model resources alive across background/resume. Releasing the
    // framework here raced with late onSurfaceChanged callbacks and made
    // ChangeScene() dereference a disposed CubismIdManager.
    _isActive = false;
}

void LAppDelegate::OnDestroy()
{
    if (_view)
    {
        delete _view;
        _view = NULL;
    }
    if (_textureManager)
    {
        delete _textureManager;
        _textureManager = NULL;
    }

    LAppLive2DManager::ReleaseInstance();
    CubismFramework::Dispose();
    ReleaseInstance();
}

void LAppDelegate::Run()
{
    LAppPal::UpdateTime();

    const double frameStart = NowSeconds();
    double deltaTime = LAppPal::GetDeltaTime();
    if (deltaTime < 0.0 || deltaTime > 0.25)
    {
        deltaTime = 0.016;
    }

    glClearColor(_backgroundColor[0], _backgroundColor[1], _backgroundColor[2], _backgroundColor[3]);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glClearDepthf(1.0f);

    if (_view != NULL)
    {
        _view->Render();
    }

    const double cpuMs = (NowSeconds() - frameStart) * 1000.0;
    _cpuTimeMs = _cpuTimeMs <= 0.0f ? static_cast<float>(cpuMs)
                                    : _cpuTimeMs * 0.90f + static_cast<float>(cpuMs) * 0.10f;
    _frameTimeMs = _frameTimeMs <= 0.0f ? static_cast<float>(deltaTime * 1000.0)
                                        : _frameTimeMs * 0.90f + static_cast<float>(deltaTime * 1000.0) * 0.10f;

    ++_fpsFrameCount;
    const double now = NowSeconds();
    const double elapsed = now - _fpsWindowStart;
    if (elapsed >= 0.5)
    {
        _fps = static_cast<float>(_fpsFrameCount / elapsed);
        _fpsFrameCount = 0;
        _fpsWindowStart = now;
    }

    if (_adpfEnabled)
    {
        JniBridgeC::ReportFrame(static_cast<long long>(cpuMs * 1000000.0));
    }

    if (!_isActive)
    {
        JniBridgeC::MoveTaskToBack();
    }
}

void LAppDelegate::OnSurfaceCreate()
{
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

    if (_textureManager == nullptr)
    {
        _textureManager = new LAppTextureManager();
    }
    else
    {
        _textureManager->ReleaseInvalidTextures();
    }

    if (_view != NULL)
    {
        delete _view;
    }
    _view = new LAppView();
    LAppPal::UpdateTime();

    if (!CubismFramework::IsInitialized())
    {
        CubismFramework::Initialize();
    }

    // Drop shader programs belonging to the lost EGL context.
    Rendering::CubismShader_OpenGLES2::GetInstance()->ReleaseInvalidShaderProgram();
    Rendering::CubismShader_OpenGLES2::DeleteInstance();

    _renderThreadTid = static_cast<int>(gettid());
    _fpsWindowStart = NowSeconds();
    _fpsFrameCount = 0;
    _fps = 0.0f;
    _frameTimeMs = 0.0f;
    _cpuTimeMs = 0.0f;

    // On context loss, existing models need fresh GPU resources.
    LAppLive2DManager* manager = LAppLive2DManager::GetInstance();
    for (csmUint32 i = 0; i < manager->GetModelNum(); ++i)
    {
        LAppModel* model = manager->GetModel(i);
        if (model != nullptr)
        {
            model->ReloadRenderer();
        }
    }
}

void LAppDelegate::OnSurfaceChanged(float width, float height)
{
    _width = std::max(1, static_cast<int>(width));
    _height = std::max(1, static_cast<int>(height));
    glViewport(0, 0, _width, _height);

    if (_view != NULL)
    {
        _view->Initialize(_width, _height);
    }

    if (!CubismFramework::IsInitialized())
    {
        return;
    }

    LAppLive2DManager* manager = LAppLive2DManager::GetInstance();
    manager->EnsureInitialized();
    manager->SetRenderTargetSize(_width, _height);
}

void LAppDelegate::SetSceneIndex(int index)
{
    _SceneIndex = index;
}

LAppDelegate::LAppDelegate()
    : _cubismOption()
    , _textureManager(NULL)
    , _view(NULL)
    , _width(0)
    , _height(0)
    , _SceneIndex(0)
    , _captured(false)
    , _isActive(true)
    , _mouseX(0.0f)
    , _mouseY(0.0f)
    , _fpsWindowStart(0.0)
    , _fpsFrameCount(0)
    , _fps(0.0f)
    , _frameTimeMs(0.0f)
    , _cpuTimeMs(0.0f)
    , _renderThreadTid(0)
    , _adpfEnabled(false)
{
    _backgroundColor[0] = 0.043f;
    _backgroundColor[1] = 0.059f;
    _backgroundColor[2] = 0.102f;
    _backgroundColor[3] = 1.0f;

    _cubismOption.LogFunction = LAppPal::PrintMessageLn;
    _cubismOption.LoggingLevel = LAppDefine::CubismLoggingLevel;
    _cubismOption.LoadFileFunction = LAppPal::LoadFileAsBytes;
    _cubismOption.ReleaseBytesFunction = LAppPal::ReleaseBytes;
    CubismFramework::CleanUp();
    CubismFramework::StartUp(&_cubismAllocator, &_cubismOption);
}

LAppDelegate::~LAppDelegate()
{
}

void LAppDelegate::SetBackgroundColor(float r, float g, float b, float a)
{
    _backgroundColor[0] = std::clamp(r, 0.0f, 1.0f);
    _backgroundColor[1] = std::clamp(g, 0.0f, 1.0f);
    _backgroundColor[2] = std::clamp(b, 0.0f, 1.0f);
    _backgroundColor[3] = std::clamp(a, 0.0f, 1.0f);
}

void LAppDelegate::OnTouchBegan(double x, double y)
{
    _mouseX = static_cast<float>(x);
    _mouseY = static_cast<float>(y);

    if (_view != NULL)
    {
        _captured = true;
        _view->OnTouchesBegan(_mouseX, _mouseY);
    }
}

void LAppDelegate::OnTouchEnded(double x, double y)
{
    _mouseX = static_cast<float>(x);
    _mouseY = static_cast<float>(y);

    if (_view != NULL)
    {
        _captured = false;
        _view->OnTouchesEnded(_mouseX, _mouseY);
    }
}

void LAppDelegate::OnTouchMoved(double x, double y)
{
    _mouseX = static_cast<float>(x);
    _mouseY = static_cast<float>(y);

    if (_captured && _view != NULL)
    {
        _view->OnTouchesMoved(_mouseX, _mouseY);
    }
}
