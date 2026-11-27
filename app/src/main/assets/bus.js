import * as THREE from "./three.module.js";

// Vehicle, accessible stop, and articulated figures adapted from the TransInclusivo HTML prototype.
const lerp = (a, b, t) => a + (b - a) * t;
const S = { d: 0, v: 0, doors: 0, ramp: 0, secure: 0, braking: false, cam: "ext" };
  const canvas = document.createElement("canvas");
  document.querySelector("#scene").append(canvas);
  const renderer=new THREE.WebGLRenderer({canvas,antialias:true,powerPreference:'high-performance'});
  renderer.setPixelRatio(1);
  renderer.shadowMap.enabled=false;renderer.shadowMap.type=THREE.PCFSoftShadowMap;
  renderer.outputColorSpace=THREE.SRGBColorSpace;
  renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.05;
  const scene=new THREE.Scene();
  const FOG=0xe9dcc4;
  scene.fog=new THREE.Fog(FOG,70,300);
  const camera=new THREE.PerspectiveCamera(48,16/9,.1,700);

  /* utilidades */
  const M=(c,o)=>new THREE.MeshStandardMaterial(Object.assign({color:c,roughness:.55,metalness:.08},o||{}));
  const B=(w,h,d)=>new THREE.BoxGeometry(w,h,d);
  function add(geo,mat,x,y,z,parent,shadow=true,recv=true){const m=new THREE.Mesh(geo,mat);m.position.set(x,y,z);m.castShadow=shadow;m.receiveShadow=recv;parent.add(m);return m;}
  function rodGeo(r){return new THREE.CylinderGeometry(r,r,1,10);}
  function placeRod(mesh,p1,p2){
    const dir=new THREE.Vector3().subVectors(p2,p1);const len=dir.length()||1e-4;
    mesh.position.copy(p1).addScaledVector(dir,.5);mesh.scale.set(1,len,1);
    mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0,1,0),dir.normalize());
  }
  function rod(parent,p1,p2,r,mat){const m=new THREE.Mesh(rodGeo(r),mat);m.castShadow=true;parent.add(m);placeRod(m,p1,p2);return m;}
  const V=(x,y,z)=>new THREE.Vector3(x,y,z);
  function canvasTex(w,h,draw,srgb=true){
    const c=document.createElement('canvas');c.width=w;c.height=h;draw(c.getContext('2d'),w,h);
    const t=new THREE.CanvasTexture(c);if(srgb)t.colorSpace=THREE.SRGBColorSpace;t.anisotropy=4;return t;
  }
  function rng(seed){let a=seed;return()=>{a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296;};}
  const rnd=rng(20241122);
  function isaIcon(ctx,cx,cy,s,fg){
    ctx.save();ctx.strokeStyle=fg;ctx.fillStyle=fg;ctx.lineWidth=s*.11;ctx.lineCap='round';ctx.lineJoin='round';
    ctx.beginPath();ctx.arc(cx-s*.08,cy-s*.36,s*.11,0,Math.PI*2);ctx.fill();
    ctx.beginPath();ctx.moveTo(cx-s*.08,cy-s*.2);ctx.lineTo(cx-s*.08,cy+s*.1);ctx.lineTo(cx+s*.22,cy+s*.1);ctx.lineTo(cx+s*.34,cy+s*.38);ctx.stroke();
    ctx.beginPath();ctx.moveTo(cx-s*.08,cy-s*.08);ctx.lineTo(cx+s*.2,cy-s*.08);ctx.stroke();
    ctx.beginPath();ctx.arc(cx-s*.04,cy+s*.18,s*.27,Math.PI*.15,Math.PI*1.15,false);ctx.stroke();
    ctx.restore();
  }
  function labelTex(w,h,lines,bg,fg,opt={}){
    return canvasTex(w,h,(ctx)=>{
      ctx.fillStyle=bg;ctx.fillRect(0,0,w,h);
      if(opt.border){ctx.strokeStyle=opt.border;ctx.lineWidth=opt.bw||8;ctx.strokeRect(opt.bw/2||4,opt.bw/2||4,w-(opt.bw||8),h-(opt.bw||8));}
      let x0=opt.icon?h:0;
      if(opt.icon) isaIcon(ctx,h*.5,h*.52,h*.78,fg);
      ctx.fillStyle=fg;ctx.textAlign='center';ctx.textBaseline='middle';
      const n=lines.length;
      lines.forEach((l,i)=>{ctx.font=`700 ${(opt.fs||h/(n+1.1))|0}px 'Atkinson Hyperlegible',Arial,sans-serif`;ctx.fillText(l,x0+(w-x0)/2,h*(i+1)/(n+1));});
    });
  }
  function noiseTex(base,amp,size,repX,repY,tile){
    const t=canvasTex(size,size,(ctx,w,h)=>{
      ctx.fillStyle=base;ctx.fillRect(0,0,w,h);
      for(let i=0;i<w*h/6;i++){const v=(Math.random()-.5)*amp;ctx.fillStyle=`rgba(${v>0?255:0},${v>0?255:0},${v>0?255:0},${Math.abs(v)/255})`;ctx.fillRect(Math.random()*w,Math.random()*h,2,2);}
      if(tile){ctx.strokeStyle='rgba(0,0,0,.28)';ctx.lineWidth=3;ctx.strokeRect(0,0,w,h);}
    });
    t.wrapS=t.wrapT=THREE.RepeatWrapping;t.repeat.set(repX,repY);return t;
  }
  function person(o){
    const root=new THREE.Group();const hip=new THREE.Group();hip.position.y=.9;root.add(hip);
    const mt=c=>M(c,{roughness:.75,metalness:0});
    add(B(.42,.55,.24),mt(o.shirt),0,.3,0,hip);
    if(o.vest){
      add(B(.44,.42,.26),M(o.vest,{roughness:.5,emissive:o.vest,emissiveIntensity:.28}),0,.32,0,hip);
      add(B(.45,.04,.27),M(0xe8e8e8,{metalness:.6,roughness:.3}),0,.24,0,hip);add(B(.45,.04,.27),M(0xe8e8e8,{metalness:.6,roughness:.3}),0,.4,0,hip);
    }
    const head=add(new THREE.SphereGeometry(.125,18,14),mt(o.skin),0,.74,0,hip);
    const hairM=new THREE.Mesh(new THREE.SphereGeometry(.132,18,14,0,Math.PI*2,0,Math.PI*.55),mt(o.hair));hairM.position.set(0,.745,-.005);hairM.castShadow=true;hip.add(hairM);
    if(o.cap){const cap=add(B(.27,.07,.3),mt(o.cap),0,.84,.02,hip);}
    if(o.glasses) add(B(.22,.05,.03),M(0x050505,{roughness:.2}),0,.76,.12,hip);
    const arm=x=>{const g=new THREE.Group();g.position.set(x,.52,0);hip.add(g);add(B(.1,.5,.1),mt(o.shirt),0,-.25,0,g);add(new THREE.SphereGeometry(.055,8,8),mt(o.skin),0,-.52,0,g);return g;};
    const armL=arm(-.27),armR=arm(.27);
    const leg=x=>{const th=new THREE.Group();th.position.set(x,0,0);hip.add(th);add(B(.15,.46,.15),mt(o.pants),0,-.23,0,th);const kn=new THREE.Group();kn.position.set(0,-.46,0);th.add(kn);add(B(.13,.45,.13),mt(o.pants),0,-.225,0,kn);add(B(.14,.07,.24),mt(0x111111),0,-.46,.05,kn);return [th,kn];};
    const [legL,kneeL]=leg(-.11),[legR,kneeR]=leg(.11);
    root.userData={hip,head,armL,armR,legL,legR,kneeL,kneeR,pose:'stand'};
    return root;
  }
  function pose(p,name){
    const u=p.userData;u.pose=name;
    const set=(hy,th,kn,af)=>{u.hip.position.y=hy;u.legL.rotation.x=u.legR.rotation.x=th;u.kneeL.rotation.x=u.kneeR.rotation.x=kn;u.armL.rotation.x=u.armR.rotation.x=af;u.hip.rotation.x=0;};
    if(name==='stand') set(.9,0,0,0);
    else if(name==='sit') set(.46,-Math.PI/2,Math.PI/2,-.25);
    else if(name==='chair') set(.5,-Math.PI/2,Math.PI/2,-.55);
    else if(name==='drive') set(.46,-Math.PI/2,Math.PI/2,-1.15);
    else if(name==='crouch') set(.62,-1.15,1.45,-.8);
  }

  /* ---------- luces ---------- */
  scene.add(new THREE.HemisphereLight(0xd6e8ff,0x7a6a55,.75));
  const sun=new THREE.DirectionalLight(0xfff0d2,1.9);
  sun.position.set(-22,34,26);sun.castShadow=true;
  sun.shadow.mapSize.set(2048,2048);
  Object.assign(sun.shadow.camera,{left:-22,right:22,top:22,bottom:-22,near:5,far:120});
  sun.shadow.camera.updateProjectionMatrix();sun.shadow.bias=-.0006;sun.target.position.set(0,0,0);
  scene.add(sun,sun.target);

  /* cielo */
  const sky=new THREE.Mesh(new THREE.SphereGeometry(420,24,16),new THREE.MeshBasicMaterial({vertexColors:true,side:THREE.BackSide,fog:false}));
  {const pos=sky.geometry.attributes.position,col=[];const top=new THREE.Color(0x5f9fe0),hor=new THREE.Color(FOG),low=new THREE.Color(FOG);
   for(let i=0;i<pos.count;i++){const y=pos.getY(i)/420;const c=y>0?hor.clone().lerp(top,Math.pow(y,.55)):low;col.push(c.r,c.g,c.b);}
   sky.geometry.setAttribute('color',new THREE.Float32BufferAttribute(col,3));}
  scene.add(sky);

  /* ---------- mundo ---------- */
  const world=new THREE.Group();scene.add(world);
  const asphaltT=noiseTex('#3a3d42',60,256,70,1.2,false);
  const asphalt=new THREE.Mesh(new THREE.PlaneGeometry(420,7.2),new THREE.MeshStandardMaterial({map:asphaltT,roughness:.95,metalness:0}));
  asphalt.rotation.x=-Math.PI/2;asphalt.position.set(0,0,-1.95);asphalt.receiveShadow=true;world.add(asphalt);
  const walkT=noiseTex('#b9b6ac',40,128,210,4,true);
  const sidewalkBox=add(B(420,.15,6.4),M(0x9a978f,{roughness:.9}),0,.075,4.8,world,false,true);
  const sidewalkTop=new THREE.Mesh(new THREE.PlaneGeometry(420,6.4),new THREE.MeshStandardMaterial({map:walkT,roughness:.9}));
  sidewalkTop.rotation.x=-Math.PI/2;sidewalkTop.position.set(0,.152,4.8);sidewalkTop.receiveShadow=true;world.add(sidewalkTop);
  add(B(420,.15,2.6),M(0x9a978f,{roughness:.9}),0,.075,-6.8,world,false,true);
  const farWalk=new THREE.Mesh(new THREE.PlaneGeometry(420,2.6),new THREE.MeshStandardMaterial({map:walkT,roughness:.9}));
  farWalk.rotation.x=-Math.PI/2;farWalk.position.set(0,.152,-6.8);world.add(farWalk);
  add(B(420,.2,.18),M(0xdcdcd6,{roughness:.8}),0,.1,1.62,world,false,true);       /* sardinel */
  const ground=new THREE.Mesh(new THREE.PlaneGeometry(900,900),new THREE.MeshStandardMaterial({color:0x6f7f4b,roughness:1}));
  ground.rotation.x=-Math.PI/2;ground.position.y=-.05;ground.receiveShadow=true;scene.add(ground);

  /* montañas */
  [[-1,0x4b6b45],[1,0x56744a]].forEach(([sgn,col])=>{
    for(let i=0;i<9;i++){
      const g=new THREE.IcosahedronGeometry(1,1);const m=new THREE.Mesh(g,new THREE.MeshStandardMaterial({color:col,roughness:1,flatShading:true}));
      const s=40+rnd()*50;m.scale.set(s*1.6,s*(.6+rnd()*.7),s);m.position.set(-300+i*80+rnd()*30,0,sgn*(150+rnd()*60));m.rotation.y=rnd()*3;scene.add(m);
    }
  });

  /* anillos de objetos repetidos */
  const RING=[];const WRING=308;
  const ringX = (base, off, width) => ((base - off + width / 2) % width + width) % width - width / 2;
  function facadeMat(cols,floors,base,seed){
    const cw=64,ch=64;const t=canvasTex(cols*cw,floors*ch+40,(ctx,w,h)=>{
      ctx.fillStyle=base;ctx.fillRect(0,0,w,h);
      for(let r=0;r<floors;r++)for(let c=0;c<cols;c++){
        const x=c*cw+14,y=r*ch+10;
        ctx.fillStyle='#233347';ctx.fillRect(x,y,cw-28,ch-24);
        ctx.fillStyle='rgba(180,215,240,.35)';ctx.fillRect(x+2,y+2,(cw-32)/2,ch-28);
        ctx.strokeStyle='#f2efe6';ctx.lineWidth=3;ctx.strokeRect(x,y,cw-28,ch-24);
        if(r%2===1){ctx.fillStyle='#4b4036';ctx.fillRect(x-4,y+ch-26,cw-20,5);}
      }
      ctx.fillStyle='rgba(0,0,0,.18)';ctx.fillRect(0,h-40,w,40);
      ctx.fillStyle='#d9d2c0';ctx.fillRect(0,0,w,6);
    });
    return new THREE.MeshStandardMaterial({map:t,roughness:.85});
  }
  const BASES=['#e8c9a0','#f0d890','#a9cbe0','#e1a9a0','#bcd9a8','#f1ead8','#d7b48a'];
  const FACS=[[4,3],[3,2],[5,4],[4,5],[3,3],[5,3]].map((cf,i)=>facadeMat(cf[0],cf[1],BASES[i%BASES.length],i));
  const FACDIM=[[4,3],[3,2],[5,4],[4,5],[3,3],[5,3]];
  const roofM=M(0x8a4b3a,{roughness:.9});
  let bx=0;
  for(let i=0;i<22;i++){
    [1,-1].forEach(side=>{
      const v=Math.floor(rnd()*FACS.length);const [c,f]=FACDIM[v];const w=c*3.2,h=f*3.3+.6,dpt=12;
      const m=new THREE.Mesh(B(w,h,dpt),FACS[v]);m.castShadow=false;m.receiveShadow=true;
      const z=side>0?8.6+dpt/2:-8.4-dpt/2;m.position.set(0,h/2,z);
      const rf=new THREE.Mesh(B(w+.5,.5,dpt+.5),roofM);rf.position.set(0,h/2+.25,0);m.add(rf);
      world.add(m);RING.push({o:m,base:i*14.0+(side>0?0:5)+rnd()*3,W:WRING,kind:'bld'});
    });
  }
  /* árboles y faroles */
  function makeTree(){
    const g=new THREE.Group();add(new THREE.CylinderGeometry(.1,.14,2.2,8),M(0x6b4a2f),0,1.1,0,g);
    const lm=M(0x2f7a3a,{roughness:.9});
    [[0,2.9,0,1.1],[.5,2.5,.3,.8],[-.5,2.6,-.2,.85]].forEach(([x,y,z,r])=>add(new THREE.IcosahedronGeometry(r,1),lm,x,y,z,g));
    return g;
  }
  function makeLamp(){
    const g=new THREE.Group();const pm=M(0x2b2f36,{metalness:.6,roughness:.4});
    add(new THREE.CylinderGeometry(.06,.08,5,8),pm,0,2.5,0,g);add(B(.1,.1,1.2),pm,0,5,-.55,g);
    add(B(.45,.12,.25),M(0xfff4cc,{emissive:0xffe9a8,emissiveIntensity:1.1}),0,4.93,-1.1,g,false);
    return g;
  }
  for(let i=0;i<20;i++){const t=makeTree();t.position.set(0,.15,7.9);world.add(t);RING.push({o:t,base:i*16+4,W:WRING,kind:'tree'});}
  for(let i=0;i<20;i++){const l=makeLamp();l.position.set(0,.15,7.7);l.rotation.y=0;world.add(l);RING.push({o:l,base:i*16+12,W:WRING,kind:'lamp'});}
  /* marcas viales */
  const dashM=new THREE.MeshStandardMaterial({color:0xffd34d,roughness:.8});
  for(let i=0;i<50;i++){const m=new THREE.Mesh(B(2.4,.01,.14),dashM);m.position.set(0,.012,-2.0);world.add(m);RING.push({o:m,base:i*6.2,W:310,kind:'dash'});}
  add(B(420,.01,.1),new THREE.MeshStandardMaterial({color:0xffffff}),0,.012,1.45,world,false,false);
  /* cruces peatonales cebra se omiten; autos y mototaxis */
  function makeCar(col,moto){
    const g=new THREE.Group();
    if(moto){
      add(B(1.9,.7,1.2),M(col),0,.75,0,g);add(B(1.2,.7,1.2),M(0x222222),-.3,1.45,0,g);add(B(1.3,.1,1.3),M(0x333333),-.3,1.85,0,g);
      [[.7,.3,.5],[.7,.3,-.5],[-.7,.3,0]].forEach(([x,y,z])=>add(new THREE.CylinderGeometry(.3,.3,.18,12).rotateX(Math.PI/2),M(0x111111),x,y,z,g));
    }else{
      add(B(4.2,.8,1.8),M(col,{metalness:.35,roughness:.35}),0,.7,0,g);add(B(2.2,.7,1.6),M(0x9fc4de,{transparent:true,opacity:.7,roughness:.1}),-.2,1.4,0,g);
      [[1.3,.35,.9],[1.3,.35,-.9],[-1.3,.35,.9],[-1.3,.35,-.9]].forEach(([x,y,z])=>add(new THREE.CylinderGeometry(.35,.35,.22,14).rotateX(Math.PI/2),M(0x111111),x,y,z,g));
    }
    return g;
  }
  const CARS=[];
  [[0xc0392b,0,-4.2,-11,0],[0x2e86c1,0,-4.4,-14,70],[0xf1c40f,1,-3.6,-9,140],[0xecf0f1,0,-4.0,-12,210],[0x27ae60,1,-4.1,-10,260]].forEach(([c,mo,z,sp,ph])=>{
    const car=makeCar(c,mo);car.position.set(0,0,z);car.rotation.y=Math.PI;world.add(car);CARS.push({o:car,sp,ph});
  });

  /* ---------- paraderos ---------- */
  const tactileT=canvasTex(64,64,(ctx,w,h)=>{ctx.fillStyle='#ffd400';ctx.fillRect(0,0,w,h);ctx.fillStyle='#b38f00';for(let y=8;y<h;y+=16)for(let x=8;x<w;x+=16){ctx.beginPath();ctx.arc(x,y,4.5,0,7);ctx.fill();}});
  tactileT.wrapS=tactileT.wrapT=THREE.RepeatWrapping;
  const bars=canvasTex(64,64,(ctx,w,h)=>{ctx.fillStyle='#ffd400';ctx.fillRect(0,0,w,h);ctx.fillStyle='#b38f00';for(let x=6;x<w;x+=14)ctx.fillRect(x,0,6,h);});
  bars.wrapS=bars.wrapT=THREE.RepeatWrapping;
  function buildStop(name){
    const g=new THREE.Group();
    const t1=tactileT.clone();t1.needsUpdate=true;t1.repeat.set(30,2);
    const strip=new THREE.Mesh(new THREE.PlaneGeometry(12,.6),new THREE.MeshStandardMaterial({map:t1,roughness:.7}));strip.rotation.x=-Math.PI/2;strip.position.set(2.2,.158,1.95);strip.receiveShadow=true;g;g.add(strip);
    const b1=bars.clone();b1.needsUpdate=true;b1.repeat.set(1,10);
    const guide=new THREE.Mesh(new THREE.PlaneGeometry(.42,4.2),new THREE.MeshStandardMaterial({map:b1,roughness:.7}));guide.rotation.x=-Math.PI/2;guide.position.set(2.1,.158,4.4);g.add(guide);
    const post=M(0x30343c,{metalness:.6,roughness:.4});
    [[0,5.0],[6.0,5.0],[0,6.7],[6.0,6.7]].forEach(([x,z])=>add(new THREE.CylinderGeometry(.06,.06,2.6,8),post,x,1.45,z,g));
    add(B(6.4,.12,2.1),M(0x0a4fa8,{roughness:.4}),3,2.8,5.85,g);
    add(B(6.4,.04,2.1),M(0xffd400),3,2.72,5.85,g,false);
    add(B(6.0,1.4,.05),new THREE.MeshStandardMaterial({color:0xa8d6ea,transparent:true,opacity:.28,roughness:.05,metalness:.4,depthWrite:false}),3,1.8,6.7,g,false);
    add(B(2.4,.08,.5),M(0x7a5a3a),4.1,.62,6.35,g);add(B(2.4,.5,.06),M(0x7a5a3a),4.1,.9,6.62,g);
    [3.1,5.1].forEach(x=>add(B(.08,.45,.45),post,x,.4,6.35,g));
    /* letrero principal */
    const sg=new THREE.Mesh(new THREE.PlaneGeometry(2.6,.55),new THREE.MeshBasicMaterial({map:labelTex(520,110,[name.toUpperCase()],'#0a4fa8','#ffffff',{border:'#ffd400',bw:8,fs:34})}));
    sg.position.set(3,2.45,4.78);g.add(sg);
    /* banderola ISA + poste */
    add(new THREE.CylinderGeometry(.05,.05,3.1,8),post,6.9,1.55,2.4,g);
    const pl=new THREE.Mesh(new THREE.PlaneGeometry(.9,.9),new THREE.MeshBasicMaterial({map:labelTex(180,180,[],'#0a4fa8','#ffffff',{border:'#ffffff',bw:10,icon:false}),side:THREE.DoubleSide}));
    pl.position.set(6.9,2.8,2.4);g.add(pl);
    const ic=new THREE.Mesh(new THREE.PlaneGeometry(.9,.9),new THREE.MeshBasicMaterial({map:canvasTex(180,180,(ctx)=>{ctx.fillStyle='#0a4fa8';ctx.fillRect(0,0,180,180);ctx.strokeStyle='#fff';ctx.lineWidth=10;ctx.strokeRect(5,5,170,170);isaIcon(ctx,90,92,130,'#fff');})}));
    ic.position.set(6.9,2.8,2.42);g.add(ic);
    const ic2=ic.clone();ic2.position.set(6.9,2.8,2.38);ic2.rotation.y=Math.PI;g.add(ic2);
    pl.visible=false;
    /* placa braille */
    const bp=new THREE.Mesh(new THREE.PlaneGeometry(.5,.3),new THREE.MeshBasicMaterial({map:brailleTex(['PARADERO','ACCESIBLE'])}));
    bp.position.set(6.9,1.25,2.46);g.add(bp);
    add(B(.04,.04,.04),M(0xffffff),6.9,1.5,2.46,g);
    /* luz ambiente del paradero */
    add(B(5.6,.05,.4),M(0xfff4cc,{emissive:0xffe9a8,emissiveIntensity:.9}),3,2.68,5.5,g,false);
    g.traverse(o=>{if(o.isMesh&&o.geometry.type==='BoxGeometry')o.receiveShadow=true;});
    return g;
  }
  const BR={P:[1,2,3,4],U:[1,3,6],E:[1,5],R:[1,2,3,5],T:[2,3,4,5],A:[1],D:[1,4,5],O:[1,3,5],C:[1,4],S:[2,3,4],I:[2,4],B:[1,2],L:[1,2,3],' ':[]};
  function brailleTex(lines){
    return canvasTex(300,180,(ctx,w,h)=>{
      ctx.fillStyle='#0a2a5a';ctx.fillRect(0,0,w,h);ctx.strokeStyle='#ffd400';ctx.lineWidth=8;ctx.strokeRect(4,4,w-8,h-8);
      lines.forEach((ln,li)=>{
        const y0=18+li*80;ctx.fillStyle='#fff';ctx.font='700 22px Arial';ctx.textAlign='center';ctx.fillText(ln,w/2,y0+14);
        const chars=ln.split('');const step=Math.min(34,(w-30)/chars.length);const x0=w/2-(chars.length-1)*step/2;
        chars.forEach((ch,ci)=>{(BR[ch]||[]).forEach(n=>{const col=n<=3?0:1,row=(n-1)%3;ctx.beginPath();ctx.fillStyle='#ffd400';ctx.arc(x0+ci*step+col*8-4,y0+26+row*9,3.2,0,7);ctx.fill();});});
      });
    });
  }
  const STOPS=[];
  function buildStops(){
    const station = buildStop('Paradero inclusivo');
    world.add(station);
    STOPS.push({g: station, d: 0});
  }

  /* ---------- AUTOBÚS ---------- */
  const bus=new THREE.Group();scene.add(bus);
  const mWhite=M(0xf4f6f8,{roughness:.3,metalness:.2}),mBlue=M(0x0a4fa8,{roughness:.35,metalness:.25}),mYel=M(0xffd400,{roughness:.45}),mDark=M(0x1a1c20,{roughness:.8}),mFloor=M(0x3b3f46,{roughness:.9}),mChrome=M(0xcfd6dd,{roughness:.2,metalness:.9});
  const mTail=M(0xff2a2a,{emissive:0xff0000,emissiveIntensity:.6}),mHead=M(0xffffee,{emissive:0xffffcc,emissiveIntensity:1.4});
  const mGlass=new THREE.MeshStandardMaterial({color:0xa8d6ea,transparent:true,opacity:.26,roughness:.05,metalness:.5,depthWrite:false});
  function buildBus(){
    add(B(10.4,.06,2.4),mFloor,0,.33,0,bus);
    add(B(10.2,.14,2.3),mDark,0,.23,0,bus);
    add(B(10.5,.75,.08),mBlue,0,.675,-1.25,bus);
    add(B(6.85,.75,.08),mBlue,-1.825,.675,1.25,bus);add(B(2.25,.75,.08),mBlue,4.125,.675,1.25,bus);
    add(B(10.5,.04,.085),mYel,0,1.03,-1.25,bus);add(B(6.85,.04,.085),mYel,-1.825,1.03,1.25,bus);add(B(2.25,.04,.085),mYel,4.125,1.03,1.25,bus);
    add(B(1.5,.25,.1),mWhite,2.3,2.43,1.25,bus);
    const xs=[-5.2,-3.9,-2.6,-1.3,0,1.55,3.05,4.4,5.2];
    xs.forEach(x=>{add(B(.1,1.5,.1),mWhite,x,1.8,-1.25,bus);if(x<1.6||x>3.0)add(B(.1,1.5,.1),mWhite,x,1.8,1.25,bus);});
    add(B(10.4,1.5,.03),mGlass,0,1.8,-1.25,bus,false,false);
    add(B(6.8,1.5,.03),mGlass,-1.85,1.8,1.25,bus,false,false);add(B(2.15,1.5,.03),mGlass,4.1,1.8,1.25,bus,false,false);
    add(B(10.5,.2,2.5),mWhite,0,2.65,0,bus);
    add(B(3.2,.3,1.6),M(0xc8cdd3,{roughness:.6}),-1.6,2.9,0,bus);
    add(B(10.52,.05,2.52),mBlue,0,2.55,0,bus,false);
    /* frente */
    add(B(.1,.75,2.5),mBlue,5.2,.675,0,bus);add(B(.04,1.2,2.3),mGlass,5.25,1.65,0,bus,false,false);
    add(B(.1,1.5,.1),mWhite,5.2,1.8,1.22,bus);add(B(.1,1.5,.1),mWhite,5.2,1.8,-1.22,bus);
    add(B(.08,.3,2.3),mDark,5.22,2.4,0,bus);
    add(B(.22,.2,2.4),mDark,5.3,.38,0,bus);
    add(B(.06,.16,.4),mHead,5.27,.78,.85,bus,false);add(B(.06,.16,.4),mHead,5.27,.78,-.85,bus,false);
    /* parte trasera */
    add(B(.1,.75,2.5),mBlue,-5.2,.675,0,bus);add(B(.04,1.5,2.3),mGlass,-5.25,1.8,0,bus,false,false);
    add(B(.22,.2,2.4),mDark,-5.3,.38,0,bus);
    add(B(.05,.2,.35),mTail,-5.27,.95,1.0,bus,false);add(B(.05,.2,.35),mTail,-5.27,.95,-1.0,bus,false);
    /* espejos */
    [1,-1].forEach(s=>{add(B(.05,.35,.22),mDark,5.0,1.9,s*1.5,bus);add(B(.04,.04,.3),mDark,5.0,2.2,s*1.37,bus);});
    /* faros */
    [.85,-.85].forEach(z=>{const sp=new THREE.SpotLight(0xfff2cc,2.2,45,.45,.6,1.2);sp.position.set(5.3,.8,z);sp.target.position.set(32,.4,z);bus.add(sp,sp.target);});
    /* letrero LED */
    ledCanvas=document.createElement('canvas');ledCanvas.width=640;ledCanvas.height=80;
    ledTex=new THREE.CanvasTexture(ledCanvas);ledTex.colorSpace=THREE.SRGBColorSpace;
    const led=new THREE.Mesh(new THREE.PlaneGeometry(1.95,.24),new THREE.MeshBasicMaterial({map:ledTex}));led.position.set(5.27,2.4,0);led.rotation.y=Math.PI/2;bus.add(led);
    drawLED('HUÁNUCO / AMBO');
    /* decalcomanías */
    const dec=canvasTex(1024,100,(ctx,w,h)=>{ctx.clearRect(0,0,w,h);ctx.fillStyle='#fff';ctx.font="700 62px 'Atkinson Hyperlegible',Arial";ctx.textAlign='left';ctx.textBaseline='middle';ctx.fillText('TransInclusivo Huánuco',130,h/2+2);ctx.fillStyle='#ffd400';ctx.beginPath();ctx.arc(60,h/2,42,0,7);ctx.fill();isaIcon(ctx,60,h/2+2,70,'#0a4fa8');});
    const dm=new THREE.MeshStandardMaterial({map:dec,transparent:true,roughness:.5});
    const dl=new THREE.Mesh(new THREE.PlaneGeometry(4.6,.45),dm);dl.position.set(-1.8,.66,-1.295);dl.rotation.y=Math.PI;bus.add(dl);
    const dr=new THREE.Mesh(new THREE.PlaneGeometry(4.6,.45),dm);dr.position.set(-2.4,.66,1.295);bus.add(dr);
    /* puertas */
    const leaf=(x)=>{const g=new THREE.Group();g.position.set(x,1.31,1.3);
      add(B(.68,1.9,.04),mGlass,0,0,0,g,false,false);
      add(B(.68,.06,.06),mYel,0,-.93,0,g,false);add(B(.68,.06,.06),mYel,0,.93,0,g,false);
      add(B(.05,1.9,.06),mYel,x<2.3?.31:-.31,0,0,g,false);add(B(.05,1.9,.06),mDark,x<2.3?-.31:.31,0,0,g,false);
      bus.add(g);return g;};
    doorL=leaf(1.95);doorR=leaf(2.65);
    /* ruedas */
    [[3.7,1.2],[3.7,-1.2],[-3.0,1.2],[-3.0,-1.2]].forEach(([x,z])=>{
      const p=new THREE.Group();p.position.set(x,.5,z);bus.add(p);
      add(new THREE.CylinderGeometry(.5,.5,.32,28).rotateX(Math.PI/2),M(0x101113,{roughness:.9}),0,0,0,p);
      add(new THREE.CylinderGeometry(.27,.27,.34,20).rotateX(Math.PI/2),mChrome,0,0,0,p,false);
      for(let k=0;k<5;k++){const a=k*Math.PI*2/5;add(B(.07,.07,.36),mDark,Math.cos(a)*.17,Math.sin(a)*.17,0,p,false);}
      wheels.push(p);
      add(B(1.2,.1,.34),mDark,x,1.06,z*1.01,bus,false);
    });
    buildInterior();
    /* rampa */
    rampG=new THREE.Group();rampG.position.set(2.3,.36,1.2);bus.add(rampG);
    add(B(1.1,.05,1.05),M(0x555a63,{roughness:.8}),0,0,.525,rampG);
    add(B(.07,.07,1.05),mYel,-.52,.04,.525,rampG,false);add(B(.07,.07,1.05),mYel,.52,.04,.525,rampG,false);
    for(let k=0;k<8;k++)add(B(.9,.015,.03),mChrome,0,.03,.1+k*.12,rampG,false);
    add(B(1.1,.03,.1),mYel,0,.02,1.02,rampG,false);
    pistons=[-.4,.4].map(dx=>{const sl=new THREE.Mesh(rodGeo(.035),mDark);const rd=new THREE.Mesh(rodGeo(.022),mChrome);bus.add(sl,rd);return {dx,sl,rd};});
  }
  let ledCanvas,ledTex,doorL,doorR,rampG,pistons;const wheels=[];
  function drawLED(text){
    if(!ledCanvas) return;const ctx=ledCanvas.getContext('2d'),w=ledCanvas.width,h=ledCanvas.height;
    ctx.fillStyle='#050505';ctx.fillRect(0,0,w,h);ctx.fillStyle='#ffb000';ctx.font='700 40px "Courier New",monospace';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(text,w/2,h/2+2);
    ctx.fillStyle='rgba(0,0,0,.55)';for(let x=0;x<w;x+=4)ctx.fillRect(x,0,1,h);for(let y=0;y<h;y+=4)ctx.fillRect(0,y,w,1);
    ledTex.needsUpdate=true;
  }
  /* interior */
  let strapMeshes=[],anchorPts=[];
  function seat(col,prio){
    const g=new THREE.Group();const m=M(col,{roughness:.9,metalness:0});
    add(B(.42,.1,.44),m,0,.4,0,g);add(B(.12,.35,.36),mDark,0,.18,0,g);add(B(.42,.5,.08),m,0,.72,-.2,g);
    if(prio) add(B(.42,.06,.09),mYel,0,1,-.2,g,false);
    return g;
  }
  function buildInterior(){
    const fl=.36;
    const mBlueS=0x1f4e8c;
    [-4.2,-3.4,-2.6,-1.8,-1.0].forEach(x=>{const s=seat(mBlueS);s.position.set(x,fl,-.92);bus.add(s);});
    [-4.2,-3.4,-2.6,-1.8,-1.0].forEach(x=>{const s=seat(mBlueS);s.position.set(x,fl,.92);s.rotation.y=Math.PI;bus.add(s);});
    [1.5,2.1,2.7].forEach(x=>{const s=seat(0xffc400,true);s.position.set(x,fl,-.92);bus.add(s);});
    /* cartel prioritario */
    const sg=new THREE.Mesh(new THREE.PlaneGeometry(1.8,.34),new THREE.MeshBasicMaterial({map:labelTex(540,100,['ASIENTOS PRIORITARIOS'],'#0a2a5a','#ffd400',{border:'#ffd400',bw:6,fs:40,icon:true})}));
    sg.position.set(2.1,1.62,-1.2);bus.add(sg);
    /* espacio de silla */
    add(B(.08,.9,1.0),mBlue,.02,.82,.6,bus);add(B(.09,.05,1.04),mYel,.02,1.3,.6,bus,false);
    const wsg=new THREE.Mesh(new THREE.PlaneGeometry(.9,.5),new THREE.MeshBasicMaterial({map:labelTex(360,200,['ESPACIO','RESERVADO'],'#0a2a5a','#ffffff',{border:'#ffd400',bw:6,fs:44,icon:true})}));
    wsg.position.set(.075,1.62,.6);wsg.rotation.y=Math.PI/2;bus.add(wsg);
    anchorPts=[V(.38,.38,.22),V(.38,.38,.98),V(1.28,.38,.22),V(1.28,.38,.98)];
    anchorPts.forEach(p=>add(B(.13,.025,.13),mYel,p.x,p.y-.005,p.z,bus,false));
    strapMeshes=anchorPts.map(()=>{const m=new THREE.Mesh(rodGeo(.018),M(0x0a2a5a,{roughness:.6}));m.visible=false;bus.add(m);return m;});
    /* pasamanos */
    const ym=mYel;
    [[1.5,.42],[3.08,.42],[3.08,-.45],[1.5,-.45],[2.3,-.65]].forEach(([x,z])=>rod(bus,V(x,.36,z),V(x,2.55,z),.025,ym));
    rod(bus,V(-4.8,2.45,.55),V(3.5,2.45,.55),.02,ym);rod(bus,V(-4.8,2.45,-.55),V(3.5,2.45,-.55),.02,ym);
    rod(bus,V(1.5,1.05,.2),V(1.5,1.05,1.0),.02,ym);
    /* franja amarilla de umbral */
    add(B(1.4,.02,.2),mYel,2.3,.372,1.1,bus,false);
    /* placas braille junto a la puerta */
    const bp1=new THREE.Mesh(new THREE.PlaneGeometry(.3,.18),new THREE.MeshBasicMaterial({map:brailleTex(['PUERTA'])}));bp1.position.set(3.25,1.15,1.265);bus.add(bp1);
    const bp2=new THREE.Mesh(new THREE.PlaneGeometry(.3,.18),new THREE.MeshBasicMaterial({map:brailleTex(['PUERTA'])}));bp2.position.set(3.25,1.15,1.17);bp2.rotation.y=Math.PI;bus.add(bp2);
    /* cabina del chofer */
    add(B(.04,1.6,.95),mGlass,3.75,1.16,-.78,bus,false,false);
    add(B(.5,.55,2.1),mDark,4.98,.65,0,bus);add(B(.3,.04,.6),M(0x08121c,{emissive:0x1a6bff,emissiveIntensity:.5}),4.9,.95,-.55,bus,false);
    const ds=seat(0x222831);ds.position.set(4.12,fl,-.55);ds.rotation.y=Math.PI/2;bus.add(ds);
    steering=new THREE.Mesh(new THREE.TorusGeometry(.17,.022,8,20),mDark);steering.position.set(4.62,1.05,-.55);steering.rotation.y=Math.PI/2;steering.rotation.x=.5;bus.add(steering);
    /* luces interiores */
    [.6,-.6].forEach(z=>add(B(8.8,.03,.12),M(0xfffbe8,{emissive:0xfff2cc,emissiveIntensity:1.2}),-.4,2.53,z,bus,false));
    const il=new THREE.PointLight(0xfff0d8,.9,9);il.position.set(0,2.2,0);bus.add(il);
    const il2=new THREE.PointLight(0xfff0d8,.6,6);il2.position.set(3,2.2,0);bus.add(il2);
  }
  let steering;

  /* ---------- actores ---------- */
  const A={};   /* driver, asst, pax */
  function mkActor(node,kind){return {node,kind,route:null,moving:false,speedNow:0,phase:0,turn:null,wheels:[],cane:null,chairNode:null,seated:false};}
  const HOME_ASST=V(3.3,.36,.4);
  function setupCrew(){
    const drv=person({shirt:0x1b3b6f,pants:0x1a1a1a,skin:0xc89870,hair:0x151515,cap:0x1b3b6f});
    pose(drv,'drive');drv.position.set(4.16,.36,-.55);drv.rotation.y=Math.PI/2;bus.add(drv);A.driver=mkActor(drv,'crew');
    const as=person({shirt:0xffffff,pants:0x24303f,skin:0xd9a77a,hair:0x2a1b12,vest:0x76ff03});
    as.position.copy(HOME_ASST);as.rotation.y=0;bus.add(as);A.asst=mkActor(as,'crew');
  }
  function buildPaxNode(profile){
    const g=new THREE.Group();const a=mkActor(g,profile==='wheelchair'?'wheel':'blind');
    if(profile==='wheelchair'){
      const p=person({shirt:0x8e44ad,pants:0x2c3e50,skin:0xd2a07a,hair:0x1b1b1b});pose(p,'chair');p.position.set(0,0,-.03);g.add(p);
      const cm=M(0x20242b,{metalness:.6,roughness:.4});
      add(B(.46,.05,.46),cm,0,.47,0,g);add(B(.46,.5,.04),cm,0,.74,-.23,g);
      [-1,1].forEach(s=>{
        const piv=new THREE.Group();piv.position.set(s*.31,.32,-.05);g.add(piv);
        const w=new THREE.Mesh(new THREE.TorusGeometry(.31,.022,8,28),cm);w.rotation.y=Math.PI/2;w.castShadow=true;piv.add(w);
        for(let k=0;k<3;k++){const sp=new THREE.Mesh(B(.01,.6,.014),M(0xbfc5cc,{metalness:.8}));sp.rotation.x=k*Math.PI/3;piv.add(sp);}
        a.wheels.push(piv);
        add(new THREE.SphereGeometry(.06,8,8),cm,s*.25,.07,.3,g);
        rod(g,V(s*.24,.1,.3),V(s*.24,.45,.2),.015,cm);
        add(B(.14,.02,.2),cm,s*.12,.14,.32,g);
        rod(g,V(s*.25,.55,-.25),V(s*.25,.9,-.34),.015,cm);
      });
      a.person=p;a.seated=true;
    }else{
      const p=person({shirt:0xc0392b,pants:0x2c3e50,skin:0xd2a07a,hair:0x1b1b1b,glasses:true});pose(p,'stand');g.add(p);
      const cg=new THREE.Group();cg.position.set(0,-.52,0);p.userData.armR.add(cg);
      const cane=new THREE.Mesh(new THREE.CylinderGeometry(.012,.012,1.15,8),M(0xffffff,{roughness:.4}));cane.position.y=-.575;cane.castShadow=true;cg.add(cane);
      const band=new THREE.Mesh(new THREE.CylinderGeometry(.014,.014,.25,8),M(0xd32f2f));band.position.y=-.95;cg.add(band);
      cg.rotation.x=-.72;a.cane=cg;a.person=p;
    }
    return a;
  }
  /* ---------- cámara ---------- */
  const camSt={yaw:0,pitch:0,zoom:1};
  const camLook=new THREE.Vector3(.5,1.2,0);
  function updateCamera(dt,t){
    let pos=new THREE.Vector3(),look=new THREE.Vector3();
    const sph=(T,R,az,el)=>new THREE.Vector3(T.x+R*Math.sin(az)*Math.cos(el),T.y+R*Math.sin(el),T.z+R*Math.cos(az)*Math.cos(el));
    if(S.cam==='ext'){
      const T=V(.4,1.15,0);const auto=Math.sin(t*.18)*.14;
      pos=sph(T,(11+S.v*.22)*camSt.zoom,.95+auto+camSt.yaw,.26+camSt.pitch);look.copy(T);
    }else if(S.cam==='ramp'){
      const T=V(2.3,.75,1.9);
      pos=sph(T,4.8*camSt.zoom,.45+camSt.yaw,.30+camSt.pitch);look.copy(T);
    }else{
      pos.set(-3.5,1.62,-.25);
      const base=V(2.2,1.0,.7).sub(pos);const yaw=Math.atan2(base.x,base.z)+camSt.yaw*-1;
      const pit=-.12+camSt.pitch;
      look.set(pos.x+Math.sin(yaw)*Math.cos(pit)*5,pos.y+Math.sin(pit)*5,pos.z+Math.cos(yaw)*Math.cos(pit)*5);
    }
    pos.z=Math.min(pos.z,7.8);pos.y=Math.max(pos.y,.5);
    const k=S.cam==='int'?1:1-Math.exp(-dt*3.5);
    camera.position.lerp(pos,S.cam==='int'?Math.min(1,dt*6):k);camLook.lerp(look,S.cam==='int'?Math.min(1,dt*6):k);
    camera.lookAt(camLook);
  }
  /* ---------- actualización por cuadro ---------- */
  function update(dt,t){
    const d=S.d;
    asphaltT.offset.x=(d/6)%1;walkT.offset.x=(d/2)%1;
    for(let i=0;i<RING.length;i++){
      const r=RING[i];const x=ringX(r.base,d,r.W);r.o.position.x=x;
      if(r.kind==='tree'||r.kind==='lamp'){let vis=true;for(let k=0;k<STOPS.length;k++){const sx=STOPS[k].d-d;if(x>sx-3&&x<sx+9){vis=false;break;}}r.o.visible=vis;}
    }
    CARS.forEach(c=>{c.o.position.x=ringX(c.ph+c.sp*t,d,310);});
    STOPS.forEach(s=>{const x=s.d-d;s.g.position.x=x;s.g.visible=Math.abs(x)<190;});
    wheels.forEach(w=>{w.rotation.z=-d/.5;});
    /* puertas */
    const o=easeIO(S.doors);doorL.position.x=1.95-.72*o;doorR.position.x=2.65+.72*o;
    /* rampa */
    const rr=easeIO(S.ramp);const th=lerp(-Math.PI,.2,rr);rampG.rotation.x=th;
    rampG.visible=S.ramp>.02;
    pistons.forEach(p=>{
      const base=V(2.3+p.dx,.32,.8);
      const ly=-.04,lz=.55;const y=.36+(ly*Math.cos(th)-lz*Math.sin(th)),z=1.2+(ly*Math.sin(th)+lz*Math.cos(th));
      const end=V(2.3+p.dx,y,z);const dir=end.clone().sub(base);const L=dir.length();const mid=base.clone().addScaledVector(dir.normalize(),Math.min(.45,L*.5));
      p.sl.visible = p.rd.visible = S.ramp > .02;
      placeRod(p.sl,base,mid);placeRod(p.rd,mid,end);
    });
    /* anclajes */
    const chairTargets=[V(.52,.5,.4),V(.52,.5,.8),V(1.0,.5,.4),V(1.0,.5,.8)];
    strapMeshes.forEach((m,i)=>{m.visible=S.secure>.01;if(m.visible){const e=anchorPts[i].clone().lerp(chairTargets[i],S.secure);placeRod(m,anchorPts[i],e);}});
    /* luces traseras */
    mTail.emissiveIntensity=(S.braking||S.v<.2)?1.6:.45;
    /* actores */

    const dr=A.driver&&A.driver.node;if(dr){dr.userData.head.rotation.y=S.doors>.5&&S.v<.3?-.7:0;}
    if(steering) steering.rotation.z=Math.sin(t*.6)*.04*Math.min(1,S.v/5);
    updateCamera(dt,t);
    renderer.render(scene,camera);
  }
  const easeIO=x=>x<.5?2*x*x:1-Math.pow(-2*x+2,2)/2;


