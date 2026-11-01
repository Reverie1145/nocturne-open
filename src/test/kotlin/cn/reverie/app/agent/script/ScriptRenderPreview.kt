package cn.reverie.app.agent.script

import cn.reverie.app.agent.*
import cn.reverie.render.font.FontManager
import cn.reverie.render.nanovg.NanoVGContext
import com.google.gson.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

/** Opt-in real Rhino → production command bridge → native NanoVG GL3, with synthetic player data. */
internal object ScriptRenderPreview {
    private val gallery = """
        var v=nocturne.render;
        function label(x,y,text,size,color){v.fontSize(size);v.textAlign("left","top");v.fillColor(color);v.text(x,y,text);}
        function panel(x,y,w,h){v.beginPath();v.roundedRect(x,y,w,h,8);v.fillColor("#11131CF4");v.fill();v.strokeColor("#FFFFFF18");v.strokeWidth(1);v.stroke();}
        nocturne.on("render2d",function(e){
          if(e.width<840||e.height<570||e.screenOpen)return;
          var x=388,y=20,w=e.width-x-20,t=(w-48)/2;
          v.save();v.resetTransform();v.globalAlpha(0.97);v.miterLimit(4);
          v.beginPath();v.roundedRectVarying(x,y,w,536,12,12,8,8);
          v.fillColor("#0C0D13F5");v.fill();v.strokeColor("#FFFFFF16");v.stroke();
          label(x+16,y+16,"NanoVG / 矢量能力",16,"#F4F4F6");
          label(x+16,y+42,"路径、绘制样式和字体通过真实脚本 API 运行",11,"#A3A6B5");
          var ax=x+16,bx=x+32+t,top=y+76,bottom=y+304;
          panel(ax,top,t,208);panel(bx,top,t,208);panel(ax,bottom,t,208);panel(bx,bottom,t,208);
          label(ax+14,top+14,"01 / 渐变与镂空",11,"#BBAAFF");
          v.beginPath();v.roundedRect(ax+14,top+44,t-28,44,6);
          v.fillPaint(v.linearGradient(ax+14,top+44,ax+t-14,top+88,"#BBAAFF","#516BEB"));v.fill();
          v.beginPath();v.circle(ax+56,top+146,32);
          v.fillPaint(v.radialGradient(ax+56,top+146,3,32,"#D4C8FF","#7561C800"));v.fill();
          v.beginPath();v.circle(ax+t-62,top+146,28);v.circle(ax+t-62,top+146,16);v.pathWinding("hole");
          v.fillColor("#AD99EC");v.fill();
          label(bx+14,top+14,"02 / 曲线与描边",11,"#BBAAFF");
          v.beginPath();v.moveTo(bx+16,top+130);v.bezierTo(bx+38,top+36,bx+60,top+184,bx+t-16,top+76);
          v.strokePaint(v.linearGradient(bx+16,top+130,bx+t-16,top+76,"#7ADDBB","#BBAAFF"));v.strokeWidth(3);v.lineCap("round");v.lineJoin("bevel");v.stroke();
          v.beginPath();v.moveTo(bx+16,top+162);v.quadTo(bx+t/2,top+76,bx+t-16,top+170);
          v.strokeColor("#FFFFFF66");v.strokeWidth(1.3);v.lineCap("square");v.stroke();
          v.beginPath();v.moveTo(bx+24,top+176);v.arcTo(bx+24,top+188,bx+60,top+188,10);v.lineTo(bx+74,top+188);
          v.lineCap("butt");v.lineJoin("miter");v.strokeColor("#7ADDBB");v.stroke();
          label(ax+14,bottom+14,"03 / 变换与动画",11,"#BBAAFF");
          v.save();v.translate(ax+t/2,bottom+112);v.rotate(e.time*0.3);v.scale(1.1,1.1);v.transform(1,0,0.12,1,0,0);
          v.beginPath();v.moveTo(0,-38);v.lineTo(34,20);v.lineTo(-34,20);v.closePath();
          v.fillColor("#BBAAFF33");v.fill();v.strokeColor("#BBAAFF");v.strokeWidth(1.5);v.stroke();v.restore();
          v.beginPath();v.arc(ax+t/2,bottom+112,53,0,Math.PI*1.6,"cw");v.strokeColor("#FFFFFF44");v.stroke();
          v.beginPath();v.arc(ax+t/2,bottom+112,59,0,-Math.PI*0.4,"ccw");v.strokeColor("#7ADDBB66");v.stroke();
          v.beginPath();v.ellipse(ax+t/2,bottom+112,69,30);v.strokeColor("#FFFFFF17");v.stroke();
          label(bx+14,bottom+14,"04 / 中文与文字布局",11,"#BBAAFF");
          v.save();v.scissor(bx+14,bottom+42,t-28,146);v.intersectScissor(bx+14,bottom+42,t-28,146);
          v.fontFace("ui");v.textLetterSpacing(0.3);label(bx+14,bottom+44,"RHYTHM / 0.64",14,"#F4F4F6");
          v.fontFace("default");v.textLetterSpacing(0);v.fontSize(12);v.textLineHeight(1.4);v.fillColor("#A3A6B5");
          v.textBox(bx+14,bottom+74,t-28,"完整绘图来自 JavaScript 脚本。\n中文换行、裁剪和主题保持一致。");
          v.fontFace("mono");label(bx+14,bottom+136,"x: 24.0  y: 64.0",11,"#7ADDBB");
          v.fontBlur(1);label(bx+14,bottom+163,"soft text",12,"#BBAAFF");v.fontBlur(0);v.resetScissor();v.restore();
          v.restore();
        });
    """.trimIndent()

