import * as THREE from "./three.module.js";

const scene = new THREE.Scene();
scene.background = new THREE.Color("#243432");
scene.fog = new THREE.Fog("#243432", 21, 56);
const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: "low-power" });
renderer.setPixelRatio(1);
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
document.querySelector("#scene").append(renderer.domElement);
renderer.domElement.addEventListener("webglcontextlost", () => console.error("Contexto WebGL perdido"));

const camera = new THREE.PerspectiveCamera(37, 1, .06, 100);
scene.add(new THREE.HemisphereLight("#e4f3ff", "#68735d", 2.7));
const daylight = new THREE.DirectionalLight("#fff0ce", 3.4);
daylight.position.set(-1, 8, 6);
scene.add(daylight);

function material(color, options = {}) {
    return new THREE.MeshStandardMaterial({ color, roughness: .62, ...options });
}
function box(parent, mat, w, h, d, x, y, z) {
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
    mesh.position.set(x, y, z);
    parent.add(mesh);
    return mesh;
}
function cylinder(parent, mat, radius, depth, x, y, z, axial = false) {
    const mesh = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius, depth, 24), mat);
    mesh.position.set(x, y, z);
    if (axial) mesh.rotation.x = Math.PI / 2;
    parent.add(mesh);
    return mesh;
}
function sphere(parent, mat, radius, x, y, z) {
    const mesh = new THREE.Mesh(new THREE.SphereGeometry(radius, 18, 14), mat);
    mesh.position.set(x, y, z);
    parent.add(mesh);
    return mesh;
}
function group(parent, x = 0, y = 0, z = 0) {
    const obj = new THREE.Group();
    obj.position.set(x, y, z);
    parent.add(obj);
    return obj;
}

const lime = material("#c9e750", { metalness: .27 });
const dark = material("#182021");
const cream = material("#ecf2dd");
const yellow = material("#f0cf4c");
const alloy = material("#a6b8b7", { metalness: .65 });
const rubber = material("#101514");
const teal = material("#36675e");
const doubleGlass = material("#285a64", { opacity: .44, transparent: true, depthWrite: false, metalness: .2 });
const windscreen = material("#365965", { opacity: .73, transparent: true, depthWrite: false });
const light = material("#fff0bc", { emissive: "#f5dc9f", emissiveIntensity: 1.15 });
const red = material("#e74642", { emissive: "#b72e2a", emissiveIntensity: .55 });

box(scene, material("#364542"), 110, .08, 56, 0, -.07, 0);
box(scene, material("#252e30"), 100, .025, 5.9, 0, -.02, -.65);
box(scene, material("#56645f"), 100, .14, 3.4, 0, .035, 4.0);
box(scene, material("#8f9980"), 100, .14, .10, 0, .035, 2.27);
const roadMarks = group(scene);
for (let x = -38; x < 38; x += 3) box(roadMarks, cream, 1.35, .013, .09, x, .005, -2.85);
for (let x = -28; x < 28; x += 4) {
    box(scene, material("#42554b"), .08, .012, 3.1, x, .113, 4.0);
}