buildStops();
buildBus();
setupCrew();

let currentProfile;
let ledLabel;
const riders = new Map();

function positionActor(actor, points, progress) {
    const position = progress * (points.length - 1);
    const index = Math.min(points.length - 2, Math.floor(position));
    const a = points[index], b = points[index + 1];
    actor.node.position.set(...a.map((value, axis) => lerp(value, b[axis], position - index)));
    actor.node.rotation.y = Math.atan2(b[0] - a[0], b[2] - a[2]);
    if (actor.wheels.length) actor.wheels.forEach(wheel => { wheel.rotation.x = progress * 18; });
    const body = actor.person || actor.node;
    if (body.userData.pose === "stand" && actor.kind !== "wheel") {
        const stride = Math.sin(progress * 44) * Math.sin(progress * Math.PI) * .52;
        body.userData.legL.rotation.x = stride;
        body.userData.legR.rotation.x = -stride;
        body.userData.armL.rotation.x = -stride * .6;
    }
}

const waitingPoint = [2.12, .15, 3.54];
const aislePoint = [2.15, .36, .62];
const wheelchairBay = [.67, .36, .60];
const seatPoint = [2.1, .36, -.86];
const crewPath = [[3.30, .36, .40], [2.66, .36, 1.13], [2.66, .15, 2.27], [2.86, .15, 3.10]];

