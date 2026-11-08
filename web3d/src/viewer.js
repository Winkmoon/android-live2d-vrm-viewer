import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';

const app = document.getElementById('app');
const statusEl = document.getElementById('status');
const overlay = document.getElementById('overlay');

const renderer = new THREE.WebGLRenderer({
  antialias: true,
  alpha: true,
  powerPreference: 'high-performance',
});
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.05;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFShadowMap;
app.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(
  30,
  window.innerWidth / window.innerHeight,
  0.01,
  100
);
camera.position.set(0, 1.0, 3.0);

const controls = new OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.dampingFactor = 0.075;
controls.enablePan = false;
controls.minDistance = 0.35;
controls.maxDistance = 20;
controls.maxPolarAngle = Math.PI * 0.96;
controls.target.set(0, 0.9, 0);
controls.update();

scene.add(new THREE.HemisphereLight(0xffffff, 0x334455, 2.4));

const keyLight = new THREE.DirectionalLight(0xffffff, 2.2);
keyLight.position.set(1.8, 3.2, 2.4);
keyLight.castShadow = true;
keyLight.shadow.mapSize.set(1024, 1024);
keyLight.shadow.camera.near = 0.1;
keyLight.shadow.camera.far = 12;
keyLight.shadow.camera.left = -2.5;
keyLight.shadow.camera.right = 2.5;
keyLight.shadow.camera.top = 3.5;
keyLight.shadow.camera.bottom = -0.5;
scene.add(keyLight);

const fillLight = new THREE.DirectionalLight(0x9fc8ff, 1.0);
fillLight.position.set(-2.2, 1.8, -1.8);
scene.add(fillLight);

const rimLight = new THREE.DirectionalLight(0xff9fdc, 0.8);
rimLight.position.set(0.5, 2.0, -2.5);
scene.add(rimLight);

const ground = new THREE.Mesh(
  new THREE.PlaneGeometry(12, 12),
  new THREE.ShadowMaterial({ opacity: 0.22 })
);
ground.rotation.x = -Math.PI / 2;
ground.receiveShadow = true;
scene.add(ground);

const loader = new GLTFLoader();
loader.register((parser) => new VRMLoaderPlugin(parser));

let currentRoot = null;
let currentVrm = null;
let currentMixer = null;
let idleNodes = null;
let idleTime = 0;
let lookTarget = null;
let statFrames = 0;
let statLastTime = performance.now();

function showStatus(text) {
  if (statusEl) statusEl.textContent = text;
  if (overlay) overlay.style.display = 'flex';
}

function hideStatus() {
  if (overlay) overlay.style.display = 'none';
}

function disposeRoot() {
  if (currentMixer) {
    currentMixer.stopAllAction();
    currentMixer = null;
  }
  if (currentRoot) {
    scene.remove(currentRoot);
    VRMUtils.deepDispose(currentRoot);
    currentRoot = null;
  }
  currentVrm = null;
  idleNodes = null;
  idleTime = 0;
}

function prepareRoot(root, isVrm) {
  root.traverse((obj) => {
    obj.frustumCulled = false;
    if (obj.isMesh) {
      obj.castShadow = true;
      obj.receiveShadow = true;
      if (obj.material) {
        const materials = Array.isArray(obj.material) ? obj.material : [obj.material];
        materials.forEach((mat) => {
          if (mat && mat.map) mat.map.colorSpace = THREE.SRGBColorSpace;
        });
      }
    }
  });
  scene.add(root);
  frameRoot(root);
}

function frameRoot(root) {
  const box = new THREE.Box3().setFromObject(root);
  const size = box.getSize(new THREE.Vector3());
  let height = Math.max(size.y, 0.1);

  // Some glTF exports are in centimeters. Normalize obvious outliers so the
  // camera doesn't end up inside the model.
  if (!currentVrm && height > 10.0) {
    const scale = 1.7 / height;
    root.scale.multiplyScalar(scale);
    box.setFromObject(root);
    height = Math.max(box.getSize(new THREE.Vector3()).y, 0.1);
  }

  const center = box.getCenter(new THREE.Vector3());
  root.position.x -= center.x;
  root.position.z -= center.z;
  root.position.y -= box.min.y;

  const targetY = height * 0.55;
  const distance = (height / (2 * Math.tan((camera.fov * Math.PI) / 360))) * 1.35;
  camera.near = Math.max(height / 200, 0.01);
  camera.far = Math.max(height * 40, 100);
  camera.position.set(0, targetY, distance);
  camera.updateProjectionMatrix();

  controls.minDistance = height * 0.25;
  controls.maxDistance = height * 8;
  controls.target.set(0, targetY, 0);
  controls.update();
}

function setupIdle(vrm) {
  idleNodes = {};
  const names = [
    'hips',
    'spine',
    'chest',
    'neck',
    'head',
    'leftUpperArm',
    'rightUpperArm',
    'leftLowerArm',
    'rightLowerArm',
  ];
  for (const name of names) {
    const node = vrm.humanoid?.getNormalizedBoneNode(name);
    if (node) {
      idleNodes[name] = {
        node,
        base: node.rotation.clone(),
      };
    }
  }

  // VRM rest pose is a T-pose. Lower the upper arms into a relaxed A-pose so
  // the avatar looks like a VTuber model rather than a calibration dummy.
  const leftUpper = idleNodes.leftUpperArm;
  const rightUpper = idleNodes.rightUpperArm;
  if (leftUpper) leftUpper.base.z = -1.12;
  if (rightUpper) rightUpper.base.z = 1.12;

  const leftLower = idleNodes.leftLowerArm;
  const rightLower = idleNodes.rightLowerArm;
  if (leftLower) leftLower.base.x = -0.12;
  if (rightLower) rightLower.base.x = -0.12;

  lookTarget = new THREE.Object3D();
  lookTarget.position.set(0, 1.4, 1.0);
  scene.add(lookTarget);
  if (vrm.lookAt) vrm.lookAt.target = lookTarget;
}

