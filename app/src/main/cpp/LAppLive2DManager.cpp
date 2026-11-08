/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "LAppLive2DManager.hpp"

#include <GLES2/gl2.h>
#include <Rendering/CubismRenderer.hpp>
#include <Rendering/OpenGL/CubismOffscreenManager_OpenGLES2.hpp>
#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <string>
#include <sys/stat.h>
#include <vector>

#include "JniBridgeC.hpp"
#include "LAppDefine.hpp"
#include "LAppDelegate.hpp"
#include "LAppModel.hpp"
#include "LAppPal.hpp"
#include "LAppView.hpp"

using namespace Csm;
using namespace LAppDefine;

namespace {
LAppLive2DManager* s_instance = NULL;

void BeganMotion(ACubismMotion* self)
{
    LAppPal::PrintLogLn("Motion Began: %p", self);
}

void FinishedMotion(ACubismMotion* self)
{
    LAppPal::PrintLogLn("Motion Finished: %p", self);
}

Csm::csmString s_importedRoot;

bool EndsWith(const char* value, const char* suffix)
{
    if (value == nullptr || suffix == nullptr)
    {
        return false;
    }
    const size_t valueLength = strlen(value);
    const size_t suffixLength = strlen(suffix);
    return valueLength >= suffixLength &&
           strcmp(value + valueLength - suffixLength, suffix) == 0;
}

struct ModelEntry
{
    Csm::csmString name;
    Csm::csmString basePath;
    Csm::csmString jsonName;
};
} // namespace

LAppLive2DManager* LAppLive2DManager::GetInstance()
{
    if (s_instance == NULL)
    {
        s_instance = new LAppLive2DManager();
    }
    return s_instance;
}

void LAppLive2DManager::ReleaseInstance()
{
    if (s_instance != NULL)
    {
        delete s_instance;
    }
    s_instance = NULL;
}

LAppLive2DManager::LAppLive2DManager()
    : _viewMatrix(NULL)
    , _initialized(false)
    , _modelScale(1.0f)
    , _modelOffsetX(0.0f)
    , _modelOffsetY(0.0f)
{
    _viewMatrix = new CubismMatrix44();
    SetUpModel();
}

LAppLive2DManager::~LAppLive2DManager()
{
    ReleaseAllModel();
    delete _viewMatrix;
    Csm::Rendering::CubismOffscreenManager_OpenGLES2::ReleaseInstance();
}

void LAppLive2DManager::ReleaseAllModel()
{
    for (csmUint32 i = 0; i < _models.GetSize(); i++)
    {
        delete _models[i];
    }
    _models.Clear();
    _initialized = false;
}

void LAppLive2DManager::SetUpModel()
{
    const char MODEL3_JSON[] = u8".model3.json";
    std::vector<ModelEntry> entries;

    // Bundled models in assets.
    Csm::csmVector<Csm::csmString> root = JniBridgeC::GetAssetList("");
    for (size_t i = 0; i < root.GetSize(); i++)
    {
        Csm::csmString target(root[i]);
        target.Append(MODEL3_JSON, sizeof(MODEL3_JSON) - 1);
        Csm::csmVector<Csm::csmString> sub = JniBridgeC::GetAssetList(root[i].GetRawString());
        for (size_t j = 0; j < sub.GetSize(); j++)
        {
            if (target == sub[j])
            {
                Csm::csmString basePath(ResourcesPath);
                basePath += root[i];
                basePath += "/";
                entries.push_back({root[i], basePath, target});
                break;
            }
        }
    }

    // Imported models from filesDir/models. Each child directory must contain
    // at least one *.model3.json; the JSON filename does not have to match the
    // folder name.
    if (s_importedRoot.GetLength() > 0)
    {
        DIR* rootDir = opendir(s_importedRoot.GetRawString());
        if (rootDir != nullptr)
        {
            struct dirent* child = nullptr;
            while ((child = readdir(rootDir)) != nullptr)
            {
                if (child->d_name[0] == '.')
                {
                    continue;
                }
                std::string modelDir = std::string(s_importedRoot.GetRawString()) +
                                       "/" + child->d_name;
                struct stat st;
                if (stat(modelDir.c_str(), &st) != 0 || !S_ISDIR(st.st_mode))
                {
                    continue;
                }

                std::string jsonName;
                DIR* modelFiles = opendir(modelDir.c_str());
                if (modelFiles != nullptr)
                {
                    struct dirent* file = nullptr;
                    while ((file = readdir(modelFiles)) != nullptr)
                    {
                        if (EndsWith(file->d_name, MODEL3_JSON))
                        {
                            jsonName = file->d_name;
                            break;
                        }
                    }
                    closedir(modelFiles);
                }
                if (jsonName.empty())
                {
                    continue;
                }

                Csm::csmString basePath(modelDir.c_str());
                basePath += "/";
                entries.push_back({Csm::csmString(child->d_name), basePath,
                                   Csm::csmString(jsonName.c_str())});
            }
            closedir(rootDir);
        }
    }

    std::sort(entries.begin(), entries.end(), [](const ModelEntry& a, const ModelEntry& b) {
        return strcmp(a.name.GetRawString(), b.name.GetRawString()) < 0;
    });

    _modelDir.Clear();
    _modelBase.Clear();
    _modelJson.Clear();
    for (const auto& entry : entries)
    {
        _modelDir.PushBack(entry.name);
        _modelBase.PushBack(entry.basePath);
        _modelJson.PushBack(entry.jsonName);
    }
    _initialized = false;
}

