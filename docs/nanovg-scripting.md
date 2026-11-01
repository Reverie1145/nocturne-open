# Nocturne NanoVG 脚本手册

由实际 Agent 手册自动导出；AI 与客户端 API 文档使用相同内容。

## rendering — 完整 NanoVG 矢量绘制、布局与动画
真实接口是 nocturne.render（nocturne.nvg 为同一对象的别名），不是 Java 的 org.lwjgl.nanovg、浏览器 Canvas 或 Skia。方法名称去掉 nvg 前缀，例如 vg.beginPath()/roundedRect()/fill()。没有原始 context 指针、beginFrame/endFrame、Java 对象、OpenGL 状态、网页 DOM；客户端管理帧和字体。不要猜 nvg* 全局函数。
使用 nocturne.on("render2d",function(event){ var vg=nocturne.render; ... }); 注册。每次回调从空指令帧、默认样式开始，完整重画需要显示的内容；不累积上一帧像素。没有绘图命令或提前 return 会清空本脚本的画面。绘图无需 capabilities；创建纯 HUD 请 capabilities=[]。
回调在脚本的串行工作线程运行，指令经过校验后整体发布，在客户端正在进行的 NanoVG HUD 帧内执行。客户端只取最新完成帧，不等待 JS；通常有一帧延迟，忙碌时合并事件，动画应按 time/delta 推进。超过 1 秒的旧帧不再重放；停止、失败、切换世界/分辨率时清空，迟到的旧帧不会复活。
仅在世界中、客户端 NanoVG HUD 可绘制时触发（正常游戏、聊天框、ClickGUI 背后的 HUD）。F1/其他阻挡 HUD 的屏幕可能暂停；不是主菜单或独立 Minecraft Screen 创建 API。screenOpen=true 时可按需求隐藏，例如 if(event.screenOpen && event.screen!=="ChatScreen") return。无法绘制世界空间 3D、物品纹理、图片或背景模糊，不伪造对应方法。当前绘图不接管鼠标点击；可用 mouseX/Y 做悬停、用 key 事件切换，不能声称已实现可点击的新窗口。

### 事件的精确字段与布局
width/height 为当前 Nocturne 设计画布单位（左上为 0,0，x 向右、y 向下），不是窗口物理像素/原版 GUI 像素。直接用 width/height 定位，不再次乘 GUI scale。pixelRatio 为每画布单位的帧缓冲像素数，客户端已经处理它；mouseX/mouseY 同样是画布单位。
partialTicks 为客户端帧插值；time 为本脚本启动后的单调秒数；delta 为两次实际执行的 render2d 之间的秒数，首帧 0，最大 0.1，避免暂停后动画跳变。delta 不是客户端真实 FPS。screen 为当前屏幕简单名称或 null；screenOpen 为布尔。
theme={background,surface,border,accent,text,muted,radius,speed}，六个颜色为 #RRGGBBAA，radius/speed 来自 ClickGUI。主题在渲染事件读取，用户改颜色/圆角后下一次绘制使用新值。可设置自己的颜色，但用户要统一设计时优先这些值；此桥接不自动提供玻璃背景模糊。
推荐：margin=20; w=Math.min(336,event.width-margin*2); x=event.width-margin-w（右侧锚定）；内容留 16 单位 padding。窗口过小折叠次要图表，边界用 save/scissor/restore。长标题和数值分配独立区域；用 textAlign("right","top") 固定右侧数值，给左标题单独裁剪，防止覆盖。
动画使用 value+=(target-value)*(1-Math.exp(-12*event.delta))；持续旋转用 event.time；不要每帧启动 setInterval，不按“每帧固定加 1”实现动画。绘制层顺序为脚本 id 字典序，后绘制的脚本可能覆盖前面的；不要依赖读取其他 HUD 的位置。

