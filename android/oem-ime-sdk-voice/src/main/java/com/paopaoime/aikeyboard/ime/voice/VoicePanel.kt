package com.paopaoime.aikeyboard.ime.voice

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.paopaoime.aikeyboard.addon.voice.R
import kotlin.math.*
import kotlin.random.Random

/** Shared dark surface, independent of keyboard skins. Same geometry and wave equations as Harmony. */
class VoicePanel(context: Context, onFinish: () -> Unit) : FrameLayout(context) {
    companion object { val BACKGROUND = Color.rgb(8, 14, 29) }
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var gradient: Shader? = null
    private var energy = 0f
    private var phase = 0f
    private var lastFrame = 0L
    private var emission = 0f
    private var text = ""
    private var state = "preparing"
    private var message = "正在准备语音输入…"
    private var latest = ""
    private var previous = ""
    private var previousGradient: Shader? = null
    private var previousLeft = 0f
    private var targetLevel = 0f
    private data class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, val life: Float, val radius: Float, val color: Int, var age: Float = 0f)
    private val particles = ArrayList<Particle>(40)
    private val colors = intArrayOf(0xFF55EDDA.toInt(), 0xFF58CEFF.toInt(), 0xFF8796FF.toInt(), 0xFFB388FF.toInt(), 0xFFF083E7.toInt(), 0xFFFFC78D.toInt())
    private val mic = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = width * .026f; strokeCap = Paint.Cap.ROUND }
            val cx = width / 2f; val top = height * .23f; val bottom = height * .58f; val half = width * .075f
            canvas.drawRoundRect(cx-half, top, cx+half, bottom, half, half, p)
            val arc = Path().apply { moveTo(cx-width*.135f, height*.46f); lineTo(cx-width*.135f, height*.55f); cubicTo(cx-width*.135f, height*.77f, cx+width*.135f, height*.77f, cx+width*.135f, height*.55f); lineTo(cx+width*.135f, height*.46f) }
            canvas.drawPath(arc,p); canvas.drawLine(cx,height*.71f,cx,height*.79f,p);canvas.drawLine(cx-width*.075f,height*.79f,cx+width*.075f,height*.79f,p)
        }
    }
    private val label = TextView(context).apply { text = "点我完成"; textSize = 12f; setTextColor(0xFFBCC9DF.toInt()); gravity = Gravity.CENTER; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val stop = LinearLayout(context).apply {
        id = R.id.voice_input_stop; orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; contentDescription = "完成语音输入"
        isClickable = true; isFocusable = true; setOnClickListener { onFinish() }
        mic.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF16BEFF.toInt(),0xFF0787FF.toInt(),0xFF1760F6.toInt())).apply { shape = GradientDrawable.OVAL; setStroke(dp(1),0xFF378EFF.toInt()) }
        addView(mic, LinearLayout.LayoutParams(dp(62),dp(62))); addView(label,LinearLayout.LayoutParams(-1,dp(26)))
    }
    init {
        id = R.id.voice_input_panel; setWillNotDraw(false); setBackgroundColor(BACKGROUND); isClickable = true
        addView(stop, LayoutParams(dp(132),dp(96),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(18) })
    }
    private fun dp(value: Int) = (value * density).roundToInt()
    private fun compact() = height / density < 280
    fun updateState(value: String, hint: String) { state = value; message = hint; label.text = if (value == "finishing") "正在完成…" else "点我完成"; invalidate() }
    fun updateText(value: String) { text = value; contentDescription = value; layoutText(); invalidate() }
    fun updateLevel(value: Float) { targetLevel = value.coerceIn(0f,1f); invalidate() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        gradient = LinearGradient(0f,0f,w.toFloat(),0f,intArrayOf(0xFF20D9B8.toInt(),0xFF12CAFF.toInt(),0xFF3476FF.toInt(),0xFF9258FF.toInt(),0xFFDF5FF2.toInt(),0xFFFF5599.toInt()),floatArrayOf(0f,.21f,.44f,.65f,.83f,1f),Shader.TileMode.CLAMP)
        val small=compact();mic.layoutParams=LinearLayout.LayoutParams(dp(if(small)48 else 62),dp(if(small)48 else 62))
        stop.layoutParams=LayoutParams(dp(132),dp(if(small)74 else 96),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=dp(if(small)10 else 18) }
        label.textSize=if(small)11f else 12f;layoutText()
    }
    private fun textPaint() { paint.reset();paint.isAntiAlias=true;paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);paint.textSize=dp(if(compact())16 else 19).toFloat() }
    private fun layoutText() {
        textPaint();val rows=VoiceDisplay.lines(text,width-dp(52).toFloat()){paint.measureText(it)};latest=rows.getOrNull(0).orEmpty();previous=rows.getOrNull(1).orEmpty()
        val length=paint.measureText(previous);previousLeft=(width-length)/2
        previousGradient=if(length>0)LinearGradient(previousLeft,0f,previousLeft+length,0f,intArrayOf(0xFFA6B5CE.toInt(),0x7AA6B5CE,0x00A6B5CE),floatArrayOf(0f,.36f,1f),Shader.TileMode.CLAMP)else null
    }
    private fun wave(x: Float, layer: Int, amplitude: Float, p: Float): Float = ((sin(x*PI*3.4-p*1.8+layer*.54)+sin(x*PI*6+p*.9+layer*.8)*.22)*sin(PI*x).coerceAtLeast(0.0).pow(1.65)*amplitude*(1-layer*.065)).toFloat()
    override fun onDraw(canvas: Canvas) {
        val scale=density;val w=width/scale;val small=compact();val waveHeight=max(0f,height/scale-(if(small)136 else 188));val cy=waveHeight*.59f
        val reduced=!ValueAnimator.areAnimatorsEnabled();val now=android.os.SystemClock.elapsedRealtime();val dt=min(.066f,if(lastFrame==0L).033f else(now-lastFrame)/1000f);lastFrame=now
        val target=if(state=="listening")targetLevel.pow(.7f) else .08f;energy+=(target-energy)*(if(target>energy).5f else .13f)
        if(!reduced)phase+=dt*(.7f+energy*.8f)
        val amplitude=min(58f,waveHeight*.31f)*(.13f+energy*1.35f)
        canvas.save();canvas.scale(scale,scale);canvas.clipRect(0f,0f,w,waveHeight);paint.reset();paint.isAntiAlias=true
        paint.shader=RadialGradient(w/2,cy,max(1f,w*.5f),intArrayOf(0x223D4FC1,0x001A2656),null,Shader.TileMode.CLAMP)
        canvas.save();canvas.scale(1f,max(.1f,waveHeight*.8f/w),w/2,cy);canvas.drawCircle(w/2,cy,w*.5f,paint);canvas.restore()
        val g=gradient;val matrix=Matrix();matrix.setScale(1/scale,1/scale);g?.setLocalMatrix(matrix);paint.shader=g;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
        paint.style=Paint.Style.STROKE;paint.strokeWidth=14f;paint.alpha=10;canvas.drawLine(0f,cy,w,cy,paint)
        paint.strokeWidth=.7f;paint.alpha=61;canvas.drawLine(0f,cy,w,cy,paint)
        for(layer in 5 downTo 0){
            path.reset();for(i in 0..96){val f=i/96f;val y=cy+wave(f,layer,amplitude,phase);if(i==0)path.moveTo(0f,y)else path.lineTo(f*w,y)}
            for(i in 96 downTo 0){val f=i/96f;path.lineTo(f*w,cy+wave(f,layer,amplitude*.62f,phase+.38f))};path.close()
            paint.style=Paint.Style.FILL;paint.alpha=((.12f+energy*.06f)*255).toInt();canvas.drawPath(path,paint)
            path.reset();for(i in 0..96){val f=i/96f;val y=cy+wave(f,layer,amplitude,phase);if(i==0)path.moveTo(0f,y)else path.lineTo(f*w,y)}
            paint.style=Paint.Style.STROKE;paint.strokeWidth=8f;paint.alpha=9;canvas.drawPath(path,paint);paint.strokeWidth=3.5f;paint.alpha=20;canvas.drawPath(path,paint)
            paint.strokeWidth=if(layer%2==0)1.2f else .7f;paint.alpha=((.5f+energy*.25f)*255).toInt();canvas.drawPath(path,paint)
            if(layer==2){paint.shader=null;paint.color=0xFFCADCFF.toInt();paint.strokeWidth=.55f;paint.alpha=97;canvas.drawPath(path,paint);paint.shader=g}
        }
        paint.shader=null;paint.style=Paint.Style.FILL
        if(!reduced){
            if(state=="listening"&&energy>.06f)emission+=dt*(4+energy*30)
            while(emission>=1&&particles.size<40){emission--;val f=.12f+Random.nextFloat()*.76f;particles+=Particle(f*w,cy+wave(f,1,amplitude,phase),(Random.nextFloat()-.5f)*13,12+Random.nextFloat()*16+energy*16,1.1f+Random.nextFloat()*1.2f,.7f+Random.nextFloat()*1.15f,colors[min(colors.lastIndex,(f*colors.size).toInt())])}
            emission=min(emission,1f)
            for(i in particles.indices.reversed()){val p=particles[i];p.age+=dt;p.x+=p.vx*dt;p.y-=p.vy*dt;if(p.age>=p.life||p.y<4){particles.removeAt(i);continue};val alpha=sin(PI*p.age/p.life).toFloat()*min(1f,p.y/18);paint.color=p.color;paint.alpha=(alpha*.12f*255).toInt();canvas.drawCircle(p.x,p.y,p.radius*2.8f,paint);paint.alpha=(alpha*.85f*255).toInt();canvas.drawCircle(p.x,p.y,p.radius,paint)}
        }else particles.clear()
        canvas.restore();textPaint();val lineHeight=dp(if(small)23 else 30);val y=waveHeight*scale+lineHeight*.5f-(paint.ascent()+paint.descent())/2
        paint.textAlign=Paint.Align.CENTER;paint.color=if(text.isEmpty())0xFFB1BED4.toInt()else 0xFFF4F7FF.toInt();canvas.drawText(latest.ifEmpty{message},width/2f,y,paint)
        if(previous.isNotEmpty()){paint.textAlign=Paint.Align.LEFT;paint.shader=previousGradient;canvas.drawText(previous,previousLeft,y+lineHeight,paint);paint.shader=null}
        if(!reduced&&isShown&&state!="idle")postInvalidateDelayed(33)
    }
    override fun onDetachedFromWindow(){particles.clear();lastFrame=0;super.onDetachedFromWindow()}
}