function setActors(stage, progress, wheelchair) {
    const profile = wheelchair ? "wheelchair" : "visual";
    if (profile !== currentProfile) {
        if (A.pax) A.pax.node.visible = false;
        currentProfile = profile;
        if (!riders.has(profile)) {
            const actor = buildPaxNode(profile);
            riders.set(profile, actor);
            bus.add(actor.node);
        }
        A.pax = riders.get(profile);
    }
    const rider = A.pax;
    const end = wheelchair ? wheelchairBay : seatPoint;
    const path = [waitingPoint, [2.15, .15, 2.25], [2.15, .36, 1.18], aislePoint, end];
    const unloading = stage === 5 ? THREE.MathUtils.smoothstep(progress, .20, .90) : 0;
    const boarding = stage === 3 ? THREE.MathUtils.smoothstep(progress, .02, .79) : stage >= 4 ? 1 - unloading : 0;
    rider.node.visible = stage >= 1;
    pose(rider.person, wheelchair ? "chair" : boarding > .98 ? "sit" : "stand");
    positionActor(rider, path, boarding);
    if (boarding > .98) rider.node.rotation.y = wheelchair ? Math.PI / 2 : 0;

    const crew = A.asst;
    const receiving = stage === 2 ? THREE.MathUtils.smoothstep(progress, .45, .97) :
        stage === 3 ? 1 - boarding : stage === 5 ? unloading : 0;
    pose(crew.node, wheelchair && stage === 3 && boarding > .97 ? "crouch" : "stand");
    positionActor(crew, crewPath, receiving);
    if (receiving < .03) crew.node.rotation.y = 0;
}