### 颜色、状态和变换（绘制命令返回 undefined）
颜色只接受 #RRGGBB 或 #RRGGBBAA，透明度在末尾，不是 ARGB 整数。rgba(r,g,b,a) -> 颜色字符串，RGB 数字 0..255，alpha 0..1。rgba 和三个渐变工厂是纯数据函数，可以在回调之外使用；其他绘制方法只能在 render2d 内调用。
save()/restore() 保存/恢复变换、裁剪、画笔、透明度和文字样式；最多嵌套 16 层，必须在同一回调中配对。restore 不能越过客户端的状态栈。不同脚本与客户端的字体/裁剪/变换隔离；路径不随 save/restore 保存，要显式 beginPath。
resetTransform(); translate(x,y); rotate(radians); scale(sx,sy); transform(a,b,c,d,e,f) 为二维仿射矩阵；角度使用弧度，例如 Math.PI/2。围绕中心旋转：save→translate(cx,cy)→rotate(angle)→以中心为原点画形状→restore。
globalAlpha(alpha) 范围 0..1，对随后内容生效；fillColor(color)/strokeColor(color) 设置各自颜色；strokeWidth(width) 范围 0.1..64；miterLimit(limit) 范围 1..20；lineCap("butt"|"round"|"square"); lineJoin("miter"|"round"|"bevel")。
scissor(x,y,w,h) 替换裁剪；intersectScissor(x,y,w,h) 与已有裁剪相交；resetScissor() 清除本脚本当前裁剪。裁剪受当前变换影响；嵌套面板用 intersectScissor 配合 save/restore，不要用新 scissor 意外放大父裁剪。

### 路径、形状、填充和描边
beginPath() 清空当前路径；moveTo(x,y) 起点；lineTo(x,y) 线段；bezierTo(c1x,c1y,c2x,c2y,x,y) 三次曲线；quadTo(cx,cy,x,y) 二次曲线；arcTo(x1,y1,x2,y2,radius) 切线圆弧；closePath() 闭合到起点。
rect(x,y,w,h); roundedRect(x,y,w,h,radius); roundedRectVarying(x,y,w,h,topLeft,topRight,bottomRight,bottomLeft); circle(cx,cy,radius); ellipse(cx,cy,rx,ry); arc(cx,cy,radius,startRadians,endRadians,"cw"|"ccw")。
以上仅添加几何，必须先 beginPath，再 fill() 或 stroke() 才会显示。例如 vg.beginPath();vg.roundedRect(x,y,w,h,8);vg.fillColor(event.theme.background);vg.fill();vg.strokeColor(event.theme.border);vg.strokeWidth(1);vg.stroke(); 可在同一路径先填充再描边。
pathWinding("solid"|"hole") 设置最近子路径。镂空：beginPath→rect(外框)→circle(孔)→pathWinding("hole")→fill。lineTo/曲线必须先有 moveTo 或形状；单独 fill/stroke 没有路径会报错。
三个渐变工厂返回普通 paint 对象，随后用 fillPaint(paint)/strokePaint(paint)，不能把 paint 当颜色字符串：
linearGradient(startX,startY,endX,endY,startColor,endColor)；radialGradient(cx,cy,innerRadius,outerRadius,innerColor,outerColor)；boxGradient(x,y,w,h,radius,feather,innerColor,outerColor)。径向要求 0≤innerRadius<outerRadius≤4096；feather>0。
例如阴影先用 boxGradient(x,y,w,h,r,12,"#00000055","#00000000") 填充稍大的外侧矩形，再画面板；渐变面板填充可用 linearGradient(x,y,x,y+h,"#23242CEB",event.theme.background)。阴影/玻璃 tint 是真实矢量绘制，背景高斯模糊没有此 API。

### 文字
fontFace("default"|"ui"|"mono")：default=MiSans（中文），ui=客户端 UI 字体，mono=Consolas；缺少可选字体回退 default，UI/mono 使用已有中文回退。默认 default、字号 14、左上对齐、白色填充。不要传文件路径或凭空的字体名。
fontSize(size) 范围 6..128；fontBlur(blur) 范围 0..10；textLetterSpacing(spacing) 范围 -4..32；textLineHeight(multiplier) 范围 0.5..4。
textAlign(horizontal,vertical)，horizontal="left"|"center"|"right"，vertical="top"|"middle"|"bottom"|"baseline"；设置 fillColor 控制文字颜色。
text(x,y,string) 绘制单行，不能包含换行；textBox(x,y,width,string) 按宽度自动换行，支持显式换行；textEllipsis(x,y,maxWidth,string) 按实际 NanoVG 字体宽度将长单行缩短为 …，保留完整 Unicode 码点，沿用当前文字样式/对齐。它是客户端提供的布局辅助接口。每次最多 4096 字符，每帧总共 8192；没有 Markdown 解析或同步 textWidth/measureText 返回值。不能把这些方法的返回值当宽度；文字布局由对齐、固定区域和裁剪完成。
多行说明必须固定可用高度并 scissor；textBox 只有宽度参数，不会自动限制最大高度。绘图文字不是聊天富文本，§ 或 & 颜色代码不解析，直接用 fillColor。保存文字样式也用 save/restore。

