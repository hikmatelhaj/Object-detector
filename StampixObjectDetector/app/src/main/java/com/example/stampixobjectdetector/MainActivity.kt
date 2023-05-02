package com.example.stampixobjectdetector

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.example.stampixobjectdetector.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp


class MainActivity : AppCompatActivity() {

    val paint = Paint()
    lateinit var imageProcess: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var textureView: TextureView
    lateinit var cameraManager: CameraManager
    lateinit var model: SsdMobilenetV11Metadata1
    lateinit var labels: List<String>
    lateinit var tracked_objects: HashMap<String, List<Float>>

    private var lastUpdateTime = 0L
    private val UPDATE_INTERVAL_MS = 250 // 1 second

    lateinit var rectangle: RectF
    private var xLabel = 0.0f
    private var yLabel = 0.0f
    private var label = ""


    fun openPricePage(label: String) {
        println("Opening price window")
        val Intent = Intent(this, ProductInfo::class.java)
        Intent.putExtra("label", label)
        startActivity(Intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
            getCameraPermission()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // user touched the screen
                // change the interaction here
                val x = event.x
                val y = event.y
                println("x is $x y is $y")
                println("coordinates " + tracked_objects.get("bed"))
                for ((key, value) in tracked_objects.entries) {
                    val x_min = value[0]
                    val x_max = value[1]
                    val y_min = value[2]
                    val y_max = value[3]

                    if (x in x_min..x_max && y in y_min..y_max) {
                        // Found a match
                        println("match")
                        openPricePage(key)
                    }

                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        getCameraPermission()
        tracked_objects = HashMap()
        imageProcess = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        model = SsdMobilenetV11Metadata1.newInstance(this)


        imageView = findViewById(R.id.imageView)
        val file = this.assets.open("labels.txt")
        labels = FileUtil.loadLabels(file)
        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                val currentTime = System.currentTimeMillis()
                bitmap = textureView.bitmap!! // !! for nullptr exception
                var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                // Check if the specified interval has elapsed since the last update
                if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
                    lastUpdateTime = currentTime

                    // Creates inputs for reference.
                    var image = TensorImage.fromBitmap(bitmap)
                    image = imageProcess.process(image)
                    // Runs model inference and gets result.
                    val outputs = model.process(image)
                    val locations = outputs.locationsAsTensorBuffer.floatArray
                    val classes = outputs.classesAsTensorBuffer.floatArray
                    val scores = outputs.scoresAsTensorBuffer.floatArray


                    val canvas = Canvas(mutable)
                    var h = mutable.height
                    var w = mutable.width

                    paint.textSize = h/15f
                    paint.strokeWidth = h/85f
                    var x = 0
                    tracked_objects = HashMap() // reinitialize, because you want to reset after every prediction
                    // if objects overlap, it chooses "randomly" one (the first one in the hashmap)
                    scores.forEachIndexed{ index, fl ->
                        x = index
                        x *= 4
                        if (fl > 0.6) {
                            paint.color = Color.YELLOW
                            paint.style = Paint.Style.STROKE
                            rectangle = RectF(locations[x+1] * w, locations[x] * h, locations[x+3] *w, locations[x+2] *h)
                            canvas.drawRect(rectangle, paint)
                            paint.style = Paint.Style.FILL
                            label = labels[classes[index].toInt()] + " " + fl.toString()
                            xLabel = locations[x+1] *w
                            yLabel = locations[x] *h
                            canvas.drawText(label, xLabel, yLabel, paint)
                            tracked_objects.put(labels[classes[index].toInt()], listOf(locations[x+1] * w, locations[x] * h, locations[x+3] *w, locations[x+2] *h))
                        }

                    }


                }
                imageView.setImageBitmap(mutable)
                val canvas = Canvas(mutable)
                val h = mutable.height
                paint.textSize = h/15f
                paint.strokeWidth = h/85f
                paint.color = Color.YELLOW
                paint.style = Paint.Style.STROKE
                canvas.drawRect(rectangle, paint)
                paint.style = Paint.Style.FILL
                canvas.drawText(label, xLabel, yLabel, paint)
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    @SuppressLint("MissingPermission")
    fun open_camera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object: CameraDevice.StateCallback() {
            @SuppressLint("MissingPermission")
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera

                var surfaceTexture = textureView.surfaceTexture
                var surface = Surface(surfaceTexture)

                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        session.setRepeatingRequest(captureRequest.build(), null, null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                    }
                }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) {
            }

            override fun onError(camera: CameraDevice, error: Int) {
            }
        }, handler)
    }

    private fun getCameraPermission() {
        // If permission is not enabled, ask user to enable it
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101);
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }
}