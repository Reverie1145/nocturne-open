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