### 数据、限额与故障定位
在 start/tick 调用 player.state()/modules.self() 并缓存普通对象；render2d 只读缓存和事件数据。render2d 中客户端状态/操作、storage.set/remove、ai.ask 会报错，避免异步绘制阻塞、写盘和网络请求。storage.get 是内存读取；console 可用，但不要每帧打印。
单次 render2d 分发共用 250 ms 执行上限（不是期望耗时；通常应远小于一帧），每帧 1024 指令、256 次 fill/stroke/文字绘制、4096 几何复杂度单位（直线 1、矩形/圆角形 16、圆/曲线 32）、8192 字符。坐标有限且在 ±32768，宽高 0..8192，半径 0..4096；scale 分量绝对值 0.01..16，累计矩阵线性项绝对值≤64，平移≤32768，rotate/arc 弧度绝对值≤1000。缓存数据、限制实体数、抽样图表，不能用大量重复绘制绕过限额。
回调异常/超时使脚本 FAILED 并清空画面，日志给出脚本行号或具体 API/范围；save/restore 未配对整帧拒绝。不要用 try/catch 吞掉问题后声称画面完成；修正后重新验证、启动。
get_script_status({id}).render={subscribed,queued,framesBuilt,framesDrawn,coalescedFrames,commands,width?,height?,lastFrameAgeMs?,recentlyDrawn}。framesBuilt=完整指令帧生成数；framesDrawn=非空帧实际送入 NanoVG 执行次数（缓存帧重放也计数），不证明每个像素可见；commands=最近帧指令数；recentlyDrawn=最近 250 ms 执行过；coalescedFrames 为繁忙时合并的渲染事件数，正常现象，不是 droppedEvents 或逻辑失败。
排错顺序：状态 RUNNING？subscribed=true？玩家在世界且 HUD 可见？framesBuilt 增加？commands>0？framesDrawn 增加？坐标是否在画布内，透明度是否为 0，裁剪是否太小，是否被后面的 HUD 覆盖？画面静止检查 time/delta 和缓存更新。只在 validate_script 通过而没有真实渲染时，准确报告验证范围。