function applyIdle(delta) {
  if (!currentVrm || !idleNodes) return;
  idleTime += delta;
  const t = idleTime;

  const sway = Math.sin(t * 0.65);
  const breathe = Math.sin(t * 1.15);
  const hips = idleNodes.hips;
  const spine = idleNodes.spine;
  const chest = idleNodes.chest;
  const neck = idleNodes.neck;
  const head = idleNodes.head;
  const leftUpper = idleNodes.leftUpperArm;
  const rightUpper = idleNodes.rightUpperArm;

  if (hips) hips.node.rotation.y = hips.base.y + Math.sin(t * 0.35) * 0.025;
  if (spine) spine.node.rotation.x = spine.base.x + breathe * 0.012;
  if (chest) chest.node.rotation.z = chest.base.z + sway * 0.012;
  if (neck) neck.node.rotation.y = neck.base.y + Math.sin(t * 0.55) * 0.06;
  if (head) {
    head.node.rotation.y = head.base.y + Math.sin(t * 0.55 + 0.4) * 0.05;
    head.node.rotation.x = head.base.x + Math.sin(t * 0.9) * 0.02;
  }
  if (leftUpper) leftUpper.node.rotation.x = leftUpper.base.x + Math.sin(t * 0.8) * 0.015;
  if (rightUpper) rightUpper.node.rotation.x = rightUpper.base.x - Math.sin(t * 0.8) * 0.015;

  if (lookTarget) {
    lookTarget.position.x = Math.sin(t * 0.45) * 0.18;
    lookTarget.position.y = 1.4 + Math.sin(t * 0.7) * 0.05;
    lookTarget.position.z = 1.0;
  }
}

function onModelLoaded(gltf) {
  disposeRoot();

  const vrm = gltf.userData?.vrm;
  if (vrm) {
    VRMUtils.removeUnnecessaryVertices(gltf.scene);
    VRMUtils.combineSkeletons(gltf.scene);
    VRMUtils.rotateVRM0(vrm);
    currentVrm = vrm;
    currentRoot = vrm.scene;
    prepareRoot(currentRoot, true);
    setupIdle(vrm);
  } else {
    currentRoot = gltf.scene;
    prepareRoot(currentRoot, false);
    if (gltf.animations && gltf.animations.length > 0) {
      currentMixer = new THREE.AnimationMixer(currentRoot);
      currentMixer.clipAction(gltf.animations[0]).play();
    }
  }
  hideStatus();
}

function loadAvatar(url) {
  showStatus('正在加载模型…');
  loader.load(
    url,
    (gltf) => {
      try {
        onModelLoaded(gltf);
      } catch (error) {
        console.error(error);
        showStatus('模型加载失败：' + (error?.message || error));
      }
    },
    (event) => {
      if (event && event.total > 0) {
        const percent = Math.min(99, Math.round((event.loaded / event.total) * 100));
        showStatus(`正在加载模型… ${percent}%`);
      }
    },
    (error) => {
      console.error(error);
      showStatus('模型加载失败：' + (error?.message || error));
    }
  );
}

function resetView() {
  if (currentRoot) frameRoot(currentRoot);
}

function setRenderQuality(value) {
  const ratio = Math.max(0.5, Math.min(3, Number(value) || 1));
  renderer.setPixelRatio(ratio);
  renderer.setSize(window.innerWidth, window.innerHeight);
}

function setShadows(enabled) {
  const on = Boolean(enabled);
  renderer.shadowMap.enabled = on;
  keyLight.castShadow = on;
  scene.traverse((obj) => {
    if (obj.isMesh) {
      obj.castShadow = on;
      obj.receiveShadow = on;
    }
  });
  renderer.shadowMap.needsUpdate = true;
}

window.Avatar3D = {
  load: loadAvatar,
  resetView,
  setRenderQuality,
  setShadows,
  setTargetFps,
  dispose: disposeRoot,
};

window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});

const timer = new THREE.Timer();
let targetFps = 120;
let frameIntervalMs = 1000 / targetFps;
let lastRenderAt = 0;

function setTargetFps(value) {
  const fps = Math.max(30, Math.min(240, Number(value) || 120));
  targetFps = fps;
  frameIntervalMs = 1000 / fps;
  lastRenderAt = 0;
}

function animate(now) {
  requestAnimationFrame(animate);
  if (now - lastRenderAt < frameIntervalMs - 0.5) {
    return;
  }
  lastRenderAt = now;
  timer.update();
  const delta = Math.min(timer.getDelta(), 0.05);
  if (currentVrm) {
    applyIdle(delta);
    currentVrm.update(delta);
  }
  if (currentMixer) currentMixer.update(delta);
  controls.update();
  renderer.render(scene, camera);

  statFrames += 1;
  if (now - statLastTime >= 500) {
    const fps = (statFrames * 1000) / (now - statLastTime);
    if (window.AndroidAvatar3D && window.AndroidAvatar3D.onStats) {
      try {
        window.AndroidAvatar3D.onStats(
          fps,
          renderer.info.render.calls,
          renderer.info.render.triangles,
          renderer.domElement.width,
          renderer.domElement.height,
          renderer.getPixelRatio(),
          renderer.info.memory.textures
        );
      } catch (e) {
        // Bridge may disappear during activity teardown.
      }
    }
    statFrames = 0;
    statLastTime = now;
  }
}

requestAnimationFrame(animate);
