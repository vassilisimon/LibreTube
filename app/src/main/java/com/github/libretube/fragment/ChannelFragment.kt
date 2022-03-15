package com.github.libretube.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.adapters.ChannelAdapter
import com.github.libretube.model.Subscribe
import com.google.android.material.button.MaterialButton
import com.squareup.picasso.Picasso
import retrofit2.HttpException
import java.io.IOException

private const val TAG = "ChannelFragment"

class ChannelFragment : Fragment() {

    private var channelId: String? = null
    var nextPage: String? = null
    var channelAdapter: ChannelAdapter? = null
    var isLoading = true
    var isSubscribed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            channelId = it.getString("channel_id")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_channel, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        channelId = channelId!!.replace("/channel/", "")
        view.findViewById<TextView>(R.id.channel_name).text = channelId
        val recyclerView = view.findViewById<RecyclerView>(R.id.channel_recView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        fetchChannel(view)
        val sharedPref = context?.getSharedPreferences("token", Context.MODE_PRIVATE)
        if (sharedPref?.getString("token", "") != "") {
            val subButton = view.findViewById<MaterialButton>(R.id.channel_subscribe)
            isSubscribed(subButton)
        }
        val scrollView = view.findViewById<ScrollView>(R.id.channel_scrollView)
        scrollView.viewTreeObserver
            .addOnScrollChangedListener {
                if (scrollView.getChildAt(0).bottom
                    == (scrollView.height + scrollView.scrollY)
                ) {
                    //scroll view is at bottom
                    if (nextPage != null && !isLoading) {
                        isLoading = true
                        fetchNextPage()
                    }
                }
            }
    }

    private fun isSubscribed(button: MaterialButton) {
        @SuppressLint("ResourceAsColor")
        fun run() {
            lifecycleScope.launchWhenCreated {
                val response = try {
                    val sharedPref = context?.getSharedPreferences("token", Context.MODE_PRIVATE)
                    RetrofitInstance.api.isSubscribed(
                        channelId!!,
                        sharedPref?.getString("token", "")!!
                    )
                } catch (e: IOException) {
                    println(e)
                    Log.e(TAG, "IOException, you might not have internet connection")
                    return@launchWhenCreated
                } catch (e: HttpException) {
                    Log.e(TAG, "HttpException, unexpected response")
                    return@launchWhenCreated
                }
                val colorPrimary = TypedValue()
                (context as Activity).theme.resolveAttribute(
                    android.R.attr.colorPrimary,
                    colorPrimary,
                    true
                )

                val colorText = TypedValue()
                (context as Activity).theme.resolveAttribute(
                    R.attr.colorOnSurface,
                    colorText,
                    true
                )

                runOnUiThread {
                    if (response.subscribed == true) {
                        isSubscribed = true
                        button.text = getString(R.string.unsubscribe)
                        button.setTextColor(colorText.data)

                    }
                    if (response.subscribed != null) {
                        button.setOnClickListener {
                            if (isSubscribed) {
                                unsubscribe()
                                button.text = getString(R.string.subscribe)
                                button.setTextColor(colorPrimary.data)
                            } else {
                                subscribe()
                                button.text = getString(R.string.unsubscribe)
                                button.setTextColor(colorText.data)
                            }
                        }
                    }
                }
            }
        }
        run()
    }

    private fun subscribe() {
        fun run() {
            lifecycleScope.launchWhenCreated {
                val response = try {
                    val sharedPref = context?.getSharedPreferences("token", Context.MODE_PRIVATE)
                    RetrofitInstance.api.subscribe(
                        sharedPref?.getString("token", "")!!,
                        Subscribe(channelId)
                    )
                } catch (e: IOException) {
                    println(e)
                    Log.e(TAG, "IOException, you might not have internet connection")
                    return@launchWhenCreated
                } catch (e: HttpException) {
                    Log.e(TAG, "HttpException, unexpected response$e")
                    return@launchWhenCreated
                }
                isSubscribed = true
            }
        }
        run()
    }

    private fun unsubscribe() {
        fun run() {
            lifecycleScope.launchWhenCreated {
                val response = try {
                    val sharedPref = context?.getSharedPreferences("token", Context.MODE_PRIVATE)
                    RetrofitInstance.api.unsubscribe(
                        sharedPref?.getString("token", "")!!,
                        Subscribe(channelId)
                    )
                } catch (e: IOException) {
                    println(e)
                    Log.e(TAG, "IOException, you might not have internet connection")
                    return@launchWhenCreated
                } catch (e: HttpException) {
                    Log.e(TAG, "HttpException, unexpected response")
                    return@launchWhenCreated
                }
                isSubscribed = false
            }
        }
        run()
    }

    private fun fetchChannel(view: View) {
        fun run() {
            lifecycleScope.launchWhenCreated {
                val response = try {
                    RetrofitInstance.api.getChannel(channelId!!)
                } catch (e: IOException) {
                    println(e)
                    Log.e(TAG, "IOException, you might not have internet connection")
                    return@launchWhenCreated
                } catch (e: HttpException) {
                    Log.e(TAG, "HttpException, unexpected response")
                    return@launchWhenCreated
                }
                nextPage = response.nextpage
                isLoading = false
                runOnUiThread {
                    view.findViewById<TextView>(R.id.channel_name).text = response.name
                    view.findViewById<TextView>(R.id.channel_subs).text =
                        response.subscriberCount.formatShort() + " subscribers"
                    view.findViewById<TextView>(R.id.channel_description).text =
                        response.description
                    val bannerImage = view.findViewById<ImageView>(R.id.channel_banner)
                    val channelImage = view.findViewById<ImageView>(R.id.channel_image)
                    Picasso.get().load(response.bannerUrl).into(bannerImage)
                    Picasso.get().load(response.avatarUrl).into(channelImage)
                    channelAdapter = ChannelAdapter(response.relatedStreams!!.toMutableList())
                    view.findViewById<RecyclerView>(R.id.channel_recView).adapter = channelAdapter
                }
            }
        }
        run()
    }

    private fun fetchNextPage() {
        fun run() {
            lifecycleScope.launchWhenCreated {
                val response = try {
                    RetrofitInstance.api.getChannelNextPage(channelId!!, nextPage!!)
                } catch (e: IOException) {
                    println(e)
                    Log.e(TAG, "IOException, you might not have internet connection")
                    return@launchWhenCreated
                } catch (e: HttpException) {
                    Log.e(TAG, "HttpException, unexpected response," + e.response())
                    return@launchWhenCreated
                }
                nextPage = response.nextpage
                channelAdapter?.updateItems(response.relatedStreams!!)
                isLoading = false
            }
        }
        run()
    }

    private fun Fragment?.runOnUiThread(action: () -> Unit) {
        this ?: return
        if (!isAdded) return // Fragment not attached to an Activity
        activity?.runOnUiThread(action)
    }

    override fun onDestroyView() {
        val scrollView = view?.findViewById<ScrollView>(R.id.channel_scrollView)
        scrollView?.viewTreeObserver?.removeOnScrollChangedListener {
            // TODO: fill or delete
        }
        channelAdapter = null
        view?.findViewById<RecyclerView>(R.id.channel_recView)?.adapter = null
        super.onDestroyView()
    }
}