### 完整示例：响应式状态面板、平滑生命条、折线图、阴影和主题
此完整 JS 即客户端自带的 vector-hud 示例。capabilities=[]，无需绑定模块；可 create_module 绑定到 RENDER 分类、settings=[] 以用 ClickGUI 开关。若添加锚点/大小/颜色设置，在 tick 中读取 self().settings 并缓存，避免每帧读取。
```javascript
var vg = nocturne.render;
var player = {inGame:false}, samples = [], shownHealth = null;
function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
function alpha(color, value) {
  var a = Math.round(clamp(value,0,1)*255).toString(16);
  return color.slice(0,7)+(a.length===1 ? "0"+a : a);
}
function updatePlayer() {
  player = nocturne.player.state();
  if (!player.inGame) { samples=[];shownHealth=null;return; }
  samples.push(clamp(player.health,0,20));
  if (samples.length>60) samples.shift();
}
function rounded(x,y,w,h,r,color) {
  vg.beginPath();vg.roundedRect(x,y,w,h,r);vg.fillColor(color);vg.fill();
}
function label(x,y,text,size,color,align,maxWidth) {
  vg.fontSize(size);vg.textAlign(align||"left","top");vg.fillColor(color);
  if(maxWidth)vg.textEllipsis(x,y,maxWidth,text);else vg.text(x,y,text);
}
nocturne.on("start",updatePlayer);
nocturne.on("world",function(){player={inGame:false};samples=[];shownHealth=null;});
nocturne.on("tick",function(event){if(event.tick%5===0)updatePlayer();});
nocturne.on("render2d",function(event){
  if (!player.inGame || (event.screenOpen && event.screen!=="ChatScreen")) return;
  if (event.width<200 || event.height<160) return;
  var theme=event.theme, accent=theme.accent||"#C0ADFFFF";
  var text=theme.text||"#F4F4F6FF", muted=theme.muted||"#9EA3B0FF";
  var x=20,y=20,w=Math.min(336,event.width-40),compact=event.height<280;
  var h=compact?126:226,r=clamp(theme.radius||8,4,16);
  var target=clamp(player.health,0,20);
  if(shownHealth===null)shownHealth=target;
  shownHealth+=(target-shownHealth)*(1-Math.exp(-12*event.delta));
  var healthColor=target<8?"#FF8585FF":accent;
  vg.save();
  vg.beginPath();vg.rect(x-14,y-14,w+28,h+28);
  vg.fillPaint(vg.boxGradient(x,y,w,h,r,14,"#00000055","#00000000"));vg.fill();
  vg.beginPath();vg.roundedRect(x,y,w,h,r);
  vg.fillPaint(vg.linearGradient(x,y,x,y+h,"#22232BED",theme.background||"#0C0D11F5"));vg.fill();
  vg.strokeColor(theme.border||"#FFFFFF17");vg.strokeWidth(1);vg.stroke();
  vg.scissor(x+1,y+1,w-2,h-2);vg.fontFace("default");
  label(x+16,y+15,"NOCTURNE / 玩家状态",11,muted);
  if(w>=300){
    vg.beginPath();vg.circle(x+w-59,y+21,2.5);
    vg.fillColor(alpha(accent,0.7+Math.sin(event.time*2)*0.2));vg.fill();
    label(x+w-16,y+15,"LIVE",10,muted,"right");
  }
  vg.save();vg.intersectScissor(x+16,y+39,w-32,26);
  label(x+16,y+40,String(player.name||"玩家"),18,text,"left",w-32);vg.restore();
  label(x+16,y+78,"生命值",11,muted);
  label(x+w-16,y+74,target.toFixed(1)+" / 20",17,healthColor,"right");
  rounded(x+16,y+105,w-32,5,2.5,"#FFFFFF14");
  var barWidth=(w-32)*clamp(shownHealth/20,0,1);
  if(barWidth>0.05)rounded(x+16,y+105,barWidth,5,2.5,healthColor);
  if(compact){vg.restore();return;}
  label(x+16,y+130,"最近生命值 · 15 秒",10,muted);
  var gx=x+16,gy=y+153,gw=w-32,gh=35;
  vg.beginPath();vg.moveTo(gx,gy+gh);vg.lineTo(gx+gw,gy+gh);
  vg.strokeColor("#FFFFFF12");vg.strokeWidth(1);vg.stroke();
  if(samples.length>1){
    vg.beginPath();
    for(var i=0;i<samples.length;i++){
      var px=gx+i*gw/(samples.length-1),py=gy+gh*(1-samples[i]/20);
      if(i===0)vg.moveTo(px,py);else vg.lineTo(px,py);
    }
    vg.lineTo(gx+gw,gy+gh);vg.lineTo(gx,gy+gh);vg.closePath();
    vg.fillPaint(vg.linearGradient(gx,gy,gx,gy+gh,alpha(healthColor,0.24),alpha(healthColor,0)));vg.fill();
    vg.beginPath();
    for(var j=0;j<samples.length;j++){
      var tx=gx+j*gw/(samples.length-1),ty=gy+gh*(1-samples[j]/20);
      if(j===0)vg.moveTo(tx,ty);else vg.lineTo(tx,ty);
    }
    vg.strokeColor(healthColor);vg.strokeWidth(1.6);vg.lineJoin("round");vg.lineCap("round");vg.stroke();
  }
  var position=[player.x,player.y,player.z].map(function(v){return Number(v).toFixed(1);}).join(" / ");
  label(x+16,y+202,position,10,muted,"left",w-32);vg.restore();
});
```
示例采样每 5 tick，最多 60 点（约 15 秒）；生命上限 20 是此示例的普通生存 HUD 假设，不是假造 maxHealth 字段。改图表范围/面板高度时同步调整所有区域与裁剪。没有世界时保存与启动成功仍不能替代游戏内截图验证。

