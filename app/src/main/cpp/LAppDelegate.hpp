/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include "LAppAllocator_Common.hpp"

class LAppView;
class LAppTextureManager;

class LAppDelegate
{
public:
    static LAppDelegate* GetInstance();
    static void ReleaseInstance();

    void OnStart();
    void OnPause();
    void OnStop();
    void OnDestroy();

    void OnSurfaceCreate();
    void OnSurfaceChanged(float width, float height);

    void Run();

    void OnTouchBegan(double x, double y);
    void OnTouchEnded(double x, double y);
    void OnTouchMoved(double x, double y);

    LAppTextureManager* GetTextureManager() { return _textureManager; }
    int GetWindowWidth() { return _width; }
    int GetWindowHeight() { return _height; }

    void DeActivateApp() { _isActive = false; }
    LAppView* GetView() { return _view; }

    void SetSceneIndex(int index);
    int GetSceneIndex() { return _SceneIndex; }

    void SetBackgroundColor(float r, float g, float b, float a = 1.0f);
    void SetAdpfEnabled(bool enabled) { _adpfEnabled = enabled; }

    float GetFps() const { return _fps; }
    float GetFrameTimeMs() const { return _frameTimeMs; }
    float GetCpuTimeMs() const { return _cpuTimeMs; }
    int GetRenderThreadTid() const { return _renderThreadTid; }

private:
    LAppDelegate();
    ~LAppDelegate();

    LAppAllocator_Common _cubismAllocator;
    Csm::CubismFramework::Option _cubismOption;
    LAppTextureManager* _textureManager;
    LAppView* _view;
    int _width;
    int _height;
    int _SceneIndex;
    bool _captured;
    bool _isActive;
    float _mouseY;
    float _mouseX;

    float _backgroundColor[4];
    double _fpsWindowStart;
    int _fpsFrameCount;
    float _fps;
    float _frameTimeMs;
    float _cpuTimeMs;
    int _renderThreadTid;
    bool _adpfEnabled;
};