void LAppLive2DManager::RefreshModels()
{
    Csm::csmString current;
    const Csm::csmInt32 currentIndex = GetCurrentModelIndex();
    if (currentIndex >= 0 && currentIndex < static_cast<Csm::csmInt32>(_modelDir.GetSize()))
    {
        current = _modelDir[currentIndex];
    }

    SetUpModel();

    Csm::csmInt32 nextIndex = 0;
    for (Csm::csmUint32 i = 0; i < _modelDir.GetSize(); ++i)
    {
        if (_modelDir[i] == current)
        {
            nextIndex = static_cast<Csm::csmInt32>(i);
            break;
        }
    }
    LAppDelegate::GetInstance()->SetSceneIndex(nextIndex);
}

void LAppLive2DManager::SetImportedRoot(const Csm::csmChar* root)
{
    s_importedRoot = root != nullptr ? root : "";
}

void LAppLive2DManager::EnsureInitialized()
{
    if (!CubismFramework::IsInitialized())
    {
        return;
    }
    if (_initialized || _modelDir.GetSize() == 0)
    {
        return;
    }
    if (LAppDelegate::GetInstance()->GetWindowWidth() <= 0 ||
        LAppDelegate::GetInstance()->GetWindowHeight() <= 0)
    {
        return;
    }

    csmInt32 index = LAppDelegate::GetInstance()->GetSceneIndex();
    if (index < 0 || index >= static_cast<csmInt32>(_modelDir.GetSize()))
    {
        index = 0;
    }
    ChangeScene(index);
    _initialized = true;
}

LAppModel* LAppLive2DManager::GetModel(csmUint32 no) const
{
    if (no < _models.GetSize())
    {
        return _models[no];
    }
    return NULL;
}

LAppModel* LAppLive2DManager::GetCurrentModel() const
{
    // Only one model is resident at a time. _SceneIndex is a model-directory
    // index (0..N-1), not an index into _models, so indexing _models by it
    // returned nullptr for every model except the first one.
    if (_models.GetSize() == 0)
    {
        return nullptr;
    }
    return _models[0];
}

void LAppLive2DManager::SetRenderTargetSize(csmUint32 width, csmUint32 height)
{
    for (csmUint32 i = 0; i < _models.GetSize(); i++)
    {
        LAppModel* model = GetModel(i);
        if (model != nullptr)
        {
            model->SetRenderTargetSize(width, height);
            model->ApplyMaskScale();
        }
    }
}

void LAppLive2DManager::OnDrag(csmFloat32 x, csmFloat32 y) const
{
    for (csmUint32 i = 0; i < _models.GetSize(); i++)
    {
        LAppModel* model = GetModel(i);
        if (model != nullptr)
        {
            model->SetDragging(x, y);
        }
    }
}