window.renderBusFrame = (stage, fraction, view, profile, width, height, label) => {
    if (renderer.domElement.width !== width || renderer.domElement.height !== height) {
        renderer.setSize(width, height, false);
        camera.aspect = width / height;
        camera.updateProjectionMatrix();
    }
    if (ledLabel !== label) {
        ledLabel = label;
        drawLED(label);
    }
    const t = [fraction * 7, 7 + fraction * 5, 12 + fraction * 6, 18 + fraction * 7,
        25 + fraction * 18, 43 + fraction * 7][stage];
    const approach = (1 - fraction) ** 2;
    const travel = fraction * fraction * (3 - 2 * fraction);
    S.d = [-120 + fraction * 75, -45 * approach, 0, 0, travel * 350, 350][stage];
    S.v = [10, 13 * (1 - fraction), 0, 0, 9 * Math.sin(fraction * Math.PI), 0][stage];
    S.braking = stage === 1;
    S.cam = ["ext", "ramp", "int"][view];
    STOPS[0].d = stage >= 4 ? 350 : 0;
    const doorOpen = stage === 2 ? THREE.MathUtils.smoothstep(fraction, 0, .28) :
        stage === 3 ? 1 - THREE.MathUtils.smoothstep(fraction, .93, 1) :
        stage === 5 ? THREE.MathUtils.smoothstep(fraction, 0, .11) : 0;
    S.doors = doorOpen;
    S.ramp = stage === 2 ? THREE.MathUtils.smoothstep(fraction, .28, .68) :
        stage === 3 ? 1 - THREE.MathUtils.smoothstep(fraction, .81, .94) :
        stage === 5 ? THREE.MathUtils.smoothstep(fraction, .10, .23) : 0;
    S.secure = profile === 2 ? (stage === 3 ? THREE.MathUtils.smoothstep(fraction, .75, .94)
        : stage === 4 ? 1 : stage === 5 ? 1 - THREE.MathUtils.smoothstep(fraction, .09, .22) : 0) : 0;
    setActors(stage, fraction, profile === 2);
    update(1, t);
    return renderer.domElement.toDataURL("image/jpeg", .84);
};