const bus = group(scene);
const length = 8.7;
box(bus, dark, length, .22, 2.26, 0, .53, 0);
box(bus, teal, length - .18, .12, 2.26, 0, .68, 0);
box(bus, lime, length, .69, .11, 0, .99, -1.12);
box(bus, lime, 5.65, .68, .11, -1.53, .99, 1.12);
box(bus, lime, 1.18, .68, .11, 3.76, .99, 1.12);
const roof = group(bus);
box(roof, cream, length + .04, .20, 2.28, 0, 2.67, 0);
box(roof, lime, 2.05, .17, .8, -1.2, 2.85, -.1);
box(roof, alloy, 1.8, .06, .62, -1.2, 2.96, -.1);
box(bus, dark, length, .20, .1, 0, 2.48, -1.1);
box(bus, dark, length, .20, .1, 0, 2.48, 1.1);
box(bus, dark, .15, 1.90, 2.22, -4.32, 1.56, 0);
box(bus, lime, .16, .82, 2.23, 4.31, 1.03, 0);
box(bus, dark, .17, .18, 2.27, 4.35, .48, 0);
box(bus, windscreen, .10, .94, 2.04, 4.31, 1.91, 0);
box(bus, dark, .14, .30, 2.24, 4.32, 2.48, 0);
box(bus, doubleGlass, .035, .72, 1.79, -4.425, 1.96, 0);
box(bus, lime, .05, .43, 2.23, -4.43, .87, 0);
for (const side of [-1, 1]) {
    box(bus, light, .08, .14, .33, 4.43, .95, side * .86);
    box(bus, red, .055, .21, .18, -4.47, 1.22, side * .92);
    box(bus, dark, .33, .055, .23, 3.97, 2.21, side * 1.23);
    box(bus, alloy, .19, .21, .13, 4.13, 2.18, side * 1.40);
}
const nearGlass = group(bus);
for (const side of [-1, 1]) {
    const parent = side === 1 ? nearGlass : bus;
    const windows = side === 1 ? [-3.57, -2.05, -.53, .99, 3.81] : [-3.57, -2.05, -.53, .99, 2.51, 3.82];
    for (const x of windows) {
        const width = x > 3.5 ? .87 : 1.43;
        box(parent, dark, .065, 1.11, .09, x - width / 2, 1.89, side * 1.11);
        box(parent, doubleGlass, width, .99, .035, x, 1.91, side * 1.121);
        box(parent, yellow, width, .04, .055, x, 1.38, side * 1.122);
    }
}
const doorLeft = group(bus, 1.36, 0, 1.142);
const doorRight = group(bus, 2.09, 0, 1.142);
for (const door of [doorLeft, doorRight]) {
    box(door, dark, .73, .26, .085, .35, .84, 0);
    box(door, dark, .045, 1.63, .098, .025, 1.55, 0);
    box(door, dark, .045, 1.63, .098, .69, 1.55, 0);
    box(door, doubleGlass, .65, 1.32, .07, .35, 1.66, 0);
    box(door, yellow, .64, .042, .094, .35, 1.43, 0);
}
const ramp = group(bus, 2.08, .73, 1.135);
box(ramp, alloy, 1.42, .07, 1.43, 0, -.04, .70);
for (const x of [-.69, .69]) box(ramp, yellow, .055, .065, 1.45, x, .025, .70);
for (let z = .10; z < 1.40; z += .13) box(ramp, dark, 1.26, .008, .011, 0, .001, z);

const wheels = [];
for (const x of [-2.8, 2.91]) for (const z of [-1.10, 1.10]) {
    const pivot = group(bus, x, .47, z);
    cylinder(pivot, rubber, .47, .31, 0, 0, 0, true);
    const face = Math.sign(z) * .169;
    cylinder(pivot, alloy, .279, .039, 0, 0, face, true);
    cylinder(pivot, dark, .113, .054, 0, 0, face, true);
    for (let i = 0; i < 6; i++) {
        const angle = i * Math.PI / 3;
        cylinder(pivot, dark, .031, .015, Math.sin(angle) * .185, Math.cos(angle) * .185, face * 1.15, true);
    }
    wheels.push(pivot);
}

function seat(x, z, priority = false) {
    const color = priority ? yellow : teal;
    box(bus, alloy, .47, .28, .53, x, .86, z);
    box(bus, color, .56, .14, .59, x, 1.06, z);
    box(bus, color, .135, .51, .58, x - .26, 1.32, z);
}
for (const x of [-3.4, -2.38, -1.35, -.33, .69, 1.72]) seat(x, -.72, x > 0);
for (const x of [-3.4, -2.38, -1.35]) seat(x, .72);
seat(3.5, -.55, true);
box(bus, dark, .34, .22, .66, 3.98, 1.36, -.55);
box(bus, yellow, .058, .035, .058, .83, .76, .85);
box(bus, yellow, .058, .035, .058, -.39, .76, .85);
for (const x of [-3.57, -.48, 1.36, 2.84]) cylinder(bus, yellow, .035, 1.77, x, 1.62, .58);
box(bus, yellow, 5.65, .048, .048, -.82, 2.47, .58);

function signTexture(label, bg, fg, size) {
    const canvas = document.createElement("canvas");
    canvas.width = 512;
    canvas.height = 96;
    const pen = canvas.getContext("2d");
    pen.fillStyle = bg;
    pen.fillRect(0, 0, 512, 96);
    pen.font = `800 ${size}px sans-serif`;
    pen.fillStyle = fg;
    pen.textAlign = "center";
    pen.textBaseline = "middle";
    pen.fillText(label, 256, 49);
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    return new THREE.MeshBasicMaterial({ map: texture });
}
box(bus, signTexture("HUÁNUCO / AMBO", "#121918", "#e8fb73", 44), .015, .21, 1.43, 4.40, 2.48, 0);
box(bus, signTexture("V O Z R U T A", "#c9e750", "#192321", 60), 2.0, .31, .014, -1.68, 1.03, 1.186);
box(bus, signTexture("♿", "#35665b", "#f1f5e9", 78), .42, .36, .012, .83, 1.04, 1.182);