void LAppLive2DManager::OnTap(csmFloat32 x, csmFloat32 y)
{
    const int width = LAppDelegate::GetInstance()->GetWindowWidth();
    const int height = LAppDelegate::GetInstance()->GetWindowHeight();
    if (width <= 0 || height <= 0)
    {
        return;
    }
    const float aspectRatio = static_cast<float>(width) / static_cast<float>(height);
    const float displayRatio = static_cast<float>(height) / static_cast<float>(width);

    LAppModel* model = GetCurrentModel();
    if (model == nullptr || model->GetModel() == nullptr)
    {
        return;
    }

    const float canvasRatio = model->GetModel()->GetCanvasHeight() /
                              model->GetModel()->GetCanvasWidth();
    csmFloat32 adjustedX = x;
    csmFloat32 adjustedY = y;
    if (canvasRatio < displayRatio)
    {
        adjustedX = x / aspectRatio;
        adjustedY = y / aspectRatio;
    }

    if (model->HitTest(HitAreaNameHead, adjustedX, adjustedY))
    {
        model->SetRandomExpression();
    }
    else if (model->HitTest(HitAreaNameBody, adjustedX, adjustedY))
    {
        model->StartRandomMotion(MotionGroupTapBody, PriorityNormal, FinishedMotion, BeganMotion);
    }
}

void LAppLive2DManager::OnUpdate() const
{
    if (_models.GetSize() == 0)
    {
        return;
    }

    const int width = LAppDelegate::GetInstance()->GetWindowWidth();
    const int height = LAppDelegate::GetInstance()->GetWindowHeight();
    if (width <= 0 || height <= 0)
    {
        return;
    }
    const float aspectRatio = static_cast<float>(width) / static_cast<float>(height);
    const float displayRatio = static_cast<float>(height) / static_cast<float>(width);

    Csm::Rendering::CubismOffscreenManager_OpenGLES2::GetInstance()->BeginFrameProcess();

    for (csmUint32 i = 0; i < _models.GetSize(); ++i)
    {
        CubismMatrix44 projection;
        LAppModel* model = GetModel(i);
        if (model == nullptr || model->GetModel() == NULL)
        {
            continue;
        }

        const float canvasRatio = model->GetModel()->GetCanvasHeight() /
                                  model->GetModel()->GetCanvasWidth();
        if (canvasRatio < displayRatio)
        {
            model->GetModelMatrix()->SetWidth(2.0f);
            projection.Scale(1.0f, aspectRatio);
        }
        else
        {
            model->GetModelMatrix()->SetHeight(2.0f);
            projection.Scale(1.0f / aspectRatio, 1.0f);
        }

        // Apply zoom to the model matrix so the screen aspect projection
        // remains untouched. A uniform model scale keeps the model's
        // original proportions at every zoom level.
        model->GetModelMatrix()->ScaleRelative(_modelScale, _modelScale);
        model->SetDrawOffset(_modelOffsetX, _modelOffsetY);

        if (_viewMatrix != NULL)
        {
            projection.MultiplyByMatrix(_viewMatrix);
        }

        if (LAppDelegate::GetInstance()->GetView() != nullptr)
        {
            LAppDelegate::GetInstance()->GetView()->PreModelDraw(*model);
        }
        model->Update();
        model->Draw(projection);
        if (LAppDelegate::GetInstance()->GetView() != nullptr)
        {
            LAppDelegate::GetInstance()->GetView()->PostModelDraw(*model);
        }
    }

    Csm::Rendering::CubismOffscreenManager_OpenGLES2::GetInstance()->EndFrameProcess();
    Csm::Rendering::CubismOffscreenManager_OpenGLES2::GetInstance()->ReleaseStaleRenderTextures();
}

void LAppLive2DManager::NextScene()
{
    if (_modelDir.GetSize() == 0)
    {
        return;
    }
    const csmInt32 no = (GetCurrentModelIndex() + 1) % static_cast<csmInt32>(_modelDir.GetSize());
    ChangeScene(no);
}

