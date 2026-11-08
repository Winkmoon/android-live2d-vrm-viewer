/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include <CubismFramework.hpp>
#include <Math/CubismMatrix44.hpp>
#include <Type/csmVector.hpp>
#include <Type/csmString.hpp>

class LAppModel;

class LAppLive2DManager
{
public:
    static LAppLive2DManager* GetInstance();
    static void ReleaseInstance();

    void SetUpModel();
    void RefreshModels();
    static void SetImportedRoot(const Csm::csmChar* root);

    // Loads the initial model once the GL surface has a valid size.
    void EnsureInitialized();

    LAppModel* GetModel(Csm::csmUint32 no) const;
    LAppModel* GetCurrentModel() const;

    void SetRenderTargetSize(Csm::csmUint32 width, Csm::csmUint32 height);
    void ReleaseAllModel();

    void OnDrag(Csm::csmFloat32 x, Csm::csmFloat32 y) const;
    void OnTap(Csm::csmFloat32 x, Csm::csmFloat32 y);
    void OnUpdate() const;

    void NextScene();
    void ChangeScene(Csm::csmInt32 index);
    void SetModel(Csm::csmInt32 index);

    Csm::csmUint32 GetModelNum() const;
    Csm::csmInt32 GetCurrentModelIndex() const;
    const Csm::csmVector<Csm::csmString>& GetModelDirs() const { return _modelDir; }

    void SetParameterValue(const Csm::csmChar* id, Csm::csmFloat32 value);
    void ResetParameters();
    void ReloadAllRenderers();

    void SetViewMatrix(Live2D::Cubism::Framework::CubismMatrix44* m);

private:
    LAppLive2DManager();
    virtual ~LAppLive2DManager();

    Csm::CubismMatrix44* _viewMatrix;
    Csm::csmVector<LAppModel*> _models;
    Csm::csmVector<Csm::csmString> _modelDir;
    Csm::csmVector<Csm::csmString> _modelBase;
    Csm::csmVector<Csm::csmString> _modelJson;
    bool _initialized;
};
