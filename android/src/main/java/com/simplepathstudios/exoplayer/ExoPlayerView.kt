package com.simplepathstudios.exoplayer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.text.Cue
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import java.io.File

class ExoPlayerView(
    context: Context,
    appContext: AppContext,
) : ExpoView(context, appContext),
    SurfaceHolder.Callback {
    val session = ExoPlayerSession()
    private val onExoPlayerLog by EventDispatcher()
    private val onExoPlayerEvent by EventDispatcher()
    private val surfaceView = SurfaceView(context)
    val subtitleView = SubtitleView(context)

    private var surfaceReady = false
    private var attached = false
    private var renderer: ExoPlayerRenderer? = null
    private var videoAspectRatio: Float = 0.0f
    private var hasBitmapCues: Boolean = false

    var subScale: Float = 1.0f
    var subColor: Int = Color.WHITE
    var subOutlineColor: Int = Color.BLACK
    var subOutlineWidth: Float = 4.0f

    private var customTypeface: Typeface? = null

    init {
        surfaceView.holder.addCallback(this)
        addView(
            surfaceView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            subtitleView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )

        loadCustomFont()

        subtitleView.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
        applyCustomSubtitleStyle()

        appContext.activityProvider?.currentActivity?.let { activity ->
            if (activity is LifecycleOwner) {
                activity.lifecycle.addObserver(
                    object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            cleanup()
                        }
                    },
                )
            }
        }
    }

    private fun loadCustomFont() {
        try {
            val assetManager = context.assets
            val fontList = assetManager.list("") ?: emptyArray()
            if (fontList.contains("subfont.ttf")) {
                customTypeface = Typeface.createFromAsset(assetManager, "subfont.ttf")
            }
        } catch (_: Exception) {
            customTypeface = Typeface.DEFAULT_BOLD
        }
    }

    fun applyCustomSubtitleStyle() {
        val style =
            CaptionStyleCompat(
                subColor,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                subOutlineColor,
                customTypeface,
            )
        subtitleView.setStyle(style)
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setApplyEmbeddedFontSizes(false)

        subtitleView.post {
            applyStrokeRecursively(subtitleView)
        }
        requestLayout()
    }

    private fun applyStrokeRecursively(view: android.view.View) {
        if (view is TextView) {
            val paint = view.paint
            if (paint.style == android.graphics.Paint.Style.STROKE || paint.strokeWidth > 0f) {
                paint.strokeWidth = subOutlineWidth
            }
        } else if (view is android.view.ViewGroup) {
            for (ii in 0 until view.childCount) {
                applyStrokeRecursively(view.getChildAt(ii))
            }
        }
    }

    fun updateCues(cues: List<Cue>) {
        val containsBitmap = cues.any { cueItem -> cueItem.bitmap != null }
        if (hasBitmapCues != containsBitmap) {
            hasBitmapCues = containsBitmap
            requestLayout()
        }
        subtitleView.setCues(cues)
    }

    fun updateVideoAspectRatio(aspectRatio: Float) {
        if (aspectRatio > 0.0f && videoAspectRatio != aspectRatio) {
            videoAspectRatio = aspectRatio
            post {
                val containerWidth = width
                val containerHeight = height
                if (containerWidth > 0 && containerHeight > 0) {
                    var targetWidth = containerWidth
                    var targetHeight = containerHeight

                    val containerRatio = containerWidth.toFloat() / containerHeight.toFloat()
                    if (containerRatio > videoAspectRatio) {
                        targetWidth = (containerHeight.toFloat() * videoAspectRatio).toInt()
                        targetHeight = containerHeight
                    } else {
                        targetWidth = containerWidth
                        targetHeight = (containerWidth.toFloat() / videoAspectRatio).toInt()
                    }

                    val childLeft = (containerWidth - targetWidth) / 2
                    val childTop = (containerHeight - targetHeight) / 2

                    surfaceView.measure(
                        MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY),
                    )
                    surfaceView.layout(childLeft, childTop, childLeft + targetWidth, childTop + targetHeight)

                    if (hasBitmapCues) {
                        subtitleView.measure(
                            MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY),
                        )
                        subtitleView.layout(childLeft, childTop, childLeft + targetWidth, childTop + targetHeight)
                    } else {
                        subtitleView.measure(
                            MeasureSpec.makeMeasureSpec(containerWidth, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(containerHeight, MeasureSpec.EXACTLY),
                        )
                        subtitleView.layout(0, 0, containerWidth, containerHeight)
                    }
                }
                requestLayout()
                invalidate()
            }
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val containerWidth = MeasureSpec.getSize(widthMeasureSpec)
        val containerHeight = MeasureSpec.getSize(heightMeasureSpec)

        var targetWidth = containerWidth
        var targetHeight = containerHeight

        if (videoAspectRatio > 0.0f && containerWidth > 0 && containerHeight > 0) {
            val containerRatio = containerWidth.toFloat() / containerHeight.toFloat()
            if (containerRatio > videoAspectRatio) {
                targetWidth = (containerHeight.toFloat() * videoAspectRatio).toInt()
                targetHeight = containerHeight
            } else {
                targetWidth = containerWidth
                targetHeight = (containerWidth.toFloat() / videoAspectRatio).toInt()
            }
        }

        surfaceView.measure(
            MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY),
        )

        val subtitleMeasureWidth = if (hasBitmapCues) targetWidth else containerWidth
        val subtitleMeasureHeight = if (hasBitmapCues) targetHeight else containerHeight

        subtitleView.measure(
            MeasureSpec.makeMeasureSpec(subtitleMeasureWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(subtitleMeasureHeight, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(containerWidth, containerHeight)
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        val containerWidth = right - left
        val containerHeight = bottom - top

        var targetWidth = containerWidth
        var targetHeight = containerHeight

        if (videoAspectRatio > 0.0f && containerWidth > 0 && containerHeight > 0) {
            val containerRatio = containerWidth.toFloat() / containerHeight.toFloat()
            if (containerRatio > videoAspectRatio) {
                targetWidth = (containerHeight.toFloat() * videoAspectRatio).toInt()
                targetHeight = containerHeight
            } else {
                targetWidth = containerWidth
                targetHeight = (containerWidth.toFloat() / videoAspectRatio).toInt()
            }
        }

        val childLeft = (containerWidth - targetWidth) / 2
        val childTop = (containerHeight - targetHeight) / 2

        surfaceView.layout(childLeft, childTop, childLeft + targetWidth, childTop + targetHeight)

        if (hasBitmapCues) {
            subtitleView.layout(childLeft, childTop, childLeft + targetWidth, childTop + targetHeight)
        } else {
            subtitleView.layout(0, 0, containerWidth, containerHeight)
        }

        val referenceDimension = minOf(containerWidth, containerHeight).toFloat()
        if (referenceDimension > 0f) {
            val baseSize = referenceDimension * 0.0533f * subScale
            subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, baseSize)
        }
        post {
            applyStrokeRecursively(subtitleView)
        }
    }

    private fun reconcileRenderer() {
        if (renderer == null && attached) {
            renderer =
                ExoPlayerRenderer(
                    session = session,
                    surfaceView = surfaceView,
                    onCuesUpdated = { cuesList -> updateCues(cuesList) },
                    onAspectRatioChanged = { ratio -> updateVideoAspectRatio(ratio) },
                    onLog = { payload -> onExoPlayerLog(payload) },
                    onEvent = { payload -> onExoPlayerEvent(payload) },
                )
            renderer!!.start()
        }

        renderer?.let { activeRenderer ->
            if (surfaceReady) {
                activeRenderer.attachSurfaceIfNeeded()
            }
        }
    }

    fun onSessionUpdatedFromProps() {
        reconcileRenderer()
        renderer?.onSessionUpdated()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        reconcileRenderer()
    }

    override fun onDetachedFromWindow() {
        attached = false
        reconcileRenderer()
        super.onDetachedFromWindow()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (renderer == null) {
            reconcileRenderer()
        } else {
            renderer?.surfaceReady = true
            renderer?.attachSurfaceIfNeeded()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        renderer?.surfaceReady = false
        reconcileRenderer()
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) = Unit

    fun runCommand(orders: String) {
        val parts = orders.split("|")
        if (parts.size >= 3 && parts[0] == "set") {
            when (parts[1]) {
                "sub-scale" -> {
                    parts[2].toFloatOrNull()?.let { scaleValue ->
                        subScale = scaleValue
                        post { requestLayout() }
                    }
                    return
                }

                "sub-color" -> {
                    try {
                        subColor = Color.parseColor(parts[2])
                        post { applyCustomSubtitleStyle() }
                    } catch (_: Exception) {
                    }
                    return
                }

                "sub-border-color" -> {
                    try {
                        subOutlineColor = Color.parseColor(parts[2])
                        post { applyCustomSubtitleStyle() }
                    } catch (_: Exception) {
                    }
                    return
                }
            }
        }
        renderer?.runCommand(parts.toTypedArray())
    }

    fun setOptionString(options: String) {
        val parts = options.split("|")
        if (parts.size == 2) {
            renderer?.setOptionString(parts[0], parts[1])
        }
    }

    fun cleanup() {
        renderer?.destroy()
        renderer = null
        surfaceView.holder.removeCallback(this)
    }
}