void LAppLive2DManager::ChangeScene(Csm::csmInt32 index)
{
    if (!CubismFramework::IsInitialized() || _modelDir.GetSize() == 0)
    {
        return;
    }
    if (index < 0 || index >= static_cast<csmInt32>(_modelDir.GetSize()))
    {
        index = 0;
    }

    LAppDelegate::GetInstance()->SetSceneIndex(index);

    if (index >= static_cast<csmInt32>(_modelBase.GetSize()) ||
        index >= static_cast<csmInt32>(_modelJson.GetSize()))
    {
        return;
    }
    csmString modelPath(_modelBase[index]);
    csmString modelJsonName(_modelJson[index]);

    ReleaseAllModel();
    _modelOffsetX = 0.0f;
    _modelOffsetY = 0.0f;
    _models.PushBack(new LAppModel());
    _models[0]->LoadAssets(modelPath.GetRawString(), modelJsonName.GetRawString());

    if (LAppDelegate::GetInstance()->GetView() != nullptr)
    {
        LAppDelegate::GetInstance()->GetView()->SwitchRenderingTarget(LAppView::SelectTarget_None);
        const float clearColor[3] = {0.0f, 0.0f, 0.0f};
        LAppDelegate::GetInstance()->GetView()->SetRenderTargetClearColor(
            clearColor[0], clearColor[1], clearColor[2]);
    }

    _initialized = true;
}

void LAppLive2DManager::SetModel(Csm::csmInt32 index)
{
    if (index >= 0 && index < static_cast<csmInt32>(_modelDir.GetSize()))
    {
        ChangeScene(index);
    }
}

csmUint32 LAppLive2DManager::GetModelNum() const
{
    return _modelDir.GetSize();
}

csmInt32 LAppLive2DManager::GetCurrentModelIndex() const
{
    return LAppDelegate::GetInstance()->GetSceneIndex();
}

void LAppLive2DManager::SetParameterValue(const csmChar* id, csmFloat32 value)
{
    for (csmUint32 i = 0; i < _models.GetSize(); i++)
    {
        if (_models[i] != nullptr)
        {
            _models[i]->SetParameterValue(id, value);
        }
    }
}

void LAppLive2DManager::ResetParameters()
{
    for (csmUint32 i = 0; i < _models.GetSize(); i++)
    {
        if (_models[i] != nullptr)
        {
            _models[i]->ResetParameters();
        }
    }
}

void LAppLive2DManager::ReloadAllRenderers()
{
    for (csmUint32 i = 0; i < _models.GetSize(); i++)
    {
        if (_models[i] != nullptr)
        {
            _models[i]->ReloadRenderer();
        }
    }
}

void LAppLive2DManager::SetModelScale(csmFloat32 scale)
{
    if (scale < 0.5f)
    {
        scale = 0.5f;
    }
    if (scale > 3.0f)
    {
        scale = 3.0f;
    }
    _modelScale = scale;
}

void LAppLive2DManager::TranslateModel(csmFloat32 dxPixels, csmFloat32 dyPixels)
{
    const int width = LAppDelegate::GetInstance()->GetWindowWidth();
    const int height = LAppDelegate::GetInstance()->GetWindowHeight();
    if (width <= 0 || height <= 0)
    {
        return;
    }

    // Convert screen pixels to clip-space units. Y is inverted because
    // Android touch coordinates grow downward while clip-space Y grows upward.
    _modelOffsetX += (2.0f * dxPixels) / static_cast<csmFloat32>(width);
    _modelOffsetY -= (2.0f * dyPixels) / static_cast<csmFloat32>(height);

    _modelOffsetX = std::max(-1.6f, std::min(1.6f, _modelOffsetX));
    _modelOffsetY = std::max(-1.6f, std::min(1.6f, _modelOffsetY));

    LAppModel* model = GetCurrentModel();
    if (model != nullptr)
    {
        model->SetDrawOffset(_modelOffsetX, _modelOffsetY);
    }
}

void LAppLive2DManager::ResetModelPosition()
{
    _modelOffsetX = 0.0f;
    _modelOffsetY = 0.0f;
    LAppModel* model = GetCurrentModel();
    if (model != nullptr)
    {
        model->SetDrawOffset(0.0f, 0.0f);
    }
}

void LAppLive2DManager::SetViewMatrix(CubismMatrix44* m)
{
    for (int i = 0; i < 16; i++)
    {
        _viewMatrix->GetArray()[i] = m->GetArray()[i];
    }
}