const skin = material("#ba7b52");
const navy = material("#385c7e");
const vest = material("#f8bc6e");
function person(clothes, wheelchair) {
    const actor = group(scene);
    if (wheelchair) {
        box(actor, dark, .43, .06, .45, 0, .43, 0);
        box(actor, navy, .43, .42, .06, 0, .57, -.21);
        for (const x of [-.26, .26]) {
            const wheel = cylinder(actor, rubber, .247, .075, x, .247, -.06);
            wheel.rotation.z = Math.PI / 2;
            const rim = cylinder(actor, alloy, .182, .081, x, .247, -.06);
            rim.rotation.z = Math.PI / 2;
            cylinder(actor, dark, .10, .07, x * .83, .105, .28).rotation.z = Math.PI / 2;
            box(actor, dark, .12, .20, .15, x * .54, .32, .21);
        }
    } else {
        for (const x of [-.10, .10]) box(actor, dark, .14, .54, .17, x, .28, 0);
    }
    const hips = wheelchair ? .44 : .54;
    box(actor, clothes, .35, .44, .25, 0, hips + .22, 0);
    sphere(actor, skin, .144, 0, hips + .58, 0);
    for (const x of [-.214, .214]) cylinder(actor, clothes, .056, .32, x, hips + .235, .02);
    return actor;
}
const pedestrian = person(navy, false);
const rider = person(navy, true);
box(pedestrian, cream, .022, .62, .022, -.27, .31, .26).rotation.x = .33;
const worker = person(vest, false);
const stop = group(scene, 1.2, .12, 3.52);
cylinder(stop, alloy, .055, 1.93, 0, .96, 0);
box(stop, signTexture("PARADERO", "#1a302d", "#f3f8df", 69), .71, .32, .09, 0, 1.76, 0);

const cameras = [
    [new THREE.Vector3(7.5, 4.2, 10.8), new THREE.Vector3(0, 1.16, 0)],
    [new THREE.Vector3(6.6, 3.1, 6.7), new THREE.Vector3(2.12, 1.05, 1.0)],
    [new THREE.Vector3(2.5, 5.3, 6.9), new THREE.Vector3(.53, .90, 0)],
];
let frame = { stage: 0, fraction: 0, view: 0, profile: 0 };

function draw() {
    const { stage, fraction, view, profile } = frame;
    const open = stage === 2 ? THREE.MathUtils.smoothstep(fraction, 0, .45) :
        stage === 3 ? (1 - THREE.MathUtils.smoothstep(fraction, .80, 1)) : stage === 5 ? .75 : 0;
    ramp.scale.z = THREE.MathUtils.lerp(.03, 1, open);
    ramp.rotation.x = .43 * open;
    doorLeft.position.x = 1.36 - .70 * open;
    doorRight.position.x = 2.09 + .70 * open;
    roof.visible = view !== 2;
    nearGlass.visible = view !== 2;
    const movement = stage === 0 ? fraction * 4 : stage === 1 ? 4 + fraction * 1.6 : stage === 4 ? fraction * 38 : 0;
    roadMarks.position.x = -(movement % 3);
    wheels.forEach(wheel => { wheel.rotation.z = -movement * 2.2; });
    const board = stage === 3 ? THREE.MathUtils.smoothstep(fraction, .02, .82) : stage > 3 ? 1 : 0;
    const along = THREE.MathUtils.lerp(3.15, .57, board);
    const y = along > 2.40 ? .12 : along < 1.13 ? .73 : .12 + (2.40 - along) * .48;
    const boardingScene = stage >= 1 && stage <= 3;
    pedestrian.visible = profile !== 2 && (boardingScene || (view === 2 && stage > 3));
    rider.visible = profile === 2 && (boardingScene || (view === 2 && stage > 3));
    for (const actor of [pedestrian, rider]) {
        actor.position.set(2.10 - Math.max(0, board - .75) * 3.7, y, along);
        actor.rotation.y = Math.PI;
    }
    worker.position.set(2.62 - board * .44, y, along + .31);
    worker.rotation.y = Math.PI;
    worker.visible = stage === 2 || stage === 3 || view === 2;
    stop.visible = stage > 0 && stage < 4;
    camera.position.copy(cameras[view][0]);
    camera.lookAt(cameras[view][1]);
    renderer.render(scene, camera);
}
function resize() {
    const width = Math.max(1, window.innerWidth);
    const height = Math.max(1, window.innerHeight);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    renderer.setSize(width, height);
    draw();
}
window.renderBusFrame = (stage, fraction, view, profile, width, height) => {
    frame = { stage, fraction, view, profile };
    if (renderer.domElement.width !== width || renderer.domElement.height !== height) {
        renderer.setSize(width, height, false);
        camera.aspect = width / height;
        camera.updateProjectionMatrix();
    }
    draw();
    return renderer.domElement.toDataURL("image/jpeg", .82);
};
window.addEventListener("resize", resize);
resize();