    @JvmStatic fun main(args: Array<String>) {
        val output = Path.of(args.firstOrNull() ?: "build/script-render-preview").toAbsolutePath()
        Files.createDirectories(output)
        GLFWErrorCallback.createPrint(System.err).set()
        check(glfwInit())
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3)
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE); glfwWindowHint(GLFW_STENCIL_BITS,8)
        val window=glfwCreateWindow(1280,720,"Nocturne script rendering verification",0,0)
        check(window!=0L)
        glfwMakeContextCurrent(window); GL.createCapabilities()
        val context=NanoVGContext()
        check(context.init())
        val files=AgentFiles(Files.createTempDirectory("nocturne-render-preview"))
        val repo=ScriptRepository(files)
        var health=18f
        var name="Nocturne 玩家"
        val updates=AtomicInteger()
        val host=object:ScriptHost {
            override fun call(owner:String,operation:String,arguments:JsonObject,cancellation:AgentCancellation):JsonElement {
                cancellation.check(); check(operation=="player_state")
                return jsonObject("inGame" to true,"name" to name,"health" to health,"x" to 24.5,"y" to 64,"z" to -18.2).also { updates.incrementAndGet() }
            }
            override fun ask(prompt:String,cancellation:AgentCancellation)=error("No model network in preview")
            override fun release(owner:String) {}
        }
        val renderer=NanoVGScriptRenderer()
        val seen=mutableSetOf<String>()
        val theme=jsonObject("background" to "#0C0D11F5","surface" to "#FFFFFF09","border" to "#FFFFFF17",
            "accent" to "#BBAAFF","text" to "#F4F4F6","muted" to "#9EA3B0","radius" to 9,"speed" to 1)
        fun waitFor(test:()->Boolean) {
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3)
            while(!test()&&System.nanoTime()<deadline)Thread.sleep(3)
            check(test()) { "render callback did not settle" }
        }
        try {
            FontManager(context.handle).use { fonts ->
                val default=fonts.loadFontFromFile("default",Path.of("src/main/resources/assets/reverie/fonts/misans.ttf").toFile()) ?: error("Missing CJK font")
                val ui=fonts.loadFontFromFile("ui",Path.of("src/main/resources/assets/reverie/fonts/regular_medium.otf").toFile()) ?: default
                val mono=fonts.loadFontFromFile("mono",Path.of("C:/Windows/Fonts/consola.ttf").toFile()) ?: default
                if(ui.handle!=default.handle)nvgAddFallbackFontId(context.handle,ui.handle,default.handle)
                if(mono.handle!=default.handle)nvgAddFallbackFontId(context.handle,mono.handle,default.handle)
                val handles=mapOf("default" to default.handle,"ui" to ui.handle,"mono" to mono.handle)
                ScriptService(repo,host).use { scripts ->
                    scripts.installExamples()
                    repo.save(ScriptRecord(ScriptManifest("gallery"),gallery))
                    listOf("vector-hud","gallery").forEach { id ->
                        val status=scripts.start(id).get(3,TimeUnit.SECONDS)
                        check(status.state==ScriptState.RUNNING) { "$id: ${status.error}" }
                    }
                    var tick=0
                    fun sample(value:Float=health) {
                        health=value; val before=updates.get()
                        scripts.emit("tick",jsonObject("tick" to (++tick*5)))
                        waitFor { updates.get()>before }
                    }
                    repeat(60) { sample(16f+kotlin.math.sin(it*0.16f)*2) }
                    sample(18f)
                    fun capture(id:String,width:Int=1280,height:Int=720,ratio:Int=1,screen:String?=null,drawHud:Boolean=true):BufferedImage {
                        glfwSetWindowSize(window,width*ratio,height*ratio);glfwPollEvents()
                        val fw=IntArray(1);val fh=IntArray(1)
                        glfwGetFramebufferSize(window,fw,fh)
                        glViewport(0,0,fw[0],fh[0]);glDisable(GL_DEPTH_TEST)
                        val input=ScriptRenderInput(width.toFloat(),height.toFloat(),fw[0].toFloat()/width,theme=theme,screen=screen)
                        var painted=false
                        repeat(20) {
                            glClearColor(0.055f,0.068f,0.10f,1f);glClear(GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
                            context.beginFrame(width.toFloat(),height.toFloat(),input.pixelRatio)
                            // Client-owned state deliberately differs from script defaults.
                            nvgTranslate(context.handle,3f,2f)
                            nvgScissor(context.handle,width-27f,6f,12f,12f)
                            MemoryStack.stackPush().use { stack ->
                                val green=NVGColor.malloc(stack).r(0f).g(1f).b(0f).a(1f)
                                nvgFillColor(context.handle,green)
                                scripts.render(input.copy(nanoTime=System.nanoTime())) { _, frame ->
                                    seen.addAll(frame.commands.map { it.op });renderer.draw(context.handle,frame,handles);painted=true
                                }
                                val transform=stack.mallocFloat(6);nvgCurrentTransform(context.handle,transform)
                                check(transform[0]==1f&&transform[3]==1f&&transform[4]==3f&&transform[5]==2f) { "Script leaked its transform" }
                                nvgBeginPath(context.handle);nvgRect(context.handle,width-27f,6f,12f,12f);nvgFill(context.handle)
                            }
                            context.endFrame()
                            check(glGetError()==GL_NO_ERROR) { "OpenGL error while replaying $id" }
                            listOf("vector-hud","gallery").forEach { script ->
                                check(scripts.status(script).state!=ScriptState.FAILED) { "$script: ${scripts.status(script).error}" }
                            }
                            Thread.sleep(10)
                        }
                        val buffer=MemoryUtil.memAlloc(fw[0]*fh[0]*4)
                        val result=BufferedImage(fw[0],fh[0],BufferedImage.TYPE_INT_ARGB)
                        try {
                            glReadPixels(0,0,fw[0],fh[0],GL_RGBA,GL_UNSIGNED_BYTE,buffer)
                            for(y in 0 until fh[0])for(x in 0 until fw[0]) {
                                val p=((fh[0]-1-y)*fw[0]+x)*4
                                val r=buffer[p].toInt()and 255;val g=buffer[p+1].toInt()and 255;val b=buffer[p+2].toInt()and 255
                                result.setRGB(x,y,(255 shl 24)or(r shl 16)or(g shl 8)or b)
                            }
                        } finally { MemoryUtil.memFree(buffer) }
                        val marker=result.getRGB((width-18)*ratio,14*ratio)
                        check((marker shr 8 and 255)>240&&(marker shr 16 and 255)<10) { "Script leaked clipping, alpha or fill color" }
                        if(drawHud) {
                            check(painted) { "No nonempty script frame reached NanoVG" }
                            val panel=result.getRGB(28*ratio,28*ratio)
                            check(panel!=result.getRGB(8*ratio,(height-8)*ratio)) { "HUD panel did not render" }
                        } else {
                            val panel=result.getRGB(28*ratio,28*ratio)
                            check(panel==result.getRGB(8*ratio,(height-8)*ratio)) { "Hidden/stopped HUD left pixels behind" }
                        }
                        ImageIO.write(result,"png",output.resolve("$id.png").toFile())
                        println("PASS $id (${fw[0]} × ${fh[0]})")
                        return result
                    }
                    capture("nanovg-full")
                    check(seen.containsAll(ScriptDrawRecorder.operations)) { "Unverified native methods: ${ScriptDrawRecorder.operations.toSet()-seen}" }
                    sample(4f);capture("nanovg-low-health")
                    sample(0f);capture("nanovg-zero-health")
                    sample(20f);name="这是一个很长的中文玩家名称，用来验证裁剪不会覆盖生命值和图表";sample()
                    capture("nanovg-long-title",480,360)
                    capture("nanovg-compact",220,200)
                    capture("nanovg-narrow",320,280)
                    capture("nanovg-hi-dpi",1024,600,2)
                    capture("nanovg-clickgui-hidden",800,600,screen="ClickGuiScreen",drawHud=false)
                    capture("nanovg-resize-restored",960,600)
                    val built=scripts.renderStatus("vector-hud").int("framesBuilt")
                    check(built>0&&scripts.renderStatus("vector-hud").int("framesDrawn")>0)
                    scripts.stop("vector-hud").get(3,TimeUnit.SECONDS);scripts.stop("gallery").get(3,TimeUnit.SECONDS)
                    capture("nanovg-stopped",800,600,drawHud=false)
                    println("PASS all ${ScriptDrawRecorder.operations.size} native drawing methods; 10 render scenarios, state isolation and lifecycle")
                }
            }
        } finally {
            context.close();glfwDestroyWindow(window);glfwTerminate()
        }
    }
}
