package com.example.stampixobjectdetector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONObject
import java.net.URLEncoder

class ProductInfo : AppCompatActivity() {

    private val client = OkHttpClient()
    lateinit var button: Button
    lateinit var urlText: TextView
    lateinit var ratingText: TextView


    fun getData(api_key: String, search: String) {
        val search_updated = URLEncoder.encode(search, "UTF-8") // convert string to query string
        val request = Request.Builder()
            .url("https://api.asindataapi.com/request?api_key=$api_key&type=search&amazon_domain=amazon.com&search_term=$search_updated")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    val jsonString = response.body!!.string()
                    val jsonObject = JSONObject(jsonString)
                    val data = jsonObject.getJSONArray("search_results").getJSONObject(0)
                    val url = data.getString("link")
                    val rating = data.getDouble("rating")
                    runOnUiThread {
//                        urlText.text = link
                        ratingText.text = rating.toString()

                        val linkText = "Click me"
                        val spannableString = SpannableString(linkText)
                        val clickableSpan = object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                // Open the URL in a web browser or perform some other action
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                startActivity(intent)
                            }
                        }
                        spannableString.setSpan(clickableSpan, 0, linkText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        urlText.text = spannableString
                        urlText.movementMethod = LinkMovementMethod.getInstance()
                        println("should be updated")
                    }
                    println("link is $url en rating is $rating")
                }
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_info)
        button = findViewById(R.id.button)
        button.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        urlText = findViewById(R.id.textView2)
        ratingText = findViewById(R.id.textView4)

        val b = this.intent.extras
        val label = b!!.getString("label")
        println("label is $label")
        println("Going to start the request")
        if (label != null) {
            getData("REPLACE_WITH_API_KEY", label)
        }
        println("Request is done")
    }
}